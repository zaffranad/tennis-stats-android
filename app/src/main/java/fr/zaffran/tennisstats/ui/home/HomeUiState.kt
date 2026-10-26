package fr.zaffran.tennisstats.ui.home

import fr.zaffran.tennisstats.domain.model.MatchSummary

data class HomeUiState(
    val isLoading: Boolean = true,
    /** The in-progress match, if any. */
    val activeMatch: MatchSummary? = null,
    /** All completed matches, most recently played first. */
    val completedMatches: List<MatchSummary> = emptyList(),
) {
    /** The « Nouveau match » FAB is shown only when no match is in progress. */
    val canCreateMatch: Boolean get() = !isLoading && activeMatch == null
}
