package fr.zaffran.tennisstats.ui.newmatch

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import fr.zaffran.tennisstats.domain.model.MatchStatus
import fr.zaffran.tennisstats.testing.FakeMatchRepository
import fr.zaffran.tennisstats.testing.MainDispatcherRule
import fr.zaffran.tennisstats.testing.testMatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class NewMatchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeMatchRepository()
    private val savedStateHandle = SavedStateHandle()

    private fun viewModel() = NewMatchViewModel(repository, savedStateHandle)

    @Test
    fun start_withValidForm_createsMatchAndOpensIt() = runTest {
        val viewModel = viewModel()
        viewModel.onOpponentNameChange("  Jean Dupont ")
        viewModel.onOpponentLevelChange("15/4")

        viewModel.events.test {
            viewModel.onStart()

            val event = awaitItem() as NewMatchEvent.OpenMatch
            val created = repository.matchById(event.matchId)!!
            assertThat(created.opponentName).isEqualTo("Jean Dupont")
            assertThat(created.opponentLevel).isEqualTo("15/4")
            assertThat(created.status).isEqualTo(MatchStatus.IN_PROGRESS)
        }
    }

    @Test
    fun start_withBlankName_showsNameErrorWithoutCreating() = runTest {
        val viewModel = viewModel()
        viewModel.onOpponentNameChange("   ")

        viewModel.onStart()

        assertThat(viewModel.uiState.value.error).isEqualTo(NewMatchError.OPPONENT_NAME_REQUIRED)
        assertThat(viewModel.uiState.value.isCreating).isFalse()
        assertThat(repository.matchById("match-1")).isNull()
    }

    @Test
    fun typingClearsNameError() {
        val viewModel = viewModel()
        viewModel.onStart()

        viewModel.onOpponentNameChange("J")

        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun start_whileMatchAlreadyInProgress_showsError() = runTest {
        val viewModel = viewModel()
        // Another match is started after the form was opened (no redirect then).
        repository.seedMatch(testMatch(id = "other"))
        viewModel.onOpponentNameChange("Jean Dupont")

        viewModel.onStart()

        assertThat(viewModel.uiState.value.error).isEqualTo(NewMatchError.MATCH_ALREADY_IN_PROGRESS)
        assertThat(viewModel.uiState.value.isCreating).isFalse()
    }

    @Test
    fun opening_whileMatchInProgress_redirectsToIt() = runTest {
        repository.seedMatch(testMatch(id = "active"))

        viewModel().events.test {
            assertThat(awaitItem()).isEqualTo(NewMatchEvent.OpenMatch("active"))
        }
    }

    @Test
    fun start_showsCreatingStateAndIgnoresSecondTap() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.writeGate = gate
        val viewModel = viewModel()
        viewModel.onOpponentNameChange("Jean Dupont")

        viewModel.events.test {
            viewModel.onStart()
            assertThat(viewModel.uiState.value.isCreating).isTrue()
            viewModel.onStart()

            gate.complete(Unit)

            assertThat(awaitItem()).isInstanceOf(NewMatchEvent.OpenMatch::class.java)
            expectNoEvents()
        }
    }

    @Test
    fun start_withStorageFailure_showsUnexpectedError() = runTest {
        repository.failWrites = true
        val viewModel = viewModel()
        viewModel.onOpponentNameChange("Jean Dupont")

        viewModel.onStart()

        assertThat(viewModel.uiState.value.error).isEqualTo(NewMatchError.UNEXPECTED)
        assertThat(viewModel.uiState.value.isCreating).isFalse()
    }

    @Test
    fun formIsRestoredFromSavedState() {
        viewModel().apply {
            onOpponentNameChange("Jean")
            onOpponentLevelChange("nc")
        }

        val restored = viewModel().uiState.value

        assertThat(restored.opponentName).isEqualTo("Jean")
        assertThat(restored.opponentLevel).isEqualTo("nc")
    }

    @Test
    fun levelCanBeCleared() {
        val viewModel = viewModel()
        viewModel.onOpponentLevelChange("expert")

        viewModel.onOpponentLevelChange(null)

        assertThat(viewModel.uiState.value.opponentLevel).isNull()
    }
}
