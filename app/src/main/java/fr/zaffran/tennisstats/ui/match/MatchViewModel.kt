package fr.zaffran.tennisstats.ui.match

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.zaffran.tennisstats.data.repository.MatchRepository
import fr.zaffran.tennisstats.data.repository.RepositoryResult
import fr.zaffran.tennisstats.domain.input.InputAction
import fr.zaffran.tennisstats.domain.input.ShotInputReducer
import fr.zaffran.tennisstats.domain.input.ShotInputState
import fr.zaffran.tennisstats.domain.input.deriveServeNumber
import fr.zaffran.tennisstats.domain.input.lastServeOf
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.model.Shot
import fr.zaffran.tennisstats.domain.model.ShotType
import fr.zaffran.tennisstats.domain.stats.StatsCalculator
import fr.zaffran.tennisstats.navigation.Match as MatchRoute
import fr.zaffran.tennisstats.navigation.MatchTab
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MatchViewModel(
    private val repository: MatchRepository,
    private val savedStateHandle: SavedStateHandle,
    route: MatchRoute,
) : ViewModel() {

    /** Decodes the route arguments (requires the Android Bundle, hence the separate constructor for JVM tests). */
    @Inject
    constructor(repository: MatchRepository, savedStateHandle: SavedStateHandle) :
        this(repository, savedStateHandle, savedStateHandle.toRoute<MatchRoute>())

    private val matchId: String = route.matchId
    private val initialTab: MatchTab = route.initialTab

    /** Input state; null until the serve number has been derived from the database. */
    private val input = MutableStateFlow(restoreInput())

    private val transient = MutableStateFlow(
        TransientState(selectedTab = savedStateHandle.get<String>(KEY_TAB)?.let(MatchTab::valueOf) ?: initialTab),
    )

    private val _events = Channel<MatchEvent>(Channel.BUFFERED)
    val events: Flow<MatchEvent> = _events.receiveAsFlow()

    private var nextFeedbackId = 0L
    private var feedbackJob: Job? = null

    val uiState: StateFlow<MatchUiState> = combine(
        repository.observeMatch(matchId),
        repository.observeShots(matchId),
        input,
        transient,
    ) { match, shots, inputState, t ->
        MatchUiState(
            isLoading = inputState == null,
            match = match,
            isMissing = match == null,
            shotCount = shots.size,
            recentShots = shots.take(RECENT_SHOT_COUNT),
            stats = StatsCalculator.computeStats(shots),
            input = inputState ?: ShotInputState(),
            selectedTab = t.selectedTab,
            isRecording = t.isRecording,
            isUndoing = t.isUndoing,
            isBusy = t.isBusy,
            feedback = t.feedback,
            isFinishSheetVisible = t.isFinishSheetVisible,
            isDeleteDialogVisible = t.isDeleteDialogVisible,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MatchUiState())

    init {
        if (input.value == null) {
            viewModelScope.launch {
                val shots = repository.observeShots(matchId).first()
                setInput(ShotInputState(serveNumber = deriveServeNumber(lastServeOf(shots))))
            }
        }
    }

    /** Serve selector, modifiers and result buttons of the ShotTracker. */
    fun onInputAction(action: InputAction) {
        val current = input.value ?: return
        // Anti double-tap: taps received while a shot is being written (or deleted) are ignored.
        if (transient.value.isRecording || transient.value.isUndoing) return
        val (next, shot) = ShotInputReducer.reduce(current, action)
        if (shot == null) {
            setInput(next)
            return
        }
        transient.update { it.copy(isRecording = true) }
        viewModelScope.launch {
            val result = storage { repository.addShot(matchId, shot) }
            if (result is RepositoryResult.Success) {
                // The new state is applied only once the shot is persisted.
                setInput(next)
                showFeedback(result.value)
            } else {
                _events.send(MatchEvent.ShowMessage(MatchMessage.SHOT_NOT_SAVED))
            }
            transient.update { it.copy(isRecording = false) }
        }
    }

    /** « Annuler » (input bar): deletes the last shot. */
    fun onUndo() = deleteShot { repository.deleteLastShot(matchId) }

    /** Delete button of a feed row: deletes that shot. */
    fun onDeleteShot(shotId: String) = deleteShot { repository.deleteShot(matchId, shotId) }

    /**
     * Deletes a shot, announces it (« Annulé : … ») and re-derives the serve number (the active mode is kept).
     * Ignored while a shot is being written, so that the shot about to be recorded is never the one deleted.
     */
    private fun deleteShot(delete: suspend () -> RepositoryResult<Shot?>) {
        if (transient.value.isUndoing || transient.value.isRecording) return
        transient.update { it.copy(isUndoing = true) }
        viewModelScope.launch {
            val result = storage(delete)
            if (result is RepositoryResult.Success) {
                val serveNumber = deriveServeNumber(lastServeOf(repository.observeShots(matchId).first()))
                input.value?.let { setInput(it.copy(serveNumber = serveNumber)) }
                result.value?.let { showFeedback(it, isUndone = true) }
            } else {
                _events.send(MatchEvent.ShowMessage(MatchMessage.UNEXPECTED))
            }
            transient.update { it.copy(isUndoing = false) }
        }
    }

    fun onTabSelected(tab: MatchTab) {
        savedStateHandle[KEY_TAB] = tab.name
        transient.update { it.copy(selectedTab = tab) }
    }

    fun onFinishClick() = transient.update { it.copy(isFinishSheetVisible = true) }

    fun onFinishSheetDismiss() = transient.update { if (it.isBusy) it else it.copy(isFinishSheetVisible = false) }

    /** « Victoire » / « Défaite »: completes the match and shows its statistics. */
    fun onFinish(result: MatchResult) {
        if (transient.value.isBusy) return
        transient.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val outcome = storage { repository.finishMatch(matchId, result) }
            if (outcome is RepositoryResult.Success) {
                savedStateHandle[KEY_TAB] = MatchTab.STATS.name
                transient.update { it.copy(isBusy = false, isFinishSheetVisible = false, selectedTab = MatchTab.STATS) }
            } else {
                transient.update { it.copy(isBusy = false) }
                _events.send(MatchEvent.ShowMessage(MatchMessage.UNEXPECTED))
            }
        }
    }

    fun onDeleteClick() = transient.update { it.copy(isDeleteDialogVisible = true) }

    fun onDeleteDismiss() = transient.update { if (it.isBusy) it else it.copy(isDeleteDialogVisible = false) }

    /** Deletes the match and its shots; the screen then leaves on [MatchUiState.isMissing]. */
    fun onDeleteConfirm() {
        if (transient.value.isBusy) return
        transient.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val outcome = storage { repository.deleteMatch(matchId) }
            if (outcome is RepositoryResult.Success) {
                transient.update {
                    it.copy(isBusy = false, isDeleteDialogVisible = false, isFinishSheetVisible = false)
                }
            } else {
                transient.update { it.copy(isBusy = false) }
                _events.send(MatchEvent.ShowMessage(MatchMessage.UNEXPECTED))
            }
        }
    }

    private fun showFeedback(shot: Shot, isUndone: Boolean = false) {
        val feedback = ShotFeedback(nextFeedbackId++, shot, isUndone)
        transient.update { it.copy(feedback = feedback) }
        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            delay(FEEDBACK_DURATION_MILLIS)
            transient.update { if (it.feedback?.id == feedback.id) it.copy(feedback = null) else it }
        }
    }

    private fun setInput(state: ShotInputState) {
        input.value = state
        savedStateHandle[KEY_MODE] = state.activeMode?.value
        savedStateHandle[KEY_SERVE_NUMBER] = state.serveNumber
    }

    private fun restoreInput(): ShotInputState? {
        val serveNumber = savedStateHandle.get<Int>(KEY_SERVE_NUMBER) ?: return null
        val mode = savedStateHandle.get<String>(KEY_MODE)?.let(ShotType::fromValue)
        return ShotInputState(activeMode = mode, serveNumber = serveNumber)
    }

    /** Runs a repository call; a storage failure (exception) is returned as null. */
    private suspend fun <T> storage(block: suspend () -> RepositoryResult<T>): RepositoryResult<T>? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        null
    }

    private data class TransientState(
        val selectedTab: MatchTab,
        val isRecording: Boolean = false,
        val isUndoing: Boolean = false,
        val isBusy: Boolean = false,
        val feedback: ShotFeedback? = null,
        val isFinishSheetVisible: Boolean = false,
        val isDeleteDialogVisible: Boolean = false,
    )

    companion object {
        const val FEEDBACK_DURATION_MILLIS = 2_000L
        const val RECENT_SHOT_COUNT = 5
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val KEY_MODE = "input.activeMode"
        private const val KEY_SERVE_NUMBER = "input.serveNumber"
        private const val KEY_TAB = "selectedTab"
    }
}
