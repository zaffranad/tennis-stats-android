package fr.zaffran.tennisstats.ui.match.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.zaffran.tennisstats.R
import fr.zaffran.tennisstats.domain.model.Shot
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType
import fr.zaffran.tennisstats.domain.stats.MatchStats
import fr.zaffran.tennisstats.domain.stats.ServeStats
import fr.zaffran.tennisstats.domain.stats.SpecialShotStats
import fr.zaffran.tennisstats.domain.stats.StatsCalculator
import fr.zaffran.tennisstats.domain.stats.StrokeStats
import fr.zaffran.tennisstats.ui.components.SectionHeader
import fr.zaffran.tennisstats.ui.components.StatTile
import fr.zaffran.tennisstats.ui.components.ThemePreviews
import fr.zaffran.tennisstats.ui.components.shotCountText
import fr.zaffran.tennisstats.ui.match.shotTypeLabel
import fr.zaffran.tennisstats.ui.theme.TennisStatsTheme
import java.time.Instant

private const val TILES_PER_ROW = 4

/** « Statistiques » tab (functional spec §9), updated live from the shots. */
@Composable
fun MatchStatsContent(stats: MatchStats, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    if (stats.isEmpty) {
        Box(modifier.fillMaxSize().padding(contentPadding).padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.stats_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        stats.serve?.let { serve -> item(key = "serve") { StatsCard { ServeSection(serve) } } }
        stats.groundstrokes?.let { groundstrokes ->
            item(key = "groundstrokes") {
                StatsCard {
                    groundstrokes.forehand?.let { StrokeSection(stringResource(R.string.stats_section_forehand), it) }
                    if (groundstrokes.forehand != null && groundstrokes.backhand != null) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }
                    groundstrokes.backhand?.let { StrokeSection(stringResource(R.string.stats_section_backhand), it) }
                }
            }
        }
        stats.specialShots?.let { specials -> item(key = "special") { StatsCard { SpecialShotsSection(specials) } } }
    }
}

@Composable
private fun StatsCard(content: @Composable () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) { content() }
    }
}

@Composable
private fun ServeSection(serve: ServeStats) {
    val colors = TennisStatsTheme.semanticColors
    SectionHeader(stringResource(R.string.stats_section_serve))
    TileRow(
        listOf(
            {
                StatTile(
                    value = percentText(serve.firstServePct),
                    label = stringResource(R.string.stats_first_serve),
                    color = colors.green.content,
                    subLine = stringResource(R.string.stats_ratio, serve.firstServesIn, serve.firstServes),
                    modifier = it,
                )
            },
            {
                StatTile(
                    value = percentText(serve.secondServePct),
                    label = stringResource(R.string.stats_second_serve),
                    color = colors.amber.content,
                    subLine = stringResource(R.string.stats_ratio, serve.secondServesIn, serve.secondServes),
                    modifier = it,
                )
            },
            {
                StatTile(serve.aces.toString(), stringResource(R.string.stats_aces), colors.emerald.content, it)
            },
            {
                StatTile(
                    serve.doubleFaults.toString(),
                    stringResource(R.string.stats_double_faults),
                    colors.red.content,
                    it,
                )
            },
        ),
    )
}

@Composable
private fun StrokeSection(title: String, stroke: StrokeStats) {
    val colors = TennisStatsTheme.semanticColors
    SectionHeader(title)
    TileRow(
        listOf(
            {
                StatTile(
                    value = percentText(stroke.successPct),
                    label = stringResource(R.string.stats_success),
                    color = colors.green.content,
                    subLine = stringResource(R.string.stats_out_of, stroke.total),
                    modifier = it,
                )
            },
            {
                StatTile(
                    value = percentText(stroke.winnerPct),
                    label = stringResource(R.string.stats_winners),
                    color = colors.emerald.content,
                    subLine = stringResource(R.string.stats_of_successes),
                    modifier = it,
                )
            },
            { StatTile(percentText(stroke.netPct), stringResource(R.string.stats_net), colors.red.content, it) },
            { StatTile(percentText(stroke.outPct), stringResource(R.string.stats_out), colors.orange.content, it) },
        ),
    )
}

@Composable
private fun SpecialShotsSection(specials: List<SpecialShotStats>) {
    val green = TennisStatsTheme.semanticColors.green.content
    SectionHeader(stringResource(R.string.stats_section_special))
    specials.chunked(TILES_PER_ROW).forEach { row ->
        TileRow(
            row.map { special ->
                { modifier: Modifier ->
                    StatTile(
                        value = percentText(special.successPct),
                        label = shotTypeLabel(special.type),
                        color = green,
                        subLine = shotCountText(special.total),
                        modifier = modifier,
                    )
                }
            },
        )
    }
}

/** Up to four tiles of equal width. */
@Composable
private fun TileRow(tiles: List<@Composable (Modifier) -> Unit>) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Top) {
        tiles.forEach { tile -> tile(Modifier.weight(1f)) }
        repeat(TILES_PER_ROW - tiles.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** « {pct}% » or « — » when not computable. */
@Composable
private fun percentText(pct: Int?): String =
    pct?.let { stringResource(R.string.stats_percent, it) } ?: stringResource(R.string.stats_not_available)

@ThemePreviews
@Composable
private fun MatchStatsContentPreview() {
    fun shot(type: ShotType, outcome: ShotOutcome, second: Boolean = false) =
        Shot("s", "m", type, outcome, second, Instant.EPOCH)
    val stats = StatsCalculator.computeStats(
        listOf(
            shot(ShotType.SERVE, ShotOutcome.FAULT),
            shot(ShotType.SERVE, ShotOutcome.IN, second = true),
            shot(ShotType.SERVE, ShotOutcome.ACE),
            shot(ShotType.FOREHAND, ShotOutcome.WINNER),
            shot(ShotType.FOREHAND, ShotOutcome.NET),
            shot(ShotType.BACKHAND, ShotOutcome.FAULT),
            shot(ShotType.VOLLEY, ShotOutcome.IN),
            shot(ShotType.DROP_SHOT, ShotOutcome.IN),
            shot(ShotType.DROP_SHOT, ShotOutcome.NET),
        ),
    )
    TennisStatsTheme {
        MatchStatsContent(stats, PaddingValues(16.dp))
    }
}

@ThemePreviews
@Composable
private fun MatchStatsEmptyPreview() {
    TennisStatsTheme {
        MatchStatsContent(StatsCalculator.computeStats(emptyList()), PaddingValues(16.dp))
    }
}
