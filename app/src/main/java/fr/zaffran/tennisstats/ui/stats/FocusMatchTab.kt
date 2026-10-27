package fr.zaffran.tennisstats.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zaffran.tennisstats.R
import fr.zaffran.tennisstats.domain.stats.MetricComparison
import fr.zaffran.tennisstats.navigation.MatchTab
import fr.zaffran.tennisstats.ui.components.MatchStatusBadge
import fr.zaffran.tennisstats.ui.components.SectionHeader
import fr.zaffran.tennisstats.ui.components.StatsCard
import fr.zaffran.tennisstats.ui.components.formatDate
import fr.zaffran.tennisstats.ui.components.opponentLevelLabel
import fr.zaffran.tennisstats.ui.components.shotCountText
import fr.zaffran.tennisstats.ui.match.stats.matchStatsSections

/** « Match » tab (statistics spec §3): the in-progress match, otherwise the last completed one. */
@Composable
fun FocusMatchTab(
    focus: FocusMatch?,
    contentPadding: PaddingValues,
    onOpenMatch: (matchId: String, tab: MatchTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (focus == null) {
            item(key = "empty") {
                StatsEmptyState(
                    title = stringResource(R.string.stats_no_match_title),
                    subtitle = stringResource(R.string.stats_no_match_subtitle),
                )
            }
            return@LazyColumn
        }
        item(key = "focus") {
            FocusMatchCard(
                focus = focus,
                onOpenMatch = {
                    onOpenMatch(focus.match.id, if (focus.match.isInProgress) MatchTab.INPUT else MatchTab.STATS)
                },
            )
        }
        if (focus.stats.isEmpty) {
            item(key = "noStats") {
                Text(
                    text = stringResource(R.string.stats_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )
            }
        } else {
            matchStatsSections(focus.stats)
        }
        if (focus.comparison.isNotEmpty()) {
            item(key = "comparison") { ComparisonCard(focus.comparison, focus.comparedMatchCount) }
        }
    }
}

@Composable
private fun FocusMatchCard(focus: FocusMatch, onOpenMatch: () -> Unit, modifier: Modifier = Modifier) {
    val match = focus.match
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(
                    if (match.isInProgress) R.string.stats_focus_in_progress else R.string.stats_focus_last_completed,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.opponent_vs, match.opponentName),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                MatchStatusBadge(match.status, match.result)
            }
            match.opponentLevel?.let { level ->
                Text(
                    text = stringResource(R.string.opponent_level, opponentLevelLabel(level)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${formatDate(match.playedAt)} · ${shotCountText(focus.shotCount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenMatch) { Text(stringResource(R.string.stats_open_match)) }
            }
        }
    }
}

/** « Comparaison » card: this match against the average of the other completed matches. */
@Composable
private fun ComparisonCard(comparison: List<MetricComparison>, comparedMatchCount: Int) {
    StatsCard {
        SectionHeader(stringResource(R.string.stats_section_comparison))
        Text(
            text = pluralStringResource(R.plurals.stats_comparison_subtitle, comparedMatchCount, comparedMatchCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
        )
        comparison.forEach { ComparisonRow(it) }
    }
}

@Composable
private fun ComparisonRow(comparison: MetricComparison) {
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = metricLabel(comparison.metric),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = metricValueText(comparison.metric, comparison.value),
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(
                R.string.stats_comparison_average,
                aggregateText(comparison.metric, comparison.reference),
            ),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = neutral,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 64.dp),
        )
        Text(
            text = deltaText(comparison.metric, comparison.delta),
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = deltaColor(comparison.metric, comparison.delta, neutral),
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 64.dp),
        )
    }
}
