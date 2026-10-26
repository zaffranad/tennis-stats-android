package fr.zaffran.tennisstats.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** A background (container) colour and the foreground (content) colour drawn on it. */
@Immutable
data class SemanticColor(val container: Color, val content: Color)

/**
 * Stable colour coding of shot results and shot types (functional spec §10).
 * Never derived from dynamic colour so the meaning of each colour stays constant.
 */
@Immutable
data class SemanticColors(
    /** En jeu. */
    val green: SemanticColor,
    /** Winner, Ace, Victoire. */
    val emerald: SemanticColor,
    /** Out, Amorti. */
    val orange: SemanticColor,
    /** Filet, Défaite, Smash, Double faute. */
    val red: SemanticColor,
    /** 1er service. */
    val violet: SemanticColor,
    /** 2e service, Volée. */
    val amber: SemanticColor,
    /** Revers. */
    val cyan: SemanticColor,
    /** Coup droit. */
    val blue: SemanticColor,
    /** Lob. */
    val teal: SemanticColor,
)

/** Alpha applied to dark-theme containers (40 %). */
private const val DARK_CONTAINER_ALPHA = 0.4f

private fun darkContainer(color: Long) = Color(color).copy(alpha = DARK_CONTAINER_ALPHA)

val LightSemanticColors = SemanticColors(
    green = SemanticColor(Color(0xFFDCFCE7), Color(0xFF15803D)),
    emerald = SemanticColor(Color(0xFFD1FAE5), Color(0xFF047857)),
    orange = SemanticColor(Color(0xFFFFEDD5), Color(0xFFC2410C)),
    red = SemanticColor(Color(0xFFFEE2E2), Color(0xFFB91C1C)),
    violet = SemanticColor(Color(0xFFEDE9FE), Color(0xFF6D28D9)),
    amber = SemanticColor(Color(0xFFFEF3C7), Color(0xFFB45309)),
    cyan = SemanticColor(Color(0xFFCFFAFE), Color(0xFF0E7490)),
    blue = SemanticColor(Color(0xFFDBEAFE), Color(0xFF1D4ED8)),
    teal = SemanticColor(Color(0xFFCCFBF1), Color(0xFF0F766E)),
)

val DarkSemanticColors = SemanticColors(
    green = SemanticColor(darkContainer(0xFF14532D), Color(0xFF86EFAC)),
    emerald = SemanticColor(darkContainer(0xFF064E3B), Color(0xFF6EE7B7)),
    orange = SemanticColor(darkContainer(0xFF7C2D12), Color(0xFFFDBA74)),
    red = SemanticColor(darkContainer(0xFF7F1D1D), Color(0xFFFCA5A5)),
    violet = SemanticColor(darkContainer(0xFF4C1D95), Color(0xFFC4B5FD)),
    amber = SemanticColor(darkContainer(0xFF78350F), Color(0xFFFCD34D)),
    cyan = SemanticColor(darkContainer(0xFF164E63), Color(0xFF67E8F9)),
    blue = SemanticColor(darkContainer(0xFF1E3A8A), Color(0xFF93C5FD)),
    teal = SemanticColor(darkContainer(0xFF134E4A), Color(0xFF5EEAD4)),
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }
