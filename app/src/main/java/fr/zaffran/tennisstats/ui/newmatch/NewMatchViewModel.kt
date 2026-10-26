package fr.zaffran.tennisstats.ui.newmatch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.zaffran.tennisstats.data.repository.MatchError
import fr.zaffran.tennisstats.data.repository.MatchRepository
import fr.zaffran.tennisstats.data.repository.RepositoryResult
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Form errors, translated to strings by the screen. */
enum class NewMatchError {
    /** RG-02, displayed under the name field. */
    OPPONENT_NAME_REQUIRED,

    /** RG-01, displayed under the form. */
    MATCH_ALREADY_IN_PROGRESS,

    /** Storage failure. */
    UNEXPECTED,
}

data class NewMatchUiState(
    val opponentName: String = "",
    /** Stored value of the selected level, null for none. */
    val opponentLevel: String? = null,
    val error: NewMatchError? = null,
    val isCreating: Boolean = false,
)

sealed interface NewMatchEvent {
    /** Open the match screen: the newly created match, or the match already in progress. */
    data class OpenMatch(val matchId: String) : NewMatchEvent
}

@HiltViewModel
class NewMatchViewModel @Inject constructor(
    private val repository: MatchRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        NewMatchUiState(
            opponentName = savedStateHandle[KEY_NAME] ?: "",
            opponentLevel = savedStateHandle[KEY_LEVEL],
        ),
    )
    val uiState: StateFlow<NewMatchUiState> = _uiState.asStateFlow()

    private val _events = Channel<NewMatchEvent>(Channel.BUFFERED)
    val events: Flow<NewMatchEvent> = _events.receiveAsFlow()

    init {
        // Opening the form while a match is in progress redirects to that match (web behaviour).
        viewModelScope.launch {
            repository.observeActiveMatch().first()?.let { _events.send(NewMatchEvent.OpenMatch(it.match.id)) }
        }
    }

    fun onOpponentNameChange(name: String) {
        savedStateHandle[KEY_NAME] = name
        _uiState.update {
            it.copy(opponentName = name, error = it.error.takeUnless { e -> e == NewMatchError.OPPONENT_NAME_REQUIRED })
        }
    }

    fun onOpponentLevelChange(level: String?) {
        savedStateHandle[KEY_LEVEL] = level
        _uiState.update { it.copy(opponentLevel = level) }
    }

    fun onStart() {
        val state = _uiState.value
        if (state.isCreating) return
        if (state.opponentName.isBlank()) {
            _uiState.update { it.copy(error = NewMatchError.OPPONENT_NAME_REQUIRED) }
            return
        }
        _uiState.update { it.copy(isCreating = true, error = null) }
        viewModelScope.launch {
            val result = try {
                repository.createMatch(state.opponentName, state.opponentLevel)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                null
            }
            when (result) {
                is RepositoryResult.Success -> _events.send(NewMatchEvent.OpenMatch(result.value.id))

                is RepositoryResult.Failure -> _uiState.update {
                    it.copy(isCreating = false, error = result.error.toFormError())
                }

                null -> _uiState.update { it.copy(isCreating = false, error = NewMatchError.UNEXPECTED) }
            }
        }
    }

    private fun MatchError.toFormError() = when (this) {
        MatchError.OpponentNameRequired -> NewMatchError.OPPONENT_NAME_REQUIRED
        MatchError.MatchAlreadyInProgress -> NewMatchError.MATCH_ALREADY_IN_PROGRESS
        MatchError.MatchNotInProgress, MatchError.MatchNotFound -> NewMatchError.UNEXPECTED
    }

    private companion object {
        const val KEY_NAME = "opponentName"
        const val KEY_LEVEL = "opponentLevel"
    }
}
