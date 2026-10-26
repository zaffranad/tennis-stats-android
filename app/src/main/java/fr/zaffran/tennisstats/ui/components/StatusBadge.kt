package fr.zaffran.tennisstats.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.zaffran.tennisstats.R
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.model.MatchStatus
import fr.zaffran.tennisstats.ui.theme.SemanticColor
import fr.zaffran.tennisstats.ui.theme.TennisStatsTheme

/** Badge « En cours » (pulsing dot), « Victoire » or « Défaite ». */
@Composable
fun MatchStatusBadge(status: MatchStatus, result: MatchResult?, modifier: Modifier = Modifier) {
    val colors = TennisStatsTheme.semanticColors
    when {
        status == MatchStatus.IN_PROGRESS ->
            Badge(stringResource(R.string.status_in_progress), colors.green, modifier, pulsingDot = true)

        result == MatchResult.WIN -> Badge(stringResource(R.string.result_win), colors.emerald, modifier)

        result == MatchResult.LOSS -> Badge(stringResource(R.string.result_loss), colors.red, modifier)
    }
}

@Composable
private fun Badge(text: String, color: SemanticColor, modifier: Modifier = Modifier, pulsingDot: Boolean = false) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (pulsingDot) PulsingDot(color.content)
        Text(
            text = text,
            color = color.content,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Small dot whose opacity pulses, marking a match in progress. */
@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Box(
        modifier = modifier
            .size(8.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color),
    )
}

@ThemePreviews
@Composable
private fun MatchStatusBadgePreview() {
    TennisStatsTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(8.dp)) {
            MatchStatusBadge(MatchStatus.IN_PROGRESS, null)
            MatchStatusBadge(MatchStatus.COMPLETED, MatchResult.WIN)
            MatchStatusBadge(MatchStatus.COMPLETED, MatchResult.LOSS)
        }
    }
}
