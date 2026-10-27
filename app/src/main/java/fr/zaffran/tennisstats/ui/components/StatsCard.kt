package fr.zaffran.tennisstats.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.zaffran.tennisstats.R

const val TILES_PER_ROW = 4

/** Card holding one or several statistics sections. */
@Composable
fun StatsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) { content() }
    }
}

/** Up to four tiles of equal width. */
@Composable
fun StatTileRow(tiles: List<@Composable (Modifier) -> Unit>) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Top) {
        tiles.forEach { tile -> tile(Modifier.weight(1f)) }
        repeat(TILES_PER_ROW - tiles.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** « {pct}% » or « — » when not computable. */
@Composable
fun percentText(pct: Int?): String =
    pct?.let { stringResource(R.string.stats_percent, it) } ?: stringResource(R.string.stats_not_available)
