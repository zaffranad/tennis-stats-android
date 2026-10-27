package fr.zaffran.tennisstats.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import fr.zaffran.tennisstats.R
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.stats.Trend
import fr.zaffran.tennisstats.ui.components.formatDate
import fr.zaffran.tennisstats.ui.theme.TennisStatsTheme
import kotlin.math.abs
import kotlin.math.roundToInt

private val Y_LABEL_WIDTH = 36.dp
private val X_LABEL_HEIGHT = 20.dp
private val END_PADDING = 8.dp
private val TOP_PADDING = 8.dp
private val LINE_WIDTH = 2.dp
private val GRID_WIDTH = 1.dp
private val POINT_RADIUS = 5.dp
private val SELECTED_POINT_RADIUS = 7.dp
private val POINT_RING = 2.dp

/**
 * Line chart of an indicator, one point per match (statistics spec §6), drawn with a Canvas
 * (no chart library). Points are coloured by result; the dashed line is the average.
 * A tap selects the nearest point.
 */
@Composable
fun TrendChart(trend: Trend, selectedIndex: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = TennisStatsTheme.semanticColors
    val lineColor = metricColor(trend.metric)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val inkColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = inkColor, fontFeatureSettings = "tnum")
    val textMeasurer = rememberTextMeasurer()
    val values = trend.points.map { it.value.value.toFloat() }
    val maxY = if (trend.metric.isRate) RATE_MAX else countAxisMax(values.maxOrNull() ?: 0f)
    val description = stringResource(
        R.string.stats_chart_description,
        metricLabel(trend.metric),
        trend.points.size,
        formatDate(trend.points.first().match.playedAt),
        formatDate(trend.points.last().match.playedAt),
        aggregateText(trend.metric, trend.average),
    )
    val firstDate = formatDate(trend.points.first().match.playedAt)
    val lastDate = formatDate(trend.points.last().match.playedAt)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { contentDescription = description }
            .pointerInput(values.size) {
                detectTapGestures { offset ->
                    val plotStart = Y_LABEL_WIDTH.toPx()
                    val plotWidth = size.width - plotStart - END_PADDING.toPx()
                    val step = plotWidth / (values.size - 1)
                    onSelect(((offset.x - plotStart) / step).roundToInt().coerceIn(0, values.lastIndex))
                }
            },
    ) {
        val plotStart = Y_LABEL_WIDTH.toPx()
        val plotTop = TOP_PADDING.toPx()
        val plotBottom = size.height - X_LABEL_HEIGHT.toPx()
        val plotWidth = size.width - plotStart - END_PADDING.toPx()
        fun x(i: Int) = plotStart + plotWidth * i / (values.size - 1)
        fun y(v: Float) = plotBottom - (plotBottom - plotTop) * (v / maxY)

        // Recessive grid with its labels: 0, middle, top.
        listOf(0f, maxY / 2, maxY).forEach { v ->
            drawLine(
                gridColor,
                Offset(plotStart, y(v)),
                Offset(size.width - END_PADDING.toPx(), y(v)),
                GRID_WIDTH.toPx(),
            )
            val label = if (trend.metric.isRate) "${v.roundToInt()}%" else axisCountLabel(v)
            drawLabel(textMeasurer, label, labelStyle, Offset(0f, y(v)), alignEnd = plotStart - 6.dp.toPx())
        }
        drawDateLabels(textMeasurer, firstDate, lastDate, labelStyle, plotStart, size.width - END_PADDING.toPx())

        trend.average?.let { average ->
            val ay = y(average.toFloat())
            drawLine(
                color = inkColor,
                start = Offset(plotStart, ay),
                end = Offset(size.width - END_PADDING.toPx(), ay),
                strokeWidth = GRID_WIDTH.toPx() * 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }

        val path = Path().apply {
            values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
        }
        drawPath(
            path,
            lineColor,
            style = Stroke(width = LINE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        trend.points.forEachIndexed { i, point ->
            val center = Offset(x(i), y(values[i]))
            val radius = (if (i == selectedIndex) SELECTED_POINT_RADIUS else POINT_RADIUS).toPx()
            val fill = if (point.match.result == MatchResult.WIN) colors.emerald.content else colors.red.content
            drawCircle(surfaceColor, radius + POINT_RING.toPx(), center)
            drawCircle(fill, radius, center)
            if (i ==
                selectedIndex
            ) {
                drawCircle(lineColor, radius + POINT_RING.toPx(), center, style = Stroke(GRID_WIDTH.toPx()))
            }
        }
    }
}

/** Victoire / Défaite dots and the dashed average. */
@Composable
fun TrendLegend(modifier: Modifier = Modifier) {
    val colors = TennisStatsTheme.semanticColors
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(stringResource(R.string.result_win)) { Dot(colors.emerald.content) }
        LegendItem(stringResource(R.string.result_loss)) { Dot(colors.red.content) }
        LegendItem(stringResource(R.string.stats_average)) {
            Canvas(Modifier.width(16.dp).height(2.dp)) {
                drawLine(
                    ink,
                    Offset(0f, size.height / 2),
                    Offset(size.width, size.height / 2),
                    size.height,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, mark: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        mark()
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(10.dp).background(color, CircleShape))
}

private const val RATE_MAX = 100f

/** Top of the count axis: an even number at least equal to the maximum, so that the middle is an integer. */
private fun countAxisMax(max: Float): Float {
    val top = max.roundToInt().coerceAtLeast(2)
    return (top + top % 2).toFloat()
}

private fun axisCountLabel(v: Float): String = if (abs(v - v.roundToInt()) <
    0.01f
) {
    v.roundToInt().toString()
} else {
    "%.1f".format(v)
}

/** Draws [text] vertically centred on [anchor], right-aligned on [alignEnd]. */
private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    anchor: Offset,
    alignEnd: Float,
) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(alignEnd - layout.size.width, anchor.y - layout.size.height / 2f))
}

/** Dates of the first and last matches under the x axis. */
private fun DrawScope.drawDateLabels(
    measurer: TextMeasurer,
    first: String,
    last: String,
    style: TextStyle,
    start: Float,
    end: Float,
) {
    val firstLayout = measurer.measure(first, style)
    val lastLayout = measurer.measure(last, style)
    val top = size.height - firstLayout.size.height
    drawText(firstLayout, topLeft = Offset(start, top))
    drawText(lastLayout, topLeft = Offset(end - lastLayout.size.width, top))
}
