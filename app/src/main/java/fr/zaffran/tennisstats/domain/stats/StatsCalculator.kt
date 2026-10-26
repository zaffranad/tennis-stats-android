package fr.zaffran.tennisstats.domain.stats

import fr.zaffran.tennisstats.domain.model.Shot
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType
import kotlin.math.roundToInt

/** Computes match statistics from its shots (functional spec §9). Pure functions. */
object StatsCalculator {

    fun computeStats(shots: List<Shot>): MatchStats {
        val byType = shots.groupBy { it.type }
        val forehand = byType[ShotType.FOREHAND]?.let(::strokeStats)
        val backhand = byType[ShotType.BACKHAND]?.let(::strokeStats)
        val specials = ShotType.SPECIAL_SHOTS.mapNotNull { type ->
            byType[type]?.let { specialShotStats(type, it) }
        }
        return MatchStats(
            totalShots = shots.size,
            serve = byType[ShotType.SERVE]?.let(::serveStats),
            groundstrokes = if (forehand != null || backhand != null) GroundstrokeStats(forehand, backhand) else null,
            specialShots = specials.ifEmpty { null },
        )
    }

    /** `round(part / total × 100)`, half up; null when [total] is 0. */
    fun percentage(part: Int, total: Int): Int? = if (total == 0) null else (part * 100.0 / total).roundToInt()

    private fun serveStats(serves: List<Shot>): ServeStats {
        val (second, first) = serves.partition { it.isSecondServe }
        val firstIn = first.count { it.outcome.isServeIn }
        val secondIn = second.count { it.outcome.isServeIn }
        return ServeStats(
            firstServesIn = firstIn,
            firstServes = first.size,
            firstServePct = percentage(firstIn, first.size),
            secondServesIn = secondIn,
            secondServes = second.size,
            secondServePct = percentage(secondIn, second.size),
            aces = serves.count { it.outcome == ShotOutcome.ACE },
            doubleFaults = second.count { it.outcome.isMiss },
        )
    }

    private fun strokeStats(shots: List<Shot>): StrokeStats {
        val total = shots.size
        val successful = shots.count { it.outcome.isSuccess }
        val winners = shots.count { it.outcome == ShotOutcome.WINNER }
        val net = shots.count { it.outcome == ShotOutcome.NET }
        val out = shots.count { it.outcome == ShotOutcome.FAULT }
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

    private fun specialShotStats(type: ShotType, shots: List<Shot>) = SpecialShotStats(
        type = type,
        total = shots.size,
        successPct = percentage(shots.count { it.outcome.isSuccess }, shots.size)!!,
    )

    private val ShotOutcome.isServeIn: Boolean get() = this == ShotOutcome.IN || this == ShotOutcome.ACE
    private val ShotOutcome.isSuccess: Boolean get() = this == ShotOutcome.IN || this == ShotOutcome.WINNER
}
