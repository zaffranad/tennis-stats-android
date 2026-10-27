package fr.zaffran.tennisstats.domain.stats

import com.google.common.truth.Truth.assertThat
import fr.zaffran.tennisstats.domain.model.Shot
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

class MetricCalculatorTest {

    private fun tally(vararg shots: Shot) = ShotTally.of(shots.toList())

    private fun firstServes(made: Int, total: Int) =
        tally(*(List(made) { firstServe(IN) } + List(total - made) { firstServe(FAULT) }).toTypedArray())

    /** [count] double faults in a match where serves were recorded. */
    private fun doubleFaults(count: Int) = tally(firstServe(IN), *List(count) { secondServe(NET) }.toTypedArray())

    @Test
    fun valueOf_matchesTheMatchStatistics() {
        val t = tally(
            firstServe(FAULT),
            secondServe(IN),
            firstServe(ACE),
            firstServe(NET),
            secondServe(NET),
            shot(ShotType.FOREHAND, WINNER),
            shot(ShotType.FOREHAND, NET),
            shot(ShotType.VOLLEY, WINNER),
        )

        assertThat(MetricCalculator.valueOf(StatMetric.FIRST_SERVE, t)).isEqualTo(MetricValue(1, 3))
        assertThat(MetricCalculator.valueOf(StatMetric.FIRST_SERVE, t)!!.value).isEqualTo(33)
        assertThat(MetricCalculator.valueOf(StatMetric.SECOND_SERVE, t)).isEqualTo(MetricValue(1, 2))
        assertThat(MetricCalculator.valueOf(StatMetric.FOREHAND, t)).isEqualTo(MetricValue(1, 2))
        assertThat(MetricCalculator.valueOf(StatMetric.ACES, t)).isEqualTo(MetricValue(1, null))
        assertThat(MetricCalculator.valueOf(StatMetric.DOUBLE_FAULTS, t)).isEqualTo(MetricValue(1, null))
        assertThat(MetricCalculator.valueOf(StatMetric.WINNERS, t)).isEqualTo(MetricValue(2, null))
    }

    /** Statistics spec §7, example 3. */
    @Test
    fun valueOf_isNullWithoutShotOfTheFamily() {
        val t = tally(shot(ShotType.FOREHAND, IN))

        assertThat(MetricCalculator.valueOf(StatMetric.BACKHAND, t)).isNull()
        assertThat(MetricCalculator.valueOf(StatMetric.FIRST_SERVE, t)).isNull()
        assertThat(MetricCalculator.valueOf(StatMetric.ACES, t)).isNull()
        assertThat(MetricCalculator.valueOf(StatMetric.DOUBLE_FAULTS, t)).isNull()
        assertThat(MetricCalculator.valueOf(StatMetric.WINNERS, t)).isEqualTo(MetricValue(0, null))
        assertThat(MetricCalculator.valueOf(StatMetric.WINNERS, firstServes(1, 1))).isNull()
    }

    /** Statistics spec §7, example 1: pooled, not the mean of the percentages. */
    @Test
    fun aggregate_rateIsPooled() {
        assertThat(MetricCalculator.aggregate(StatMetric.FIRST_SERVE, listOf(firstServes(1, 2), firstServes(9, 10))))
            .isEqualTo(83.0)
    }

    /** Statistics spec §7, example 2: matches without serves are not counted. */
    @Test
    fun aggregate_countIsMeanPerMatchWhereDefined() {
        val noServe = tally(shot(ShotType.FOREHAND, IN))

        assertThat(
            MetricCalculator.aggregate(StatMetric.DOUBLE_FAULTS, listOf(doubleFaults(2), doubleFaults(4), noServe)),
        ).isEqualTo(3.0)
        assertThat(MetricCalculator.aggregate(StatMetric.DOUBLE_FAULTS, listOf(doubleFaults(1), doubleFaults(0))))
            .isEqualTo(0.5)
        assertThat(MetricCalculator.aggregate(StatMetric.DOUBLE_FAULTS, listOf(noServe))).isNull()
        assertThat(MetricCalculator.aggregate(StatMetric.ACES, emptyList())).isNull()
    }

    @Test
    fun aggregate_countIsRoundedToOneDecimal() {
        assertThat(
            MetricCalculator.aggregate(
                StatMetric.DOUBLE_FAULTS,
                listOf(doubleFaults(1), doubleFaults(0), doubleFaults(0)),
            ),
        ).isEqualTo(0.3)
    }

    /** Statistics spec §7, example 6. */
    @Test
    fun compare_givesTheGapWithTheOtherMatches() {
        fun secondServeFaults(count: Int) = tally(*List(count) { secondServe(NET) }.toTypedArray())
        val current = firstServes(7, 10) + secondServeFaults(4)
        val others = listOf(firstServes(6, 10) + secondServeFaults(2), firstServes(12, 20) + secondServeFaults(2))

        val comparison = MetricCalculator.compare(current, others).associateBy { it.metric }

        assertThat(comparison[StatMetric.FIRST_SERVE]).isEqualTo(
            MetricComparison(StatMetric.FIRST_SERVE, MetricValue(7, 10), reference = 60.0, delta = 10.0),
        )
        assertThat(comparison[StatMetric.DOUBLE_FAULTS]!!.delta).isEqualTo(2.0)
        assertThat(MetricCalculator.isImprovement(StatMetric.FIRST_SERVE, 10.0)).isTrue()
        assertThat(MetricCalculator.isImprovement(StatMetric.DOUBLE_FAULTS, 2.0)).isFalse()
        // No forehand anywhere: no line.
        assertThat(comparison).doesNotContainKey(StatMetric.FOREHAND)
    }

    @Test
    fun compare_withoutOtherMatches_isEmpty() {
        assertThat(MetricCalculator.compare(firstServes(1, 2), emptyList())).isEmpty()
    }

    @Test
    fun pooledTallies_giveThePooledMatchStatistics() {
        val pooled = listOf(firstServes(1, 2), firstServes(9, 10)).sum()

        assertThat(StatsCalculator.computeStats(pooled).serve!!.firstServePct).isEqualTo(83)
        assertThat(pooled.total).isEqualTo(12)
    }
}
