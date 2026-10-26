package fr.zaffran.tennisstats.ui.match.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import fr.zaffran.tennisstats.domain.input.InputAction
import fr.zaffran.tennisstats.domain.input.ShotInputState
import fr.zaffran.tennisstats.domain.model.Shot
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType
import fr.zaffran.tennisstats.ui.components.ThemePreviews
import fr.zaffran.tennisstats.ui.match.ShotFeedback
import fr.zaffran.tennisstats.ui.theme.TennisStatsTheme
import java.time.Instant

/**
 * Input bar fixed at the bottom of the match screen (functional spec §8.3): feedback banner,
 * serve selector, backhand / modifiers / forehand-serve columns. Disabled and half transparent
 * while a shot is being written.
 */
@Composable
fun ShotTracker(
    input: ShotInputState,
    isRecording: Boolean,
    feedback: ShotFeedback?,
    onAction: (InputAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !isRecording
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier.testTag("shot_tracker"),
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .alpha(if (isRecording) 0.5f else 1f)
                .padding(bottom = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                FeedbackBanner(feedback, Modifier.align(Alignment.Center))
                if (isRecording) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp)
                            .size(16.dp),
                    )
                }
            }
            ServeSelector(
                input = input,
                enabled = enabled,
                onToggleServe = { onAction(InputAction.ToggleServe(it)) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(10.dp))
            GroundstrokeColumns(
                input = input,
                enabled = enabled,
                onOutcome = { column, outcome -> onAction(InputAction.TapOutcome(column, outcome)) },
                onToggleModifier = { onAction(InputAction.ToggleModifier(it)) },
            )
        }
    }
}

@Composable
private fun ShotTrackerPreview(input: ShotInputState, isRecording: Boolean = false, feedback: ShotFeedback? = null) {
    TennisStatsTheme {
        ShotTracker(input = input, isRecording = isRecording, feedback = feedback, onAction = {})
    }
}

private fun previewFeedback(type: ShotType, outcome: ShotOutcome, isSecondServe: Boolean = false) =
    ShotFeedback(0, Shot("s", "m", type, outcome, isSecondServe, Instant.EPOCH))

@ThemePreviews
@Composable
private fun ShotTrackerNormalPreview() {
    ShotTrackerPreview(ShotInputState(), feedback = previewFeedback(ShotType.FOREHAND, ShotOutcome.WINNER))
}

@ThemePreviews
@Composable
private fun ShotTrackerFirstServePreview() {
    ShotTrackerPreview(ShotInputState(activeMode = ShotType.SERVE, serveNumber = 1))
}

@ThemePreviews
@Composable
private fun ShotTrackerSecondServePreview() {
    ShotTrackerPreview(
        ShotInputState(activeMode = ShotType.SERVE, serveNumber = 2),
        feedback = previewFeedback(ShotType.SERVE, ShotOutcome.FAULT),
    )
}

@ThemePreviews
@Composable
private fun ShotTrackerModifierPreview() {
    ShotTrackerPreview(ShotInputState(activeMode = ShotType.VOLLEY))
}

@ThemePreviews
@Composable
private fun ShotTrackerRecordingPreview() {
    ShotTrackerPreview(
        ShotInputState(),
        isRecording = true,
        feedback = previewFeedback(ShotType.SERVE, ShotOutcome.NET, isSecondServe = true),
    )
}
