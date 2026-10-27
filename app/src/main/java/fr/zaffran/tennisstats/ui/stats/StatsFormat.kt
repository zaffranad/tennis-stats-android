package fr.zaffran.tennisstats.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.zaffran.tennisstats.R
import fr.zaffran.tennisstats.domain.stats.MetricCalculator
import fr.zaffran.tennisstats.domain.stats.MetricValue
import fr.zaffran.tennisstats.domain.stats.StatMetric
import fr.zaffran.tennisstats.domain.stats.StatsPeriod
import fr.zaffran.tennisstats.ui.theme.TennisStatsTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun metricLabel(metric: StatMetric): String = stringResource(
    when (metric) {
        StatMetric.FIRST_SERVE -> R.string.metric_first_serve
        StatMetric.SECOND_SERVE -> R.string.metric_second_serve
        StatMetric.FOREHAND -> R.string.metric_forehand
        StatMetric.BACKHAND -> R.string.metric_backhand
        StatMetric.ACES -> R.string.metric_aces
        StatMetric.DOUBLE_FAULTS -> R.string.metric_double_faults
        StatMetric.WINNERS -> R.string.metric_winners
    },
)

/** Colour of an indicator's curve (statistics spec §6.1). */
@Composable
fun metricColor(metric: StatMetric): Color {
    val colors = TennisStatsTheme.semanticColors
    return when (metric) {
        StatMetric.FIRST_SERVE -> colors.violet
        StatMetric.SECOND_SERVE -> colors.amber
        StatMetric.FOREHAND -> colors.blue
        StatMetric.BACKHAND -> colors.cyan
        StatMetric.ACES -> colors.emerald
        StatMetric.DOUBLE_FAULTS -> colors.red
        StatMetric.WINNERS -> colors.teal
    }.content
}

@Composable
fun periodLabel(period: StatsPeriod): String = stringResource(
    when (period) {
        StatsPeriod.ALL -> R.string.stats_period_all
        StatsPeriod.LAST_10 -> R.string.stats_period_last_10
        StatsPeriod.LAST_5 -> R.string.stats_period_last_5
    },
)

/** « 62% » for a rate, « 3 » for a count. */
@Composable
fun metricValueText(metric: StatMetric, value: MetricValue): String =
    if (metric.isRate) stringResource(R.string.stats_percent, value.value) else value.value.toString()

/** « 18/29 » for a rate, null for a count. */
@Composable
fun metricRatioText(value: MetricValue): String? =
    value.total?.let { stringResource(R.string.stats_ratio, value.part, it) }

/** Aggregated value (statistics spec §4.2): « 60% », « 2,3 » or « — ». */
@Composable
fun aggregateText(metric: StatMetric, value: Double?): String = when {
    value == null -> stringResource(R.string.stats_not_available)
    metric.isRate -> stringResource(R.string.stats_percent, value.roundToInt())
    else -> decimalText(value)
}

/** « 2,3 » (one decimal, French format) or « — ». */
@Composable
fun decimalText(value: Double?): String =
    value?.let { String.format(Locale.FRANCE, "%.1f", it) } ?: stringResource(R.string.stats_not_available)

/** Gap with a reference (statistics spec §3): « +5 pts », « −0,5 », « = » or « — ». */
@Composable
fun deltaText(metric: StatMetric, delta: Double?): String {
    if (delta == null) return stringResource(R.string.stats_not_available)
    if (delta == 0.0) return stringResource(R.string.stats_delta_equal)
    val sign = if (delta > 0) "+" else "−"
    return if (metric.isRate) {
        stringResource(R.string.stats_delta_points, sign + abs(delta).roundToInt())
    } else {
        sign + String.format(Locale.FRANCE, "%.1f", abs(delta))
    }
}

/** Green for an improvement, red for a regression, neutral otherwise. */
@Composable
fun deltaColor(metric: StatMetric, delta: Double?, neutral: Color): Color {
    val colors = TennisStatsTheme.semanticColors
    return when {
        delta == null || delta == 0.0 -> neutral
        MetricCalculator.isImprovement(metric, delta) -> colors.green.content
        else -> colors.red.content
    }
}

/** « {n} match(s) terminé(s) », plural for 0 (as shotCountText). */
@Composable
fun completedMatchCountText(count: Int): String =
    pluralStringResource(R.plurals.stats_completed_match_count, if (count == 0) OTHER_QUANTITY else count, count)

private const val OTHER_QUANTITY = 2
