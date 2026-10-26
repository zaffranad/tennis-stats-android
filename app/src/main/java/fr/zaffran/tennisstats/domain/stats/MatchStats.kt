package fr.zaffran.tennisstats.domain.stats

import fr.zaffran.tennisstats.domain.model.ShotType

/**
 * Statistics of a match (functional spec §9). A null section must not be displayed.
 * Percentages are null when they cannot be computed (displayed « — »).
 */
data class MatchStats(
    val totalShots: Int,
    val serve: ServeStats?,
    val groundstrokes: GroundstrokeStats?,
    val specialShots: List<SpecialShotStats>?,
) {
    val isEmpty: Boolean get() = totalShots == 0
}

/** Section « Service ». */
data class ServeStats(
    val firstServesIn: Int,
    val firstServes: Int,
    val firstServePct: Int?,
    val secondServesIn: Int,
    val secondServes: Int,
    val secondServePct: Int?,
    val aces: Int,
    val doubleFaults: Int,
)

/** Section « Coup droit » / « Revers »: a null stroke is omitted from the card. */
data class GroundstrokeStats(val forehand: StrokeStats?, val backhand: StrokeStats?)

/** Stats of one stroke type; [total] is always > 0. */
data class StrokeStats(
    val total: Int,
    val successful: Int,
    val winners: Int,
    val net: Int,
    val out: Int,
    val successPct: Int,
    /** Winners as a percentage of successful shots; null if none succeeded. */
    val winnerPct: Int?,
    val netPct: Int,
    val outPct: Int,
)

/** One tile of the « Coups spéciaux » section; [total] is always > 0. */
data class SpecialShotStats(val type: ShotType, val total: Int, val successPct: Int)
