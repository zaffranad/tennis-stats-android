package fr.zaffran.tennisstats.domain.stats

import fr.zaffran.tennisstats.domain.model.Shot
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType
import kotlin.math.roundToInt

/** Computes match statistics from its shots (functional spec §9). Pure functions. */
object StatsCalculator {

    fun computeStats(shots: List<Shot>): MatchStats = computeStats(ShotTally.of(shots))

    /** Same statistics from a tally; the tally of several matches gives pooled statistics. */
    fun computeStats(tally: ShotTally): MatchStats {
        val forehand = strokeStats(tally, ShotType.FOREHAND)
        val backhand = strokeStats(tally, ShotType.BACKHAND)
        val specials = ShotType.SPECIAL_SHOTS.mapNotNull { specialShotStats(tally, it) }
        return MatchStats(
            totalShots = tally.total,
            serve = serveStats(tally),
            groundstrokes = if (forehand != null || backhand != null) GroundstrokeStats(forehand, backhand) else null,
            specialShots = specials.ifEmpty { null },
        )
    }

    /** `round(part / total × 100)`, half up; null when [total] is 0. */
    fun percentage(part: Int, total: Int): Int? = if (total == 0) null else (part * 100.0 / total).roundToInt()

    private fun serveStats(tally: ShotTally): ServeStats? {
        if (tally.count { it.type == ShotType.SERVE } == 0) return null
        fun serves(second: Boolean, predicate: (ShotOutcome) -> Boolean = { true }) =
            tally.count { it.type == ShotType.SERVE && it.isSecondServe == second && predicate(it.outcome) }
        val first = serves(second = false)
        val firstIn = serves(second = false) { it.isServeIn }
        val second = serves(second = true)
        val secondIn = serves(second = true) { it.isServeIn }
        return ServeStats(
            firstServesIn = firstIn,
            firstServes = first,
            firstServePct = percentage(firstIn, first),
            secondServesIn = secondIn,
            secondServes = second,
            secondServePct = percentage(secondIn, second),
            aces = tally.count { it.type == ShotType.SERVE && it.outcome == ShotOutcome.ACE },
            doubleFaults = serves(second = true) { it.isMiss },
        )
    }

    private fun strokeStats(tally: ShotTally, type: ShotType): StrokeStats? {
        fun count(predicate: (ShotOutcome) -> Boolean = { true }) =
            tally.count { it.type == type && predicate(it.outcome) }
        val total = count()
        if (total == 0) return null
        val successful = count { it.isSuccess }
        val winners = count { it == ShotOutcome.WINNER }
        val net = count { it == ShotOutcome.NET }
        val out = count { it == ShotOutcome.FAULT }
        return StrokeStats(
            total = total,
            successful = successful,
            winners = winners,
            net = net,
            out = out,
            successPct = percentage(successful, total)!!,
            winnerPct = percentage(winners, successful),
            netPct = percentage(net, total)!!,
            outPct = percentage(out, total)!!,
        )
    }

    private fun specialShotStats(tally: ShotTally, type: ShotType): SpecialShotStats? {
        val total = tally.count { it.type == type }
        if (total == 0) return null
        return SpecialShotStats(
            type = type,
            total = total,
            successPct = percentage(tally.count { it.type == type && it.outcome.isSuccess }, total)!!,
        )
    }
}

/** A serve that went in (an ace included). */
internal val ShotOutcome.isServeIn: Boolean get() = this == ShotOutcome.IN || this == ShotOutcome.ACE

/** A successful stroke (functional spec §9: `in` + `winner`). */
internal val ShotOutcome.isSuccess: Boolean get() = this == ShotOutcome.IN || this == ShotOutcome.WINNER
