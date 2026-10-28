package fr.zaffran.tennisstats.ui.match.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.zaffran.tennisstats.R
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
 * Input bar fixed at the bottom of the match screen (functional spec §8.3): feedback banner and
 * « Annuler » (last shot, within thumb reach), serve selector, backhand / modifiers / forehand-serve
 * columns. Disabled and half transparent while a shot is being written.
 */
@Composable
fun ShotTracker(
    input: ShotInputState,
    isRecording: Boolean,
    feedback: ShotFeedback?,
    canUndo: Boolean,
    onAction: (InputAction) -> Unit,
    onUndo: () -> Unit,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    FeedbackBanner(feedback, Modifier.align(Alignment.Center))
                    if (isRecording) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(16.dp),
                        )
                    }
                }
                UndoButton(enabled = canUndo, onClick = onUndo, modifier = Modifier.padding(start = 8.dp))
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

/** « Annuler »: deletes the last shot (RG-06). Always in place, disabled when there is nothing to undo. */
@Composable
private fun UndoButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.tracker_undo_description)
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = modifier
            .height(40.dp)
            .testTag("undo_last_shot")
            .semantics { contentDescription = description },
    ) {
        Icon(painterResource(R.drawable.ic_undo), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(R.string.tracker_undo),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun ShotTrackerPreview(input: ShotInputState, isRecording: Boolean = false, feedback: ShotFeedback? = null) {
    TennisStatsTheme {
        ShotTracker(
            input = input,
            isRecording = isRecording,
            feedback = feedback,
            canUndo = !isRecording,
            onAction = {},
            onUndo = {},
        )
    }
}

private fun previewFeedback(
    type: ShotType,
    outcome: ShotOutcome,
    isSecondServe: Boolean = false,
    isUndone: Boolean = false,
) = ShotFeedback(0, Shot("s", "m", type, outcome, isSecondServe, Instant.EPOCH), isUndone)

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

@ThemePreviews
@Composable
private fun ShotTrackerUndonePreview() {
    ShotTrackerPreview(ShotInputState(), feedback = previewFeedback(ShotType.VOLLEY, ShotOutcome.NET, isUndone = true))
}
