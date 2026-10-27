package fr.zaffran.tennisstats.ui.stats

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import fr.zaffran.tennisstats.domain.input.ShotToRecord
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.model.MatchStatus
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType
import fr.zaffran.tennisstats.domain.stats.StatMetric
import fr.zaffran.tennisstats.domain.stats.StatsPeriod
import fr.zaffran.tennisstats.testing.FakeMatchRepository
import fr.zaffran.tennisstats.testing.MainDispatcherRule
import fr.zaffran.tennisstats.testing.shot
import fr.zaffran.tennisstats.testing.testMatch
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class StatsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeMatchRepository()
    private val base = Instant.parse("2026-10-01T10:00:00Z")

    /** Completed match n° [index] with [firstServesIn] first serves in out of 10. */
    private fun seedCompleted(index: Int, firstServesIn: Int, result: MatchResult = MatchResult.WIN) {
        val id = "c$index"
        repository.seedMatch(
            testMatch(
                id = id,
                status = MatchStatus.COMPLETED,
                result = result,
                playedAt = base.plusSeconds(index * 3600L),
            ),
        )
        repository.seedShots(
            *List(10) { i ->
                shot(ShotType.SERVE, if (i < firstServesIn) ShotOutcome.IN else ShotOutcome.FAULT, matchId = id)
            }.toTypedArray(),
        )
    }

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        StatsViewModel(repository, savedStateHandle)

    @Test
    fun noMatch_isEmpty() = runTest {
        viewModel().uiState.test {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.focus).isNull()
            assertThat(state.history).isNull()
            assertThat(state.completedMatchCount).isEqualTo(0)
        }
    }

    /** Statistics spec §7, example 8: the in-progress match is shown, even without shots. */
    @Test
    fun focus_isTheActiveMatch_comparedWithAllCompletedMatches() = runTest {
        seedCompleted(1, firstServesIn = 6)
        repository.seedMatch(testMatch(id = "live", playedAt = base.plusSeconds(10 * 3600L)))

        viewModel().uiState.test {
            val state = awaitItem()
            assertThat(state.focus!!.match.id).isEqualTo("live")
            assertThat(state.focus.stats.isEmpty).isTrue()
            assertThat(state.focus.comparedMatchCount).isEqualTo(1)
            // The in-progress match is not part of the history.
            assertThat(state.history!!.matchCount).isEqualTo(1)

            repository.addShot("live", ShotToRecord(ShotType.SERVE, ShotOutcome.IN, isSecondServe = false))

            val live = awaitItem().focus!!
            assertThat(live.shotCount).isEqualTo(1)
            assertThat(live.comparison.single { it.metric == StatMetric.FIRST_SERVE }.delta).isEqualTo(40.0)
        }
    }

    @Test
    fun focus_withoutActiveMatch_isTheLastCompletedOne_excludedFromItsComparison() = runTest {
        seedCompleted(1, firstServesIn = 5)
        seedCompleted(2, firstServesIn = 8)

        viewModel().uiState.test {
            val focus = awaitItem().focus!!
            assertThat(focus.match.id).isEqualTo("c2")
            assertThat(focus.comparedMatchCount).isEqualTo(1)
            assertThat(focus.comparison.single { it.metric == StatMetric.FIRST_SERVE }.reference).isEqualTo(50.0)
        }
    }

    @Test
    fun period_and_metric_filterHistoryAndTrend() = runTest {
        (1..7).forEach {
            seedCompleted(
                it,
                firstServesIn = it,
                result = if (it % 2 ==
                    0
                ) {
                    MatchResult.WIN
                } else {
                    MatchResult.LOSS
                },
            )
        }
        val vm = viewModel()

        vm.uiState.test {
            val all = awaitItem()
            assertThat(all.periodMatchCount).isEqualTo(7)
            assertThat(all.trend!!.points.map { it.value.value }).containsExactly(10, 20, 30, 40, 50, 60, 70).inOrder()

            vm.onPeriodSelected(StatsPeriod.LAST_5)
            val last5 = awaitItem()
            assertThat(last5.periodMatchCount).isEqualTo(5)
            assertThat(last5.history!!.matchCount).isEqualTo(5)
            assertThat(last5.trend!!.points.first().value.value).isEqualTo(30)

            vm.onMetricSelected(StatMetric.BACKHAND)
            val backhand = awaitItem()
            assertThat(backhand.metric).isEqualTo(StatMetric.BACKHAND)
            assertThat(backhand.trend!!.points).isEmpty()
        }
    }

    @Test
    fun selection_isSavedAndRestored() = runTest {
        val handle = SavedStateHandle()
        viewModel(handle).apply {
            onTabSelected(StatsTab.EVOLUTION)
            onPeriodSelected(StatsPeriod.LAST_10)
            onMetricSelected(StatMetric.ACES)
        }

        viewModel(handle).uiState.test {
            val state = awaitItem()
            assertThat(state.selectedTab).isEqualTo(StatsTab.EVOLUTION)
            assertThat(state.period).isEqualTo(StatsPeriod.LAST_10)
            assertThat(state.metric).isEqualTo(StatMetric.ACES)
        }
    }
}
