package fr.zaffran.tennisstats.domain.stats

import fr.zaffran.tennisstats.domain.model.Match

/** Value of an indicator on one match of the evolution. */
data class TrendPoint(val match: Match, val value: MetricValue)

/** « Évolution » tab for one indicator (statistics spec §6). */
data class Trend(
    val metric: StatMetric,
    /** Matches where the indicator is defined, oldest first. */
    val points: List<TrendPoint>,
    /** Aggregated over the points (§4.2); null without points. */
    val average: Double?,
    val best: TrendPoint?,
    val last: TrendPoint?,
    /** Mean of the last [RECENT_MATCHES] values − mean of the previous ones; null with fewer than 4 points. */
    val change: Double?,
) {
    /** A curve needs at least two points. */
    val canBeDrawn: Boolean get() = points.size >= 2

    companion object {
        const val RECENT_MATCHES = 3
    }
}

object TrendCalculator {

    /** Evolution of [metric] over [matchesNewestFirst] (completed matches of the period). */
    fun computeTrend(metric: StatMetric, matchesNewestFirst: List<MatchTally>): Trend {
        val points = matchesNewestFirst.asReversed().mapNotNull { (match, tally) ->
            MetricCalculator.valueOf(metric, tally)?.let { TrendPoint(match, it) }
        }
        // On ties, the most recent match is the best one.
        val best = points.asReversed().let { newestFirst ->
            if (metric.higherIsBetter) {
                newestFirst.maxByOrNull {
                    it.value.value
                }
            } else {
                newestFirst.minByOrNull { it.value.value }
            }
        }
        return Trend(
            metric = metric,
            points = points,
            average = MetricCalculator.aggregate(metric, matchesNewestFirst.map { it.tally }),
            best = best,
            last = points.lastOrNull(),
            change = change(metric, points),
        )
    }

    private fun change(metric: StatMetric, points: List<TrendPoint>): Double? {
        if (points.size <= Trend.RECENT_MATCHES) return null
        val values = points.map { it.value.value.toDouble() }
        val recent = MetricCalculator.roundForDisplay(metric, values.takeLast(Trend.RECENT_MATCHES).average())
        val previous = MetricCalculator.roundForDisplay(metric, values.dropLast(Trend.RECENT_MATCHES).average())
        return MetricCalculator.roundForDisplay(metric, recent - previous)
    }
}
