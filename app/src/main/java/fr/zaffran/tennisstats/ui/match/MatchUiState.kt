package fr.zaffran.tennisstats.ui.match

import fr.zaffran.tennisstats.domain.input.ShotInputState
import fr.zaffran.tennisstats.domain.model.Match
import fr.zaffran.tennisstats.domain.model.Shot
import fr.zaffran.tennisstats.domain.stats.MatchStats
import fr.zaffran.tennisstats.navigation.MatchTab

data class MatchUiState(
    val isLoading: Boolean = true,
    val match: Match? = null,
    /** The match no longer exists (deleted): the screen goes back home. */
    val isMissing: Boolean = false,
    val shotCount: Int = 0,
    /** The [MatchViewModel.RECENT_SHOT_COUNT] most recent shots, newest first (shot feed). */
    val recentShots: List<Shot> = emptyList(),
    val stats: MatchStats = MatchStats(totalShots = 0, serve = null, groundstrokes = null, specialShots = null),
    val input: ShotInputState = ShotInputState(),
    val selectedTab: MatchTab = MatchTab.INPUT,
    /** A shot is being written: the input bar is disabled. */
    val isRecording: Boolean = false,
    /** A shot is being deleted (« Annuler » or a row of the feed). */
    val isUndoing: Boolean = false,
    /** Finishing or deleting the match is in progress. */
    val isBusy: Boolean = false,
    /** Feedback banner of the last recorded shot, displayed 2 s. */
    val feedback: ShotFeedback? = null,
    val isFinishSheetVisible: Boolean = false,
    val isDeleteDialogVisible: Boolean = false,
) {
    val isInProgress: Boolean get() = match?.isInProgress == true

    /** « Annuler » and the feed's delete buttons are usable (RG-06). */
    val canDeleteShots: Boolean get() = isInProgress && !isRecording && !isUndoing
}

/**
 * A shot to announce in the feedback banner: recorded, or deleted when [isUndone] (« Annulé : … »).
 * [id] distinguishes two identical shots.
 */
data class ShotFeedback(val id: Long, val shot: Shot, val isUndone: Boolean = false)

sealed interface MatchEvent {
    data class ShowMessage(val message: MatchMessage) : MatchEvent
}

enum class MatchMessage {
    /** « Impossible d'enregistrer le coup ». */
    SHOT_NOT_SAVED,

    /** Generic failure of another operation. */
    UNEXPECTED,
}
