package fr.zaffran.tennisstats.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.zaffran.tennisstats.R
import fr.zaffran.tennisstats.domain.stats.HistoryStats
import fr.zaffran.tennisstats.domain.stats.ResultSplitRow
import fr.zaffran.tennisstats.domain.stats.ServeStats
import fr.zaffran.tennisstats.domain.stats.StatsPeriod
import fr.zaffran.tennisstats.ui.components.SectionHeader
import fr.zaffran.tennisstats.ui.components.StatTile
import fr.zaffran.tennisstats.ui.components.StatTileRow
import fr.zaffran.tennisstats.ui.components.StatsCard
import fr.zaffran.tennisstats.ui.components.percentText
import fr.zaffran.tennisstats.ui.match.stats.matchStatsSections
import fr.zaffran.tennisstats.ui.theme.TennisStatsTheme

/** « Historique » tab (statistics spec §5): cumulated statistics of the completed matches of the period. */
@Composable
fun HistoryTab(
    uiState: StatsUiState,
    contentPadding: PaddingValues,
    onPeriodSelected: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val history = uiState.history
        if (history == null) {
            noCompletedMatchItem()
            return@LazyColumn
        }
        periodItems(uiState, onPeriodSelected)
        item(key = "summary") { SummaryCard(history) }
        matchStatsSections(history.pooled, serveCountTiles = { serve -> historyServeCountTiles(history, serve) })
        history.resultSplit?.let { rows -> item(key = "resultSplit") { ResultSplitCard(rows) } }
    }
}

/** Empty state shared by the « Historique » and « Évolution » tabs. */
fun LazyListScope.noCompletedMatchItem() {
    item(key = "empty") {
        StatsEmptyState(
            title = stringResource(R.string.home_empty_title),
            subtitle = stringResource(R.string.stats_no_completed_subtitle),
        )
    }
}

/** Period selector and number of completed matches taken into account. */
fun LazyListScope.periodItems(uiState: StatsUiState, onPeriodSelected: (StatsPeriod) -> Unit) {
    item(key = "period") {
        PeriodSelector(uiState.period, onPeriodSelected)
        Text(
            text = completedMatchCountText(uiState.periodMatchCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

@Composable
private fun SummaryCard(history: HistoryStats) {
    val colors = TennisStatsTheme.semanticColors
    val ink = MaterialTheme.colorScheme.onSurface
    StatsCard {
        SectionHeader(stringResource(R.string.stats_section_summary))
        StatTileRow(
            listOf(
                { StatTile(history.matchCount.toString(), stringResource(R.string.stats_matches), ink, it) },
                {
                    StatTile(
                        value = history.wins.toString(),
                        label = stringResource(R.string.stats_wins),
                        color = colors.emerald.content,
                        subLine = percentText(history.winPct),
                        modifier = it,
                    )
                },
                { StatTile(history.losses.toString(), stringResource(R.string.stats_losses), colors.red.content, it) },
                {
                    StatTile(
                        value = history.shotsPerMatch.toString(),
                        label = stringResource(R.string.stats_shots_per_match),
                        color = ink,
                        subLine = stringResource(R.string.stats_total, history.totalShots),
                        modifier = it,
                    )
                },
            ),
        )
    }
}

/** « Aces / match » and « DF / match » instead of the counts of a single match. */
@Composable
private fun historyServeCountTiles(history: HistoryStats, serve: ServeStats): List<@Composable (Modifier) -> Unit> {
    val colors = TennisStatsTheme.semanticColors
    return listOf(
        {
            StatTile(
                value = decimalText(history.acesPerMatch),
                label = stringResource(R.string.stats_aces_per_match),
                color = colors.emerald.content,
                subLine = stringResource(R.string.stats_total, serve.aces),
                modifier = it,
            )
        },
        {
            StatTile(
                value = decimalText(history.doubleFaultsPerMatch),
                label = stringResource(R.string.stats_double_faults_per_match),
                color = colors.red.content,
                subLine = stringResource(R.string.stats_total, serve.doubleFaults),
                modifier = it,
            )
        },
    )
}

/** What distinguishes the won matches from the lost ones. */
@Composable
private fun ResultSplitCard(rows: List<ResultSplitRow>) {
    val colors = TennisStatsTheme.semanticColors
    StatsCard {
        SectionHeader(stringResource(R.string.stats_section_result_split))
        SplitRow(
            label = "",
            wins = stringResource(R.string.stats_wins),
            losses = stringResource(R.string.stats_losses),
            winsColor = colors.emerald.content,
            lossesColor = colors.red.content,
            isHeader = true,
        )
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 8.dp))
            SplitRow(
                label = metricLabel(row.metric),
                wins = aggregateText(row.metric, row.wins),
                losses = aggregateText(row.metric, row.losses),
            )
        }
    }
}

@Composable
private fun SplitRow(
    label: String,
    wins: String,
    losses: String,
    winsColor: Color = MaterialTheme.colorScheme.onSurface,
    lossesColor: Color = MaterialTheme.colorScheme.onSurface,
    isHeader: Boolean = false,
) {
    val style = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val valueStyle = style.copy(fontFeatureSettings = "tnum")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = style, modifier = Modifier.weight(1f))
        listOf(wins to winsColor, losses to lossesColor).forEach { (text, color) ->
            Text(
                text = text,
                style = valueStyle,
                fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Bold,
                color = color,
                textAlign = TextAlign.End,
                modifier = Modifier.width(88.dp),
            )
        }
    }
}
