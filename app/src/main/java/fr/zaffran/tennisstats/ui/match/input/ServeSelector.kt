package fr.zaffran.tennisstats.ui.match.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.zaffran.tennisstats.domain.input.ShotInputState
import fr.zaffran.tennisstats.domain.model.ShotType
import fr.zaffran.tennisstats.ui.match.serveLabel
import fr.zaffran.tennisstats.ui.match.shotTypeColor

/** « 1er service » / « 2e service » toggles (functional spec §8.3, ServeSelector). */
@Composable
fun ServeSelector(
    input: ShotInputState,
    enabled: Boolean,
    onToggleServe: (serveNumber: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeNumber = if (input.isServeMode) input.serveNumber else null
    val outline = activeNumber?.let { shotTypeColor(ShotType.SERVE, it).content }
        ?: MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(2.dp, outline), RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(1, 2).forEach { number ->
            ServeButton(
                serveNumber = number,
                selected = activeNumber == number,
                enabled = enabled,
                onClick = { onToggleServe(number) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ServeButton(
    serveNumber: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = shotTypeColor(ShotType.SERVE, serveNumber)
    val container = if (selected) color.content else MaterialTheme.colorScheme.surfaceContainerHighest
    val content = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = container,
        contentColor = content,
        modifier = modifier
            .height(52.dp)
            .testTag("serve_$serveNumber")
            .semantics {
                this.selected = selected
                role = Role.Button
            },
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            NumberPill(
                serveNumber,
                background = if (selected) content else Color.Transparent,
                foreground = if (selected) container else content,
            )
            Text(
                text = serveLabel(serveNumber),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun NumberPill(number: Int, background: Color, foreground: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(24.dp)
            .background(background, CircleShape)
            .border(1.5.dp, foreground, CircleShape),
    ) {
        Text(
            text = number.toString(),
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
