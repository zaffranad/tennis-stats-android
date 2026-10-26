package fr.zaffran.tennisstats.ui.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.model.MatchStatus
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType
import fr.zaffran.tennisstats.testing.FakeMatchRepository
import fr.zaffran.tennisstats.testing.MainDispatcherRule
import fr.zaffran.tennisstats.testing.shot
import fr.zaffran.tennisstats.testing.testMatch
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeMatchRepository()

    @Test
    fun initialState_isLoading() {
        assertThat(HomeViewModel(repository).uiState.value).isEqualTo(HomeUiState(isLoading = true))
        assertThat(HomeUiState().canCreateMatch).isFalse()
    }

    @Test
    fun noMatch_showsEmptyHistoryAndAllowsCreation() = runTest {
        HomeViewModel(repository).uiState.test {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.activeMatch).isNull()
            assertThat(state.completedMatches).isEmpty()
            assertThat(state.canCreateMatch).isTrue()
        }
    }

    @Test
    fun activeMatch_isShownWithShotCount_andHidesCreation() = runTest {
        repository.seedMatch(testMatch(id = "m1"))
        repository.seedShots(shot(ShotType.FOREHAND, ShotOutcome.IN, matchId = "m1"))

        HomeViewModel(repository).uiState.test {
            val state = awaitItem()
            assertThat(state.activeMatch!!.match.id).isEqualTo("m1")
            assertThat(state.activeMatch.shotCount).isEqualTo(1)
            assertThat(state.canCreateMatch).isFalse()
        }
    }

    @Test
    fun completedMatches_areAllListedMostRecentFirst() = runTest {
        val base = Instant.parse("2026-10-01T10:00:00Z")
        (1..7).forEach { i ->
            repository.seedMatch(
                testMatch(
                    id = "m$i",
                    status = MatchStatus.COMPLETED,
                    result = if (i % 2 == 0) MatchResult.WIN else MatchResult.LOSS,
                    playedAt = base.plusSeconds(i * 3600L),
                ),
            )
        }

        HomeViewModel(repository).uiState.test {
            val state = awaitItem()
            assertThat(state.completedMatches.map { it.match.id })
                .containsExactly("m7", "m6", "m5", "m4", "m3", "m2", "m1")
                .inOrder()
        }
    }

    @Test
    fun stateUpdatesWhenMatchIsCreated() = runTest {
        HomeViewModel(repository).uiState.test {
            assertThat(awaitItem().activeMatch).isNull()

            repository.createMatch("Paul Martin", null)

            assertThat(awaitItem().activeMatch!!.match.opponentName).isEqualTo("Paul Martin")
        }
    }
}
