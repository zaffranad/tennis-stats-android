package fr.zaffran.tennisstats.ui.match.finish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.zaffran.tennisstats.R
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.ui.theme.SemanticColor
import fr.zaffran.tennisstats.ui.theme.TennisStatsTheme

/** « Terminer le match » sheet: Victoire, Défaite, or cancel and delete the match (functional spec §8.4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishMatchSheet(
    isBusy: Boolean,
    onFinish: (MatchResult) -> Unit,
    onCancelAndDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.finish_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                if (isBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            val colors = TennisStatsTheme.semanticColors
            ResultButton(R.string.result_win, R.drawable.ic_emoji_events, colors.emerald, !isBusy) {
                onFinish(MatchResult.WIN)
            }
            ResultButton(R.string.result_loss, R.drawable.ic_thumb_down, colors.red, !isBusy) {
                onFinish(MatchResult.LOSS)
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            TextButton(
                onClick = onCancelAndDelete,
                enabled = !isBusy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.finish_cancel_and_delete))
            }
        }
    }
}

@Composable
private fun ResultButton(label: Int, icon: Int, color: SemanticColor, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color.content,
            contentColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

/** Confirmation before deleting a match and all its shots (RG-05, RG-07). */
@Composable
fun DeleteMatchDialog(isBusy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
        title = { Text(stringResource(R.string.delete_dialog_title)) },
        text = { Text(stringResource(R.string.delete_dialog_text)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isBusy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.delete_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(stringResource(R.string.delete_dialog_dismiss))
            }
        },
    )
}
