package fr.zaffran.tennisstats.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.zaffran.tennisstats.R
import fr.zaffran.tennisstats.domain.model.OpponentLevel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display label of a stored opponent level; unknown values are displayed as is. */
@Composable
fun opponentLevelLabel(value: String): String = when (OpponentLevel.fromValue(value)) {
    OpponentLevel.DEBUTANT -> stringResource(R.string.level_debutant)
    OpponentLevel.INTERMEDIAIRE -> stringResource(R.string.level_intermediaire)
    OpponentLevel.AVANCE -> stringResource(R.string.level_avance)
    OpponentLevel.EXPERT -> stringResource(R.string.level_expert)
    OpponentLevel.NC -> stringResource(R.string.level_nc)
    else -> value
}

/**
 * « {n} coup(s) »: singular only for n = 1 (web rule). French plural rules put 0 in the "one"
 * category, so 0 is resolved with an "other" quantity.
 */
@Composable
fun shotCountText(count: Int): String =
    pluralStringResource(R.plurals.shot_count, if (count == 0) OTHER_QUANTITY else count, count)

private const val OTHER_QUANTITY = 2

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)

/** `dd/MM/yyyy` in the device time zone. */
fun formatDate(instant: Instant, zoneId: ZoneId = ZoneId.systemDefault()): String =
    DATE_FORMATTER.format(instant.atZone(zoneId))
