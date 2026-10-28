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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * « Derniers coups (N total) »: the most recent shots, newest first, one compact row each.
 * While the match is in progress ([canDelete]), each row has a button deleting that shot (RG-06).
 */
@Composable
fun ShotFeed(
    shotCount: Int,
    recentShots: List<Shot>,
    canDelete: Boolean,
    onDeleteShot: (Shot) -> Unit,
    modifier: Modifier = Modifier,
    showDeleteButtons: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.feed_recent_shots, shotCount),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (recentShots.isEmpty()) {
            EmptyFeed()
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    recentShots.forEachIndexed { index, shot ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ShotRow(
                            shot = shot,
                            number = shotCount - index,
                            isLatest = index == 0,
                            showDeleteButton = showDeleteButtons,
                            canDelete = canDelete,
                            onDelete = { onDeleteShot(shot) },
                        )
                    }
                }
            }
        }
    }
}

/** One shot: number, coloured dot, « {Type} → {Résultat} » and its delete button. The latest one is tinted. */
@Composable
private fun ShotRow(
    shot: Shot,
    number: Int,
    isLatest: Boolean,
    showDeleteButton: Boolean,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    val color = recordedShotColor(shot)
    val text = shotFeedText(shot)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (isLatest) color.container else Color.Transparent)
            .padding(start = 12.dp, end = 4.dp)
            .testTag(if (isLatest) "last_shot" else "shot_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (isLatest) color.content else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 24.dp),
        )
        Box(
            Modifier
                .padding(start = 10.dp)
                .size(10.dp)
                .background(color.content, CircleShape),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isLatest) FontWeight.Bold else FontWeight.Medium,
            color = if (isLatest) color.content else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
        )
        if (showDeleteButton) {
            IconButton(
                onClick = onDelete,
                enabled = canDelete,
                modifier = Modifier.testTag("delete_shot_$number"),
            ) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.feed_delete_shot, number, text),
                    modifier = Modifier.size(20.dp),
                )
            }
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
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
            }
            .padding(horizontal = 16.dp, vertical = 24.dp),
    )
}

@ThemePreviews
@Composable
private fun ShotFeedPreview() {
    val shots = listOf(
        Shot("s5", "m", ShotType.SERVE, ShotOutcome.NET, true, Instant.EPOCH),
        Shot("s4", "m", ShotType.SERVE, ShotOutcome.FAULT, false, Instant.EPOCH),
        Shot("s3", "m", ShotType.FOREHAND, ShotOutcome.WINNER, false, Instant.EPOCH),
        Shot("s2", "m", ShotType.VOLLEY, ShotOutcome.IN, false, Instant.EPOCH),
        Shot("s1", "m", ShotType.BACKHAND, ShotOutcome.FAULT, false, Instant.EPOCH),
    )
    TennisStatsTheme {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ShotFeed(shotCount = 12, recentShots = shots, canDelete = true, onDeleteShot = {})
                ShotFeed(
                    shotCount = 2,
                    recentShots = shots.take(2),
                    canDelete = false,
                    onDeleteShot = {},
                    showDeleteButtons = false,
                )
                ShotFeed(shotCount = 0, recentShots = emptyList(), canDelete = true, onDeleteShot = {})
            }
        }
    }
}
