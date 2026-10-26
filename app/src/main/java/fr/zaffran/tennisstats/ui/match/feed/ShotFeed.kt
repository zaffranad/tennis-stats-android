package fr.zaffran.tennisstats.ui.match.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.zaffran.tennisstats.R
import fr.zaffran.tennisstats.domain.model.Shot
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType
import fr.zaffran.tennisstats.ui.components.ThemePreviews
import fr.zaffran.tennisstats.ui.match.recordedShotColor
import fr.zaffran.tennisstats.ui.match.shotFeedText
import fr.zaffran.tennisstats.ui.theme.TennisStatsTheme
import java.time.Instant

/** « Dernier coup (N total) » with « Annuler » and the last shot card (functional spec §8.2). */
@Composable
fun ShotFeed(
    shotCount: Int,
    lastShot: Shot?,
    canUndo: Boolean,
    isUndoing: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feed_last_shot, shotCount),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (canUndo && shotCount > 0) {
                TextButton(
                    onClick = onUndo,
                    enabled = !isUndoing,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_undo),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Box(Modifier.width(6.dp))
                    Text(stringResource(R.string.feed_undo))
                }
            }
        }
        if (lastShot == null) EmptyFeed() else LastShotCard(lastShot)
    }
}

@Composable
private fun LastShotCard(shot: Shot) {
    val color = recordedShotColor(shot)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.container,
        contentColor = color.content,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("last_shot"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .background(color.content, CircleShape),
            )
            Text(
                text = shotFeedText(shot),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun EmptyFeed() {
    val outline = MaterialTheme.colorScheme.outline
    Text(
        text = stringResource(R.string.feed_empty),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                    ),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                )
            }
            .padding(horizontal = 16.dp, vertical = 24.dp),
    )
}

@ThemePreviews
@Composable
private fun ShotFeedPreview() {
    TennisStatsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ShotFeed(
                shotCount = 12,
                lastShot = Shot("s", "m", ShotType.FOREHAND, ShotOutcome.WINNER, false, Instant.EPOCH),
                canUndo = true,
                isUndoing = false,
                onUndo = {},
            )
            ShotFeed(
                shotCount = 13,
                lastShot = Shot("s", "m", ShotType.SERVE, ShotOutcome.NET, true, Instant.EPOCH),
                canUndo = true,
                isUndoing = false,
                onUndo = {},
            )
            ShotFeed(shotCount = 0, lastShot = null, canUndo = true, isUndoing = false, onUndo = {})
        }
    }
}
