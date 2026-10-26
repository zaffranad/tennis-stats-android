package fr.zaffran.tennisstats.ui.match.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Column as LayoutColumn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zaffran.tennisstats.domain.input.Column
import fr.zaffran.tennisstats.domain.input.ShotInputState
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType
import fr.zaffran.tennisstats.ui.match.outcomeColor
import fr.zaffran.tennisstats.ui.match.outcomeIcon
import fr.zaffran.tennisstats.ui.match.outcomeLabel
import fr.zaffran.tennisstats.ui.match.serveLabel
import fr.zaffran.tennisstats.ui.match.shotTypeColor
import fr.zaffran.tennisstats.ui.match.shotTypeIcon
import fr.zaffran.tennisstats.ui.match.shotTypeLabel
import fr.zaffran.tennisstats.ui.theme.SemanticColor

private val OutcomeButtonHeight = 64.dp

/** Indent of « Winner », « Out » and « Filet » on the outer side: a tactile landmark around « En jeu ». */
private val OuterIndent = 20.dp
private val ColumnCorner = 20.dp

/**
 * Backhand column (left edge), modifiers (centre, ~20 %) and forehand / serve column (right edge),
 * functional spec §8.3.
 */
@Composable
fun GroundstrokeColumns(
    input: ShotInputState,
    enabled: Boolean,
    onOutcome: (Column, ShotOutcome) -> Unit,
    onToggleModifier: (ShotType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        OutcomeColumn(Column.LEFT, input, enabled, onOutcome, Modifier.weight(0.4f))
        ModifierColumn(
            activeMode = input.activeMode,
            enabled = enabled,
            onToggleModifier = onToggleModifier,
            modifier = Modifier
                .weight(0.2f)
                .fillMaxHeight()
                .padding(horizontal = 6.dp),
        )
        OutcomeColumn(Column.RIGHT, input, enabled, onOutcome, Modifier.weight(0.4f))
    }
}

@Composable
private fun OutcomeColumn(
    column: Column,
    input: ShotInputState,
    enabled: Boolean,
    onOutcome: (Column, ShotOutcome) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLeft = column == Column.LEFT
    val mode = input.activeMode
    val isServeColumn = mode == ShotType.SERVE && !isLeft
    // In serve mode the backhand column is greyed out and disabled.
    val greyed = mode == ShotType.SERVE && isLeft
    val columnEnabled = enabled && !greyed
    val headerType = when {
        mode == null || mode == ShotType.SERVE -> if (isLeft) {
            ShotType.BACKHAND
        } else if (isServeColumn) {
            ShotType.SERVE
        } else {
            ShotType.FOREHAND
        }

        else -> mode
    }
    val headerColor = if (greyed) disabledColor() else shotTypeColor(headerType, input.serveNumber)
    val headerLabel = if (isServeColumn) serveLabel(input.serveNumber) else shotTypeLabel(headerType)
    val shape = if (isLeft) {
        RoundedCornerShape(topEnd = ColumnCorner, bottomEnd = ColumnCorner)
    } else {
        RoundedCornerShape(topStart = ColumnCorner, bottomStart = ColumnCorner)
    }
    val outcomes = listOf(
        ShotOutcome.IN,
        if (isServeColumn) ShotOutcome.ACE else ShotOutcome.WINNER,
        ShotOutcome.FAULT,
        ShotOutcome.NET,
    )
    LayoutColumn(
        modifier = modifier
            .clip(shape)
            .background(headerColor.container)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ColumnHeader(
            label = headerLabel,
            icon = shotTypeIcon(headerType),
            color = headerColor,
            isLeft = isLeft,
            modifier = Modifier.testTag("header_${column.name.lowercase()}"),
        )
        outcomes.forEach { outcome ->
            val indent = if (outcome == ShotOutcome.IN) 0.dp else OuterIndent
            OutcomeButton(
                outcome = outcome,
                enabled = columnEnabled,
                greyed = greyed,
                onClick = { onOutcome(column, outcome) },
                modifier = Modifier
                    .padding(
                        start = if (isLeft) indent else 6.dp,
                        end = if (isLeft) 6.dp else indent,
                    )
                    .testTag("outcome_${column.name.lowercase()}_${outcome.value}"),
            )
        }
    }
}

@Composable
private fun ColumnHeader(
    label: String,
    icon: Int,
    color: SemanticColor,
    isLeft: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLeft) HeaderIcon(icon, color)
        Text(
            text = label,
            color = color.content,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (!isLeft) HeaderIcon(icon, color)
    }
}

@Composable
private fun HeaderIcon(icon: Int, color: SemanticColor) {
    Icon(painterResource(icon), contentDescription = null, tint = color.content, modifier = Modifier.size(18.dp))
}

@Composable
private fun OutcomeButton(
    outcome: ShotOutcome,
    enabled: Boolean,
    greyed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (greyed) disabledColor() else outcomeColor(outcome)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = color.content,
        contentColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .height(OutcomeButtonHeight),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(outcomeIcon(outcome)), contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = outcomeLabel(outcome),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** Volée, Smash, Lob, Amorti stacked in the centre column. */
@Composable
private fun ModifierColumn(
    activeMode: ShotType?,
    enabled: Boolean,
    onToggleModifier: (ShotType) -> Unit,
    modifier: Modifier = Modifier,
) {
    LayoutColumn(modifier = modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ShotType.SPECIAL_SHOTS.forEach { type ->
            ModifierButton(
                type = type,
                selected = activeMode == type,
                enabled = enabled,
                onClick = { onToggleModifier(type) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ModifierButton(
    type: ShotType,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = shotTypeColor(type)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) color.container else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (selected) color.content else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) BorderStroke(2.dp, color.content) else null,
        shadowElevation = if (selected) 4.dp else 0.dp,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("modifier_${type.value}")
            .semantics {
                this.selected = selected
                role = Role.Button
            },
    ) {
        LayoutColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(painterResource(shotTypeIcon(type)), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = shotTypeLabel(type),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun disabledColor() = SemanticColor(
    container = MaterialTheme.colorScheme.surfaceContainerHighest,
    content = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
)
