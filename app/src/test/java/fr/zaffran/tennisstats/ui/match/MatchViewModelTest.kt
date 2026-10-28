package fr.zaffran.tennisstats.ui.match

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import fr.zaffran.tennisstats.domain.input.Column
import fr.zaffran.tennisstats.domain.input.InputAction.StartMiss
import fr.zaffran.tennisstats.domain.input.InputAction.TapOutcome
import fr.zaffran.tennisstats.domain.input.InputAction.ToggleModifier
import fr.zaffran.tennisstats.domain.input.InputAction.ToggleServe
import fr.zaffran.tennisstats.domain.input.ShotInputState
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.model.MatchStatus
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType
import fr.zaffran.tennisstats.navigation.Match as MatchRoute
import fr.zaffran.tennisstats.navigation.MatchTab
import fr.zaffran.tennisstats.testing.FakeMatchRepository
import fr.zaffran.tennisstats.testing.MainDispatcherRule
import fr.zaffran.tennisstats.testing.shot
import fr.zaffran.tennisstats.testing.testMatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MatchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeMatchRepository()

    /** Creates the ViewModel and keeps its state collected for the duration of the test. */
    private fun TestScope.viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        tab: MatchTab = MatchTab.INPUT,
    ): MatchViewModel = MatchViewModel(repository, handle, MatchRoute("m1", tab))
        .also { vm -> backgroundScope.launch { vm.uiState.collect {} } }

    private fun runMatchTest(block: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) {
        block()
    }

    @Test
    fun opensOnRouteTab_withMatchAndShots() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1", status = MatchStatus.COMPLETED, result = MatchResult.WIN))
        repository.seedShots(shot(ShotType.FOREHAND, ShotOutcome.WINNER, matchId = "m1"))

        val state = viewModel(tab = MatchTab.STATS).uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.selectedTab).isEqualTo(MatchTab.STATS)
        assertThat(state.isInProgress).isFalse()
        assertThat(state.shotCount).isEqualTo(1)
        assertThat(state.stats.groundstrokes!!.forehand!!.winnerPct).isEqualTo(100)
    }

    @Test
    fun serveNumberIsDerivedOnOpen() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        repository.seedShots(
            shot(ShotType.SERVE, ShotOutcome.FAULT, matchId = "m1"),
            shot(ShotType.BACKHAND, ShotOutcome.IN, matchId = "m1"),
        )

        assertThat(viewModel().uiState.value.input).isEqualTo(ShotInputState(activeMode = null, serveNumber = 2))
    }

    @Test
    fun forehandShot_isRecordedWithFeedback_andStateUnchanged() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        val vm = viewModel()

        vm.onInputAction(TapOutcome(Column.RIGHT, ShotOutcome.WINNER))

        val state = vm.uiState.value
        assertThat(repository.shotsOf("m1").map { it.type to it.outcome })
            .containsExactly(ShotType.FOREHAND to ShotOutcome.WINNER)
        assertThat(state.shotCount).isEqualTo(1)
        assertThat(state.recentShots.single().outcome).isEqualTo(ShotOutcome.WINNER)
        assertThat(state.feedback!!.shot.type).isEqualTo(ShotType.FOREHAND)
        assertThat(state.input).isEqualTo(ShotInputState())
        assertThat(state.isRecording).isFalse()
    }

    @Test
    fun feedbackDisappearsAfterTwoSeconds() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        val vm = viewModel()

        vm.onInputAction(TapOutcome(Column.LEFT, ShotOutcome.IN))
        advanceTimeBy(MatchViewModel.FEEDBACK_DURATION_MILLIS - 1)
        runCurrent()
        assertThat(vm.uiState.value.feedback).isNotNull()

        advanceTimeBy(2)
        runCurrent()
        assertThat(vm.uiState.value.feedback).isNull()
    }

    @Test
    fun newShotReplacesFeedbackAndRestartsTimer() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        val vm = viewModel()

        vm.onInputAction(TapOutcome(Column.LEFT, ShotOutcome.IN))
        advanceTimeBy(1_500)
        vm.onInputAction(TapOutcome(Column.RIGHT, ShotOutcome.NET))
        advanceTimeBy(1_000)
        runCurrent()

        val feedback = vm.uiState.value.feedback!!
        assertThat(feedback.shot.type).isEqualTo(ShotType.FOREHAND)
        assertThat(feedback.shot.outcome).isEqualTo(ShotOutcome.NET)
    }

    @Test
    fun doubleFaultSequence() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        val vm = viewModel()

        vm.onInputAction(ToggleServe(1))
        vm.onInputAction(TapOutcome(Column.RIGHT, ShotOutcome.FAULT))
        assertThat(vm.uiState.value.input).isEqualTo(ShotInputState(ShotType.SERVE, serveNumber = 2))

        vm.onInputAction(TapOutcome(Column.RIGHT, ShotOutcome.NET))

        val state = vm.uiState.value
        assertThat(state.input).isEqualTo(ShotInputState())
        assertThat(state.feedback!!.shot.isDoubleFault).isTrue()
        assertThat(state.stats.serve!!.doubleFaults).isEqualTo(1)
    }

    @Test
    fun tapsWhileRecordingAreIgnored() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        val vm = viewModel()
        val gate = CompletableDeferred<Unit>()
        repository.writeGate = gate

        vm.onInputAction(TapOutcome(Column.RIGHT, ShotOutcome.IN))
        assertThat(vm.uiState.value.isRecording).isTrue()
        vm.onInputAction(TapOutcome(Column.LEFT, ShotOutcome.IN))
        vm.onInputAction(ToggleServe(1))
        gate.complete(Unit)

        assertThat(repository.shotsOf("m1")).hasSize(1)
        assertThat(vm.uiState.value.input).isEqualTo(ShotInputState())
        assertThat(vm.uiState.value.isRecording).isFalse()
    }

    @Test
    fun storageFailure_showsSnackbarAndKeepsState() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        val vm = viewModel()
        vm.onInputAction(ToggleServe(1))
        repository.failWrites = true

        vm.events.test {
            vm.onInputAction(TapOutcome(Column.RIGHT, ShotOutcome.FAULT))

            assertThat(awaitItem()).isEqualTo(MatchEvent.ShowMessage(MatchMessage.SHOT_NOT_SAVED))
        }
        assertThat(vm.uiState.value.input).isEqualTo(ShotInputState(ShotType.SERVE, serveNumber = 1))
        assertThat(vm.uiState.value.isRecording).isFalse()
        assertThat(vm.uiState.value.feedback).isNull()
    }

    @Test
    fun undo_deletesLastShotAndRederivesServeNumber_keepingMode() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        val vm = viewModel()
        vm.onInputAction(ToggleServe(1))
        vm.onInputAction(TapOutcome(Column.RIGHT, ShotOutcome.FAULT))
        vm.onInputAction(TapOutcome(Column.RIGHT, ShotOutcome.IN))
        vm.onInputAction(ToggleModifier(ShotType.LOB))

        // Last shot = 2nd serve in: undo makes the 1st serve fault the last serve → 2nd serve.
        vm.onUndo()

        val state = vm.uiState.value
        assertThat(state.shotCount).isEqualTo(1)
        assertThat(state.input).isEqualTo(ShotInputState(activeMode = ShotType.LOB, serveNumber = 2))

        vm.onUndo()
        assertThat(vm.uiState.value.shotCount).isEqualTo(0)
        assertThat(vm.uiState.value.input).isEqualTo(ShotInputState(activeMode = ShotType.LOB, serveNumber = 1))
    }

    @Test
    fun undo_announcesTheDeletedShot() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        val vm = viewModel()
        vm.onInputAction(TapOutcome(Column.LEFT, ShotOutcome.NET))

        vm.onUndo()

        val feedback = vm.uiState.value.feedback!!
        assertThat(feedback.isUndone).isTrue()
        assertThat(feedback.shot.type to feedback.shot.outcome).isEqualTo(ShotType.BACKHAND to ShotOutcome.NET)
    }

    @Test
    fun undo_isIgnoredWhileAShotIsBeingWritten() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        repository.seedShots(shot(ShotType.BACKHAND, ShotOutcome.IN, matchId = "m1"))
        val vm = viewModel()
        val gate = CompletableDeferred<Unit>()
        repository.writeGate = gate

        vm.onInputAction(TapOutcome(Column.RIGHT, ShotOutcome.IN))
        assertThat(vm.uiState.value.canDeleteShots).isFalse()
        vm.onUndo()
        gate.complete(Unit)

        assertThat(repository.shotsOf("m1").map { it.type }).containsExactly(ShotType.FOREHAND, ShotType.BACKHAND)
    }

    @Test
    fun recentShots_areTheFiveMostRecent() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        val seeded = List(7) { shot(ShotType.FOREHAND, ShotOutcome.IN, matchId = "m1") }
        repository.seedShots(*seeded.toTypedArray())

        val state = viewModel().uiState.value

        assertThat(state.shotCount).isEqualTo(7)
        assertThat(state.recentShots).containsExactlyElementsIn(seeded.takeLast(5).reversed()).inOrder()
    }

    @Test
    fun deleteShot_removesThatShotAndRederivesServeNumber() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        val fault = shot(ShotType.SERVE, ShotOutcome.FAULT, matchId = "m1")
        val backhand = shot(ShotType.BACKHAND, ShotOutcome.IN, matchId = "m1")
        repository.seedShots(fault, backhand)
        val vm = viewModel()
        assertThat(vm.uiState.value.input.serveNumber).isEqualTo(2)

        // Deleting an older shot: the 1st serve fault is no longer the last serve → 1st serve.
        vm.onDeleteShot(fault.id)

        val state = vm.uiState.value
        assertThat(state.recentShots).containsExactly(backhand)
        assertThat(state.input.serveNumber).isEqualTo(1)
        assertThat(state.feedback!!.shot).isEqualTo(fault)
        assertThat(state.feedback.isUndone).isTrue()
    }

    @Test
    fun deleteShot_onCompletedMatch_isRefused() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1", status = MatchStatus.COMPLETED, result = MatchResult.WIN))
        val forehand = shot(ShotType.FOREHAND, ShotOutcome.IN, matchId = "m1")
        repository.seedShots(forehand)
        val vm = viewModel()

        vm.events.test {
            vm.onDeleteShot(forehand.id)
            assertThat(awaitItem()).isEqualTo(MatchEvent.ShowMessage(MatchMessage.UNEXPECTED))
        }
        assertThat(repository.shotsOf("m1")).containsExactly(forehand)
    }

    @Test
    fun miss_isRecordedOnlyOnceQualified_andUndoCancelsIt() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        val vm = viewModel()

        vm.onInputAction(StartMiss(Column.RIGHT))
        assertThat(vm.uiState.value.input.pendingMiss).isEqualTo(Column.RIGHT)
        assertThat(vm.uiState.value.canUndo).isTrue()
        assertThat(repository.shotsOf("m1")).isEmpty()

        vm.onUndo()
        assertThat(vm.uiState.value.input.pendingMiss).isNull()
        assertThat(repository.shotsOf("m1")).isEmpty()

        vm.onInputAction(StartMiss(Column.RIGHT))
        vm.onInputAction(TapOutcome(Column.RIGHT, ShotOutcome.NET))
        assertThat(repository.shotsOf("m1").map { it.type to it.outcome })
            .containsExactly(ShotType.FOREHAND to ShotOutcome.NET)
        assertThat(vm.uiState.value.input.pendingMiss).isNull()
    }

    @Test
    fun pendingMiss_survivesProcessDeath() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        val handle = SavedStateHandle()
        viewModel(handle).onInputAction(StartMiss(Column.LEFT))

        assertThat(viewModel(handle).uiState.value.input.pendingMiss).isEqualTo(Column.LEFT)
    }

    @Test
    fun winnerChip_qualifiesLastSuccessAndReverts() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        val vm = viewModel()
        vm.onInputAction(TapOutcome(Column.LEFT, ShotOutcome.IN))
        assertThat(vm.uiState.value.qualifiableShot!!.outcome).isEqualTo(ShotOutcome.IN)

        vm.onToggleWinner()
        assertThat(repository.shotsOf("m1").single().outcome).isEqualTo(ShotOutcome.WINNER)
        assertThat(vm.uiState.value.qualifiableShot!!.isWinner).isTrue()
        assertThat(vm.uiState.value.feedback!!.shot.outcome).isEqualTo(ShotOutcome.WINNER)

        vm.onToggleWinner()
        assertThat(repository.shotsOf("m1").single().outcome).isEqualTo(ShotOutcome.IN)
    }

    @Test
    fun winnerChip_onServe_qualifiesAsAce() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        val vm = viewModel()
        vm.onInputAction(ToggleServe(1))
        vm.onInputAction(TapOutcome(Column.RIGHT, ShotOutcome.IN))

        vm.onToggleWinner()

        assertThat(repository.shotsOf("m1").single().outcome).isEqualTo(ShotOutcome.ACE)
    }

    @Test
    fun winnerChip_isOfferedOnlyAfterASuccessAndUntilTheNextShot() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        val vm = viewModel()
        assertThat(vm.uiState.value.qualifiableShot).isNull()

        vm.onInputAction(TapOutcome(Column.RIGHT, ShotOutcome.IN))
        assertThat(vm.uiState.value.qualifiableShot).isNotNull()

        // Hidden while a miss is pending, and gone once the miss is recorded.
        vm.onInputAction(StartMiss(Column.LEFT))
        assertThat(vm.uiState.value.qualifiableShot).isNull()
        vm.onInputAction(TapOutcome(Column.LEFT, ShotOutcome.FAULT))
        assertThat(vm.uiState.value.qualifiableShot).isNull()

        vm.onToggleWinner()
        assertThat(repository.shotsOf("m1").map { it.outcome }).containsExactly(ShotOutcome.FAULT, ShotOutcome.IN)
    }

    @Test
    fun undo_onCompletedMatch_isRefused() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1", status = MatchStatus.COMPLETED, result = MatchResult.LOSS))
        repository.seedShots(shot(ShotType.FOREHAND, ShotOutcome.IN, matchId = "m1"))
        val vm = viewModel()

        vm.events.test {
            vm.onUndo()
            assertThat(awaitItem()).isEqualTo(MatchEvent.ShowMessage(MatchMessage.UNEXPECTED))
        }
        assertThat(repository.shotsOf("m1")).hasSize(1)
    }

    @Test
    fun finish_completesMatchAndShowsStats() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        val vm = viewModel()
        vm.onFinishClick()
        assertThat(vm.uiState.value.isFinishSheetVisible).isTrue()

        vm.onFinish(MatchResult.WIN)

        val state = vm.uiState.value
        assertThat(state.match!!.status).isEqualTo(MatchStatus.COMPLETED)
        assertThat(state.match.result).isEqualTo(MatchResult.WIN)
        assertThat(state.isFinishSheetVisible).isFalse()
        assertThat(state.selectedTab).isEqualTo(MatchTab.STATS)
        assertThat(state.isInProgress).isFalse()
    }

    @Test
    fun deleteConfirm_removesMatch_andMarksItMissing() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        repository.seedShots(shot(ShotType.FOREHAND, ShotOutcome.IN, matchId = "m1"))
        val vm = viewModel()
        vm.onFinishClick()
        vm.onDeleteClick()
        assertThat(vm.uiState.value.isDeleteDialogVisible).isTrue()

        vm.onDeleteConfirm()

        assertThat(repository.matchById("m1")).isNull()
        assertThat(repository.shotsOf("m1")).isEmpty()
        assertThat(vm.uiState.value.isMissing).isTrue()
        assertThat(vm.uiState.value.isDeleteDialogVisible).isFalse()
    }

    @Test
    fun deleteDismiss_keepsMatch() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1", status = MatchStatus.COMPLETED, result = MatchResult.WIN))
        val vm = viewModel()
        vm.onDeleteClick()

        vm.onDeleteDismiss()

        assertThat(vm.uiState.value.isDeleteDialogVisible).isFalse()
        assertThat(repository.matchById("m1")).isNotNull()
    }

    @Test
    fun inputStateAndTabSurviveProcessDeath() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1"))
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.onInputAction(ToggleModifier(ShotType.SMASH))
        first.onTabSelected(MatchTab.STATS)

        val restored = viewModel(handle).uiState.value

        assertThat(restored.input).isEqualTo(ShotInputState(activeMode = ShotType.SMASH))
        assertThat(restored.selectedTab).isEqualTo(MatchTab.STATS)
    }

    @Test
    fun shotOnCompletedMatch_isRefused() = runMatchTest {
        repository.seedMatch(testMatch(id = "m1", status = MatchStatus.COMPLETED, result = MatchResult.WIN))
        val vm = viewModel()

        vm.events.test {
            vm.onInputAction(TapOutcome(Column.LEFT, ShotOutcome.IN))
            assertThat(awaitItem()).isEqualTo(MatchEvent.ShowMessage(MatchMessage.SHOT_NOT_SAVED))
        }
        assertThat(repository.shotsOf("m1")).isEmpty()
    }
}
