package fr.zaffran.tennisstats.domain.stats

import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType
import fr.zaffran.tennisstats.domain.stats.StatsCalculator.percentage
import kotlin.math.roundToInt

/** Indicators followed across matches (statistics spec §4.1), in display order. */
enum class StatMetric(
    /** A rate in % (comparable between matches); otherwise a count per match. */
    val isRate: Boolean,
    val higherIsBetter: Boolean = true,
) {
    FIRST_SERVE(isRate = true),
    SECOND_SERVE(isRate = true),
    FOREHAND(isRate = true),
    BACKHAND(isRate = true),
    ACES(isRate = false),
    DOUBLE_FAULTS(isRate = false, higherIsBetter = false),
    WINNERS(isRate = false),
}

/** Value of an indicator on one match: the rate `part / total`, or the count [part] when [total] is null. */
data class MetricValue(val part: Int, val total: Int?) {
    /** Percentage for a rate, count otherwise. */
    val value: Int get() = total?.let { percentage(part, it)!! } ?: part
}

/** Indicator values and aggregations (statistics spec §4). Pure functions. */
object MetricCalculator {

    /** Value of [metric] on a match, or null when it is not defined (no shot of the family). */
    fun valueOf(metric: StatMetric, tally: ShotTally): MetricValue? = when (metric) {
        StatMetric.FIRST_SERVE -> rate(tally, { it.isServe(second = false) }) { it.isServeIn }

        StatMetric.SECOND_SERVE -> rate(tally, { it.isServe(second = true) }) { it.isServeIn }

        StatMetric.FOREHAND -> rate(tally, { it.type == ShotType.FOREHAND }) { it.isSuccess }

        StatMetric.BACKHAND -> rate(tally, { it.type == ShotType.BACKHAND }) { it.isSuccess }

        StatMetric.ACES -> count(tally, { it.type == ShotType.SERVE }) { it.outcome == ShotOutcome.ACE }

        StatMetric.DOUBLE_FAULTS -> count(tally, { it.type == ShotType.SERVE }) {
            it.isServe(second = true) && it.outcome.isMiss
        }

        StatMetric.WINNERS -> count(tally, { it.type != ShotType.SERVE }) { it.outcome == ShotOutcome.WINNER }
    }

    /**
     * [metric] over several matches, rounded as displayed (§4.2): pooled percentage for a rate,
     * mean per match (1 decimal) for a count, over the matches where it is defined; null if none.
     */
    fun aggregate(metric: StatMetric, tallies: List<ShotTally>): Double? {
        val values = tallies.mapNotNull { valueOf(metric, it) }
        if (values.isEmpty()) return null
        return if (metric.isRate) {
            percentage(values.sumOf { it.part }, values.sumOf { it.total!! })?.toDouble()
        } else {
            roundForDisplay(metric, values.sumOf { it.part }.toDouble() / values.size)
        }
    }

    /** Rounds as displayed: integer for a rate (points), 1 decimal for a count. */
    fun roundForDisplay(metric: StatMetric, value: Double): Double =
        if (metric.isRate) value.roundToInt().toDouble() else (value * 10).roundToInt() / 10.0

    /** True when [delta] (value − reference) is an improvement for [metric]. */
    fun isImprovement(metric: StatMetric, delta: Double): Boolean = if (metric.higherIsBetter) delta > 0 else delta < 0

    /** [metric] on a match compared with the other matches (§3); indicators without both values are omitted. */
    fun compare(tally: ShotTally, others: List<ShotTally>): List<MetricComparison> =
        StatMetric.entries.mapNotNull { metric ->
            val value = valueOf(metric, tally) ?: return@mapNotNull null
            val reference = aggregate(metric, others) ?: return@mapNotNull null
            MetricComparison(metric, value, reference, roundForDisplay(metric, value.value - reference))
        }

    private fun ShotKey.isServe(second: Boolean) = type == ShotType.SERVE && isSecondServe == second

    private fun rate(tally: ShotTally, family: (ShotKey) -> Boolean, success: (ShotOutcome) -> Boolean): MetricValue? {
        val total = tally.count(family)
        if (total == 0) return null
        return MetricValue(part = tally.count { family(it) && success(it.outcome) }, total = total)
    }

    private fun count(tally: ShotTally, family: (ShotKey) -> Boolean, counted: (ShotKey) -> Boolean): MetricValue? {
        if (tally.count(family) == 0) return null
        return MetricValue(part = tally.count { family(it) && counted(it) }, total = null)
    }
}

/** One line of the « Comparaison » card: [delta] = value − [reference], rounded as displayed. */
data class MetricComparison(val metric: StatMetric, val value: MetricValue, val reference: Double, val delta: Double)
