package fr.zaffran.tennisstats.ui.match.input

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zaffran.tennisstats.R
import fr.zaffran.tennisstats.ui.match.ShotFeedback
import fr.zaffran.tennisstats.ui.match.outcomeIcon
import fr.zaffran.tennisstats.ui.match.recordedShotColor
import fr.zaffran.tennisstats.ui.match.shotFeedbackText
import fr.zaffran.tennisstats.ui.theme.SemanticColor

/**
 * Animated confirmation of the last recorded shot, coloured by its outcome, or of a deleted shot
 * (« Annulé : … », neutral colour). Shown 2 s.
 */
@Composable
fun FeedbackBanner(feedback: ShotFeedback?, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = feedback,
        transitionSpec = { (fadeIn() + slideInVertically { -it / 2 }) togetherWith fadeOut() },
        contentKey = { it?.id },
        label = "feedback",
        modifier = modifier,
    ) { current ->
        if (current == null) {
            Box(Modifier)
        } else {
            val color = if (current.isUndone) {
                SemanticColor(
                    container = MaterialTheme.colorScheme.inverseSurface,
                    content = MaterialTheme.colorScheme.inverseOnSurface,
                )
            } else {
                recordedShotColor(current.shot)
            }
            Surface(
                shape = CircleShape,
                color = color.container,
                contentColor = color.content,
                modifier = Modifier
                    .testTag("feedback_banner")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val icon = if (current.isUndone) R.drawable.ic_undo else outcomeIcon(current.shot.outcome)
                    Icon(painterResource(icon), null, Modifier.size(18.dp))
                    val text = shotFeedbackText(current.shot)
                    Text(
                        text = if (current.isUndone) stringResource(R.string.feedback_undone, text) else text,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
