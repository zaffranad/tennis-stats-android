package fr.zaffran.tennisstats.ui.stats

import fr.zaffran.tennisstats.domain.model.Match
import fr.zaffran.tennisstats.domain.stats.HistoryStats
import fr.zaffran.tennisstats.domain.stats.MatchStats
import fr.zaffran.tennisstats.domain.stats.MetricComparison
import fr.zaffran.tennisstats.domain.stats.StatMetric
import fr.zaffran.tennisstats.domain.stats.StatsPeriod
import fr.zaffran.tennisstats.domain.stats.Trend

/** Tabs of the statistics screen. */
enum class StatsTab {
    /** « Match ». */
    MATCH,

    /** « Historique ». */
    HISTORY,

    /** « Évolution ». */
    EVOLUTION,
}

data class StatsUiState(
    val isLoading: Boolean = true,
    val selectedTab: StatsTab = StatsTab.MATCH,
    val period: StatsPeriod = StatsPeriod.ALL,
    val metric: StatMetric = StatMetric.FIRST_SERVE,
    /** Match of the « Match » tab: the in-progress one, otherwise the last completed one. */
    val focus: FocusMatch? = null,
    /** All completed matches, whatever the period. */
    val completedMatchCount: Int = 0,
    /** Completed matches of the period. */
    val periodMatchCount: Int = 0,
    /** Null when the period has no completed match. */
    val history: HistoryStats? = null,
    val trend: Trend? = null,
)

data class FocusMatch(
    val match: Match,
    val shotCount: Int,
    val stats: MatchStats,
    /** Compared with the [comparedMatchCount] other completed matches. */
    val comparison: List<MetricComparison>,
    val comparedMatchCount: Int,
)
