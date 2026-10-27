package fr.zaffran.tennisstats.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zaffran.tennisstats.R
import fr.zaffran.tennisstats.domain.stats.StatMetric
import fr.zaffran.tennisstats.domain.stats.StatsPeriod
import fr.zaffran.tennisstats.domain.stats.Trend
import fr.zaffran.tennisstats.domain.stats.TrendPoint
import fr.zaffran.tennisstats.ui.components.MatchStatusBadge
import fr.zaffran.tennisstats.ui.components.StatTile
import fr.zaffran.tennisstats.ui.components.StatTileRow
import fr.zaffran.tennisstats.ui.components.StatsCard
import fr.zaffran.tennisstats.ui.components.formatDate

/** « Évolution » tab (statistics spec §6): one indicator, match after match. */
@Composable
fun EvolutionTab(
    uiState: StatsUiState,
    contentPadding: PaddingValues,
    onPeriodSelected: (StatsPeriod) -> Unit,
    onMetricSelected: (StatMetric) -> Unit,
    onOpenMatch: (matchId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val trend = uiState.trend
        if (uiState.periodMatchCount == 0 || trend == null) {
            noCompletedMatchItem()
            return@LazyColumn
        }
        periodItems(uiState, onPeriodSelected)
        item(key = "metrics") { MetricSelector(uiState.metric, onMetricSelected) }
        if (trend.canBeDrawn) {
            item(key = "chart") { TrendChartCard(trend) }
            item(key = "summary") { TrendSummary(trend) }
        } else {
            item(key = "notEnough") {
                Text(
                    text = stringResource(R.string.stats_trend_not_enough),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
            }
        }
        if (trend.points.isNotEmpty()) {
            item(key = "detailHeader") {
                Text(
                    text = stringResource(R.string.stats_match_detail),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { heading() },
                )
            }
            items(trend.points.asReversed(), key = { it.match.id }) { point ->
                TrendMatchRow(trend.metric, point, onClick = { onOpenMatch(point.match.id) })
            }
        }
    }
}

@Composable
private fun MetricSelector(selected: StatMetric, onSelected: (StatMetric) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(StatMetric.entries) { metric ->
            FilterChip(
                selected = metric == selected,
                onClick = { onSelected(metric) },
                label = { Text(metricLabel(metric)) },
            )
        }
    }
}

@Composable
private fun TrendChartCard(trend: Trend) {
    // The most recent match is selected by default; the selection is reset when the points change.
    var selectedIndex by remember(trend.points) { mutableIntStateOf(trend.points.lastIndex) }
    val selected = trend.points[selectedIndex]
    StatsCard {
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = metricLabel(trend.metric),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(
                        if (trend.metric.isRate) R.string.stats_unit_percent else R.string.stats_unit_per_match,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(
                    R.string.stats_chart_selection,
                    formatDate(selected.match.playedAt),
                    selected.match.opponentName,
                    metricValueText(trend.metric, selected.value),
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            TrendChart(trend, selectedIndex, onSelect = { selectedIndex = it })
            TrendLegend()
        }
    }
}

@Composable
private fun TrendSummary(trend: Trend) {
    val ink = MaterialTheme.colorScheme.onSurface
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    StatsCard {
        StatTileRow(
            listOf(
                {
                    StatTile(
                        aggregateText(trend.metric, trend.average),
                        stringResource(R.string.stats_average),
                        ink,
                        it,
                    )
                },
                {
                    StatTile(
                        value = trend.best?.let { best -> metricValueText(trend.metric, best.value) }.orEmpty(),
                        label = stringResource(R.string.stats_best),
                        color = ink,
                        subLine = trend.best?.let { formatDate(it.match.playedAt) },
                        modifier = it,
                    )
                },
                {
                    StatTile(
                        value = trend.last?.let { last -> metricValueText(trend.metric, last.value) }.orEmpty(),
                        label = stringResource(R.string.stats_last),
                        color = ink,
                        subLine = trend.last?.let { formatDate(it.match.playedAt) },
                        modifier = it,
                    )
                },
                {
                    StatTile(
                        value = deltaText(trend.metric, trend.change),
                        label = stringResource(R.string.stats_change),
                        color = deltaColor(trend.metric, trend.change, neutral),
                        subLine = stringResource(R.string.stats_change_subline),
                        modifier = it,
                    )
                },
            ),
        )
    }
}

@Composable
private fun TrendMatchRow(metric: StatMetric, point: TrendPoint, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.opponent_vs, point.match.opponentName),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatDate(point.match.playedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = metricValueText(metric, point.value),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                )
                metricRatioText(point.value)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MatchStatusBadge(point.match.status, point.match.result)
        }
    }
}
