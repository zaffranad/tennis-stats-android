package fr.zaffran.tennisstats.domain.stats

import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.stats.StatsCalculator.percentage
import kotlin.math.roundToInt

/** Matches taken into account by the history and the evolution (statistics spec §5). */
enum class StatsPeriod(val matchLimit: Int?) {
    ALL(null),
    LAST_10(10),
    LAST_5(5),
    ;

    /** Keeps the most recent matches of [matchesNewestFirst]. */
    fun <T> select(matchesNewestFirst: List<T>): List<T> =
        matchLimit?.let(matchesNewestFirst::take) ?: matchesNewestFirst
}

/** « Historique » tab: cumulated statistics of completed matches (statistics spec §5). */
data class HistoryStats(
    val matchCount: Int,
    val wins: Int,
    val losses: Int,
    val winPct: Int,
    val totalShots: Int,
    val shotsPerMatch: Int,
    /** Statistics of all the shots pooled together (functional spec §9 sections). */
    val pooled: MatchStats,
    /** Means per match with at least one serve; null without any serve. */
    val acesPerMatch: Double?,
    val doubleFaultsPerMatch: Double?,
    /** Wins versus losses; null unless there is at least one of each. */
    val resultSplit: List<ResultSplitRow>?,
)

/** One line of the « Victoires / défaites » table; a null value is displayed « — ». */
data class ResultSplitRow(val metric: StatMetric, val wins: Double?, val losses: Double?)

object HistoryCalculator {

    /** Cumulated statistics of [matches] (completed); null when there is none. */
    fun computeHistory(matches: List<MatchTally>): HistoryStats? {
        if (matches.isEmpty()) return null
        val tallies = matches.map { it.tally }
        val (won, lost) = matches.partition { it.match.result == MatchResult.WIN }
        val totalShots = tallies.sumOf { it.total }
        return HistoryStats(
            matchCount = matches.size,
            wins = won.size,
            losses = lost.size,
            winPct = percentage(won.size, matches.size)!!,
            totalShots = totalShots,
            shotsPerMatch = (totalShots.toDouble() / matches.size).roundToInt(),
            pooled = StatsCalculator.computeStats(tallies.sum()),
            acesPerMatch = MetricCalculator.aggregate(StatMetric.ACES, tallies),
            doubleFaultsPerMatch = MetricCalculator.aggregate(StatMetric.DOUBLE_FAULTS, tallies),
            resultSplit = if (won.isNotEmpty() && lost.isNotEmpty()) resultSplit(won, lost) else null,
        )
    }

    private fun resultSplit(won: List<MatchTally>, lost: List<MatchTally>): List<ResultSplitRow> =
        StatMetric.entries.mapNotNull { metric ->
            val wins = MetricCalculator.aggregate(metric, won.map { it.tally })
            val losses = MetricCalculator.aggregate(metric, lost.map { it.tally })
            if (wins == null && losses == null) null else ResultSplitRow(metric, wins, losses)
        }
}
