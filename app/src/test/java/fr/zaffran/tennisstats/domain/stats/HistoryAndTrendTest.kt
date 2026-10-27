package fr.zaffran.tennisstats.domain.stats

import com.google.common.truth.Truth.assertThat
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.model.MatchStatus
import fr.zaffran.tennisstats.domain.model.Shot
import fr.zaffran.tennisstats.domain.model.ShotOutcome.FAULT
import fr.zaffran.tennisstats.domain.model.ShotOutcome.IN
import fr.zaffran.tennisstats.domain.model.ShotOutcome.NET
import fr.zaffran.tennisstats.domain.model.ShotType
import fr.zaffran.tennisstats.testing.firstServe
import fr.zaffran.tennisstats.testing.secondServe
import fr.zaffran.tennisstats.testing.shot
import fr.zaffran.tennisstats.testing.testMatch
import java.time.Instant
import org.junit.Test

class HistoryAndTrendTest {

    private val base = Instant.parse("2026-10-01T10:00:00Z")

    /** Completed match n° [index] (played later when [index] is higher). */
    private fun completed(index: Int, result: MatchResult, shots: List<Shot>) = MatchTally(
        testMatch(
            id = "m$index",
            status = MatchStatus.COMPLETED,
            result = result,
            playedAt = base.plusSeconds(index * 86_400L),
        ),
        ShotTally.of(shots),
    )

    /** [made] first serves in out of 10. */
    private fun serves(made: Int) = List(made) { firstServe(IN) } + List(10 - made) { firstServe(FAULT) }

    /** Matches whose first-serve rates are [rates] (in %), oldest first; returned newest first. */
    private fun matchesWithRates(vararg rates: Int) =
        rates.mapIndexed { i, rate -> completed(i, MatchResult.WIN, serves(rate / 10)) }.reversed()

    @Test
    fun history_summaryAndPooledStats() {
        val matches = listOf(
            completed(2, MatchResult.LOSS, serves(5) + secondServe(NET)),
            completed(1, MatchResult.WIN, serves(9) + shot(ShotType.FOREHAND, IN)),
        )

        val history = HistoryCalculator.computeHistory(matches)!!

        assertThat(history.matchCount).isEqualTo(2)
        assertThat(history.wins).isEqualTo(1)
        assertThat(history.losses).isEqualTo(1)
        assertThat(history.winPct).isEqualTo(50)
        assertThat(history.totalShots).isEqualTo(22)
        assertThat(history.shotsPerMatch).isEqualTo(11)
        assertThat(history.pooled.serve!!.firstServePct).isEqualTo(70)
        assertThat(history.doubleFaultsPerMatch).isEqualTo(0.5)
        assertThat(history.acesPerMatch).isEqualTo(0.0)
        val split = history.resultSplit!!.associateBy { it.metric }
        assertThat(split[StatMetric.FIRST_SERVE]).isEqualTo(ResultSplitRow(StatMetric.FIRST_SERVE, 90.0, 50.0))
        assertThat(split[StatMetric.FOREHAND]).isEqualTo(ResultSplitRow(StatMetric.FOREHAND, 100.0, null))
        assertThat(split).doesNotContainKey(StatMetric.BACKHAND)
    }

    @Test
    fun history_withoutLossOrWithoutMatch() {
        assertThat(HistoryCalculator.computeHistory(matchesWithRates(50, 60))!!.resultSplit).isNull()
        assertThat(HistoryCalculator.computeHistory(emptyList())).isNull()
    }

    /** Statistics spec §7, example 7. */
    @Test
    fun period_keepsTheMostRecentMatches() {
        val newestFirst = (12 downTo 1).toList()

        assertThat(StatsPeriod.LAST_5.select(newestFirst)).containsExactly(12, 11, 10, 9, 8).inOrder()
        assertThat(StatsPeriod.LAST_10.select(newestFirst)).hasSize(10)
        assertThat(StatsPeriod.ALL.select(newestFirst)).hasSize(12)
    }

    /** Statistics spec §7, example 4. */
    @Test
    fun trend_isChronological_withChangeOfTheLastThreeMatches() {
        val trend = TrendCalculator.computeTrend(StatMetric.FIRST_SERVE, matchesWithRates(50, 60, 70, 80, 90))

        assertThat(trend.points.map { it.value.value }).containsExactly(50, 60, 70, 80, 90).inOrder()
        assertThat(trend.change).isEqualTo(25.0)
        assertThat(trend.average).isEqualTo(70.0)
        assertThat(trend.last!!.value.value).isEqualTo(90)
        assertThat(trend.best!!.value.value).isEqualTo(90)
        assertThat(trend.canBeDrawn).isTrue()
    }

    @Test
    fun trend_changeNeedsFourPoints_andCurveTwo() {
        val three = TrendCalculator.computeTrend(StatMetric.FIRST_SERVE, matchesWithRates(50, 60, 70))
        val one = TrendCalculator.computeTrend(StatMetric.FIRST_SERVE, matchesWithRates(50))

        assertThat(three.change).isNull()
        assertThat(three.canBeDrawn).isTrue()
        assertThat(one.canBeDrawn).isFalse()
    }

    /** Statistics spec §7, examples 3 and 5. */
    @Test
    fun trend_skipsUndefinedMatches_andLowerIsBetterForDoubleFaults() {
        fun doubleFaults(n: Int) = List(n) { secondServe(FAULT) } + firstServe(IN)
        val matches = listOf(
            completed(4, MatchResult.WIN, listOf(shot(ShotType.FOREHAND, IN))),
            completed(3, MatchResult.LOSS, doubleFaults(2)),
            completed(2, MatchResult.WIN, doubleFaults(1)),
            completed(1, MatchResult.LOSS, doubleFaults(3)),
        )

        val trend = TrendCalculator.computeTrend(StatMetric.DOUBLE_FAULTS, matches)

        assertThat(trend.points.map { it.match.id }).containsExactly("m1", "m2", "m3").inOrder()
        assertThat(trend.best!!.match.id).isEqualTo("m2")
        assertThat(trend.average).isEqualTo(2.0)
    }
}
