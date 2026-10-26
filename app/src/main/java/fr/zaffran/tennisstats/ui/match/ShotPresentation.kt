package fr.zaffran.tennisstats.ui.match

import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import fr.zaffran.tennisstats.R
import fr.zaffran.tennisstats.domain.model.Shot
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType
import fr.zaffran.tennisstats.ui.theme.SemanticColor
import fr.zaffran.tennisstats.ui.theme.TennisStatsTheme

/** Labels, icons and colours of shot types and outcomes (functional spec §3.3 and §10). */

@Composable
fun shotTypeLabel(type: ShotType): String = stringResource(
    when (type) {
        ShotType.SERVE -> R.string.shot_type_serve
        ShotType.FOREHAND -> R.string.shot_type_forehand
        ShotType.BACKHAND -> R.string.shot_type_backhand
        ShotType.VOLLEY -> R.string.shot_type_volley
        ShotType.SMASH -> R.string.shot_type_smash
        ShotType.LOB -> R.string.shot_type_lob
        ShotType.DROP_SHOT -> R.string.shot_type_drop_shot
    },
)

@Composable
fun outcomeLabel(outcome: ShotOutcome): String = stringResource(
    when (outcome) {
        ShotOutcome.IN -> R.string.shot_outcome_in
        ShotOutcome.WINNER -> R.string.shot_outcome_winner
        ShotOutcome.ACE -> R.string.shot_outcome_ace
        ShotOutcome.FAULT -> R.string.shot_outcome_fault
        ShotOutcome.NET -> R.string.shot_outcome_net
        ShotOutcome.UNFORCED_ERROR -> R.string.shot_outcome_unforced_error
    },
)

/** « 1er service » / « 2e service ». */
@Composable
fun serveLabel(serveNumber: Int): String =
    stringResource(if (serveNumber == 2) R.string.tracker_second_serve else R.string.tracker_first_serve)

@DrawableRes
fun outcomeIcon(outcome: ShotOutcome): Int = when (outcome) {
    ShotOutcome.IN -> R.drawable.ic_check_circle
    ShotOutcome.WINNER -> R.drawable.ic_star
    ShotOutcome.ACE -> R.drawable.ic_bolt
    ShotOutcome.FAULT, ShotOutcome.UNFORCED_ERROR -> R.drawable.ic_cancel
    ShotOutcome.NET -> R.drawable.ic_fence
}

@DrawableRes
fun shotTypeIcon(type: ShotType): Int = when (type) {
    ShotType.SERVE -> R.drawable.ic_bolt
    ShotType.FOREHAND -> R.drawable.ic_north_east
    ShotType.BACKHAND -> R.drawable.ic_south_east
    ShotType.VOLLEY -> R.drawable.ic_back_hand
    ShotType.SMASH -> R.drawable.ic_local_fire_department
    ShotType.LOB -> R.drawable.ic_north
    ShotType.DROP_SHOT -> R.drawable.ic_south
}

@Composable
@ReadOnlyComposable
fun outcomeColor(outcome: ShotOutcome): SemanticColor {
    val colors = TennisStatsTheme.semanticColors
    return when (outcome) {
        ShotOutcome.IN -> colors.green
        ShotOutcome.WINNER, ShotOutcome.ACE -> colors.emerald
        ShotOutcome.FAULT -> colors.orange
        ShotOutcome.NET, ShotOutcome.UNFORCED_ERROR -> colors.red
    }
}

/** Colour of a shot type; a serve is violet (1st) or amber (2nd). */
@Composable
@ReadOnlyComposable
fun shotTypeColor(type: ShotType, serveNumber: Int = 1): SemanticColor {
    val colors = TennisStatsTheme.semanticColors
    return when (type) {
        ShotType.SERVE -> if (serveNumber == 2) colors.amber else colors.violet
        ShotType.FOREHAND -> colors.blue
        ShotType.BACKHAND -> colors.cyan
        ShotType.VOLLEY -> colors.amber
        ShotType.SMASH -> colors.red
        ShotType.LOB -> colors.teal
        ShotType.DROP_SHOT -> colors.orange
    }
}

/** Colour of a recorded shot: its outcome, or dark red for a double fault. */
@Composable
@ReadOnlyComposable
fun recordedShotColor(shot: Shot): SemanticColor = if (shot.isDoubleFault) {
    SemanticColor(container = MaterialTheme.colorScheme.error, content = MaterialTheme.colorScheme.onError)
} else {
    outcomeColor(shot.outcome)
}

/** Feedback banner text: « Coup droit → Winner », « 1er service → Ace » or « Double faute ». */
@Composable
fun shotFeedbackText(shot: Shot): String = when {
    shot.isDoubleFault -> stringResource(R.string.shot_double_fault)

    shot.type == ShotType.SERVE ->
        stringResource(
            R.string.shot_description,
            serveLabel(if (shot.isSecondServe) 2 else 1),
            outcomeLabel(shot.outcome),
        )

    else -> stringResource(R.string.shot_description, shotTypeLabel(shot.type), outcomeLabel(shot.outcome))
}

/** Feed text: « Service (1er) → Out », « Revers → En jeu » or « Double faute ». */
@Composable
fun shotFeedText(shot: Shot): String = when {
    shot.isDoubleFault -> stringResource(R.string.shot_double_fault)

    shot.type == ShotType.SERVE -> stringResource(
        R.string.shot_description,
        stringResource(if (shot.isSecondServe) R.string.feed_serve_second else R.string.feed_serve_first),
        outcomeLabel(shot.outcome),
    )

    else -> stringResource(R.string.shot_description, shotTypeLabel(shot.type), outcomeLabel(shot.outcome))
}
