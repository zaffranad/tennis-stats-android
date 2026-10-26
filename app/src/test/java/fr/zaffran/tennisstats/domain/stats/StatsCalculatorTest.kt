package fr.zaffran.tennisstats.domain.stats

import com.google.common.truth.Truth.assertThat
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotOutcome.ACE
import fr.zaffran.tennisstats.domain.model.ShotOutcome.FAULT
import fr.zaffran.tennisstats.domain.model.ShotOutcome.IN
import fr.zaffran.tennisstats.domain.model.ShotOutcome.NET
import fr.zaffran.tennisstats.domain.model.ShotOutcome.WINNER
import fr.zaffran.tennisstats.domain.model.ShotType
import fr.zaffran.tennisstats.testing.firstServe
import fr.zaffran.tennisstats.testing.secondServe
import fr.zaffran.tennisstats.testing.shot
import org.junit.Test

class StatsCalculatorTest {

    private fun shots(type: ShotType, vararg outcomes: ShotOutcome) = outcomes.map { shot(type, it) }

    /** Functional spec §9.5, example 1. */
    @Test
    fun serveStats() {
        val stats = StatsCalculator.computeStats(
            listOf(firstServe(FAULT), secondServe(IN), firstServe(ACE), firstServe(NET), secondServe(NET)),
        )

        assertThat(stats.serve).isEqualTo(
            ServeStats(
                firstServesIn = 1,
                firstServes = 3,
                firstServePct = 33,
                secondServesIn = 1,
                secondServes = 2,
                secondServePct = 50,
                aces = 1,
                doubleFaults = 1,
            ),
        )
        assertThat(stats.groundstrokes).isNull()
        assertThat(stats.specialShots).isNull()
    }

    /** Functional spec §9.5, example 2. */
    @Test
    fun forehandStats() {
        val stats = StatsCalculator.computeStats(shots(ShotType.FOREHAND, IN, IN, WINNER, NET, FAULT, FAULT))

        val forehand = stats.groundstrokes!!.forehand!!
        assertThat(forehand.total).isEqualTo(6)
        assertThat(forehand.successPct).isEqualTo(50)
        assertThat(forehand.winnerPct).isEqualTo(33)
        assertThat(forehand.netPct).isEqualTo(17)
        assertThat(forehand.outPct).isEqualTo(33)
        assertThat(stats.groundstrokes.backhand).isNull()
        assertThat(stats.serve).isNull()
    }

    /** Functional spec §9.5, example 3. */
    @Test
    fun backhandStatsWithoutSuccess() {
        val stats = StatsCalculator.computeStats(shots(ShotType.BACKHAND, NET, FAULT))

        assertThat(stats.groundstrokes!!.backhand).isEqualTo(
            StrokeStats(
                total = 2,
                successful = 0,
                winners = 0,
                net = 1,
                out = 1,
                successPct = 0,
                winnerPct = null,
                netPct = 50,
                outPct = 50,
            ),
        )
        assertThat(stats.groundstrokes.forehand).isNull()
    }

    /** Functional spec §9.5, example 4. */
    @Test
    fun specialShotStats() {
        val stats = StatsCalculator.computeStats(
            listOf(shot(ShotType.VOLLEY, IN), shot(ShotType.VOLLEY, FAULT), shot(ShotType.SMASH, WINNER)),
        )

        assertThat(stats.specialShots).containsExactly(
            SpecialShotStats(ShotType.VOLLEY, total = 2, successPct = 50),
            SpecialShotStats(ShotType.SMASH, total = 1, successPct = 100),
        ).inOrder()
        assertThat(stats.groundstrokes).isNull()
    }

    @Test
    fun specialShotsInDisplayOrderRegardlessOfInputOrder() {
        val stats = StatsCalculator.computeStats(
            listOf(shot(ShotType.DROP_SHOT, IN), shot(ShotType.LOB, IN), shot(ShotType.VOLLEY, IN)),
        )

        assertThat(stats.specialShots!!.map { it.type })
            .containsExactly(ShotType.VOLLEY, ShotType.LOB, ShotType.DROP_SHOT)
            .inOrder()
    }

    /** Functional spec §9.5, example 5. */
    @Test
    fun roundingIsHalfUp() {
        assertThat(StatsCalculator.percentage(1, 8)).isEqualTo(13)
        assertThat(StatsCalculator.percentage(2, 3)).isEqualTo(67)
        assertThat(StatsCalculator.percentage(1, 3)).isEqualTo(33)
        assertThat(StatsCalculator.percentage(1, 6)).isEqualTo(17)
        assertThat(StatsCalculator.percentage(0, 5)).isEqualTo(0)
        assertThat(StatsCalculator.percentage(5, 5)).isEqualTo(100)
        assertThat(StatsCalculator.percentage(0, 0)).isNull()
    }

    @Test
    fun emptyMatch_hasNoSection() {
        val stats = StatsCalculator.computeStats(emptyList())

        assertThat(stats).isEqualTo(MatchStats(totalShots = 0, serve = null, groundstrokes = null, specialShots = null))
        assertThat(stats.isEmpty).isTrue()
    }

    @Test
    fun onlySecondServes_firstServePctIsNull() {
        val serve = StatsCalculator.computeStats(listOf(secondServe(IN), secondServe(FAULT))).serve!!

        assertThat(serve.firstServes).isEqualTo(0)
        assertThat(serve.firstServePct).isNull()
        assertThat(serve.secondServePct).isEqualTo(50)
        assertThat(serve.doubleFaults).isEqualTo(1)
    }

    @Test
    fun onlyFirstServes_secondServePctIsNull() {
        val serve = StatsCalculator.computeStats(listOf(firstServe(IN), firstServe(ACE))).serve!!

        assertThat(serve.firstServePct).isEqualTo(100)
        assertThat(serve.secondServePct).isNull()
        assertThat(serve.aces).isEqualTo(1)
        assertThat(serve.doubleFaults).isEqualTo(0)
    }

    @Test
    fun aceOnSecondServeCountsAsAceAndIn() {
        val serve = StatsCalculator.computeStats(listOf(firstServe(NET), secondServe(ACE))).serve!!

        assertThat(serve.aces).isEqualTo(1)
        assertThat(serve.secondServesIn).isEqualTo(1)
        assertThat(serve.doubleFaults).isEqualTo(0)
    }

    @Test
    fun allSections_withMixedShots() {
        val stats = StatsCalculator.computeStats(
            listOf(
                firstServe(FAULT),
                secondServe(IN),
                shot(ShotType.FOREHAND, WINNER),
                shot(ShotType.BACKHAND, IN),
                shot(ShotType.LOB, NET),
            ),
        )

        assertThat(stats.totalShots).isEqualTo(5)
        assertThat(stats.serve).isNotNull()
        assertThat(stats.groundstrokes!!.forehand!!.winnerPct).isEqualTo(100)
        assertThat(stats.groundstrokes.backhand!!.successPct).isEqualTo(100)
        assertThat(stats.specialShots).containsExactly(SpecialShotStats(ShotType.LOB, total = 1, successPct = 0))
    }

    @Test
    fun unforcedErrorCountsInTotalOnly() {
        val forehand = StatsCalculator.computeStats(
            shots(ShotType.FOREHAND, IN, ShotOutcome.UNFORCED_ERROR),
        ).groundstrokes!!.forehand!!

        assertThat(forehand.total).isEqualTo(2)
        assertThat(forehand.successPct).isEqualTo(50)
        assertThat(forehand.netPct).isEqualTo(0)
        assertThat(forehand.outPct).isEqualTo(0)
    }
}
