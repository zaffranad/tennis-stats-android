package fr.zaffran.tennisstats.ui.match

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import fr.zaffran.tennisstats.data.SequentialIdGenerator
import fr.zaffran.tennisstats.data.inMemoryDatabase
import fr.zaffran.tennisstats.data.local.TennisStatsDatabase
import fr.zaffran.tennisstats.data.repository.OfflineMatchRepository
import fr.zaffran.tennisstats.data.repository.RepositoryResult
import fr.zaffran.tennisstats.data.util.Clock
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.model.MatchStatus
import fr.zaffran.tennisstats.navigation.Match as MatchRoute
import fr.zaffran.tennisstats.navigation.MatchTab
import fr.zaffran.tennisstats.ui.theme.TennisStatsTheme
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Match screen with a real ViewModel and an in-memory Room repository. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MatchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: TennisStatsDatabase
    private lateinit var repository: OfflineMatchRepository
    private lateinit var matchId: String

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        repository = OfflineMatchRepository(
            database = database,
            matchDao = database.matchDao(),
            shotDao = database.shotDao(),
            clock = Clock { Instant.now() },
            idGenerator = SequentialIdGenerator(),
        )
        matchId = runBlocking {
            (repository.createMatch("Jean Dupont", "15/4") as RepositoryResult.Success).value.id
        }
        val viewModel = MatchViewModel(repository, SavedStateHandle(), MatchRoute(matchId, MatchTab.INPUT))
        composeRule.setContent {
            TennisStatsTheme { MatchScreen(onBack = {}, onMatchDeleted = {}, viewModel = viewModel) }
        }
        composeRule.waitUntil { composeRule.onAllNodes(hasTestTag("shot_tracker")).fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun header() = composeRule.onNodeWithTag("header_right", useUnmergedTree = true)

    private fun waitForText(text: String, tag: String) = composeRule.waitUntil(timeoutMillis = 3_000) {
        composeRule.onAllNodes(hasTestTag(tag) and hasAnyDescendant(hasText(text)), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForBanner(text: String) = composeRule.waitUntil(timeoutMillis = 3_000) {
        composeRule.onAllNodes(hasText(text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
    }

    private fun waitIdleTracker() = composeRule.waitUntil(timeoutMillis = 3_000) {
        composeRule.onAllNodes(
            hasTestTag("outcome_right_in") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled),
        )
            .fetchSemanticsNodes().isEmpty()
    }

    private val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
    private val notSelected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)

    @Test
    fun doubleFault_sequence() {
        composeRule.onNodeWithTag("serve_1").performClick()
        header().assert(hasAnyDescendant(hasText("1er service")))

        // « Raté » records nothing until it is qualified.
        composeRule.onNodeWithTag("miss_right").performClick()
        composeRule.onNodeWithTag("miss_prompt").assertExists()
        composeRule.onNodeWithTag("outcome_right_fault").performClick()
        waitForText("2e service", "header_right")
        composeRule.onNodeWithTag("serve_2").assert(selected)
        waitIdleTracker()

        composeRule.onNodeWithTag("miss_right").performClick()
        composeRule.onNodeWithTag("outcome_right_net").performClick()

        waitForBanner("Double faute")
        waitForText("Coup droit", "header_right")
        composeRule.onNodeWithTag("serve_1").assert(notSelected)
        composeRule.onNodeWithTag("serve_2").assert(notSelected)
        composeRule.onNodeWithTag("miss_right").assertExists()
        composeRule.onNodeWithTag("miss_prompt").assertDoesNotExist()
    }

    @Test
    fun manualPath_untilVictory() {
        composeRule.onNodeWithTag("serve_1").performClick()
        composeRule.onNodeWithTag("miss_right").performClick()
        composeRule.onNodeWithTag("outcome_right_fault").performClick()
        waitForBanner("1er service → Out")
        waitIdleTracker()
        composeRule.onNodeWithTag("outcome_right_in").performClick()
        waitForBanner("2e service → En jeu")
        waitIdleTracker()
        composeRule.onNodeWithTag("outcome_right_in").performClick()
        waitForBanner("Coup droit → En jeu")
        waitIdleTracker()
        // The rally is won: the forehand is qualified as a winner afterwards.
        composeRule.onNodeWithTag("qualify_winner").performClick()
        waitForBanner("Coup droit → Winner")
        composeRule.onNodeWithTag("qualify_winner").assert(selected)
        composeRule.onNodeWithTag("modifier_volley").performClick()
        composeRule.onNodeWithTag("modifier_volley").assert(selected)
        composeRule.onNodeWithTag("miss_left").performClick()
        composeRule.onNodeWithTag("outcome_left_net").performClick()
        waitForBanner("Volée → Filet")
        waitIdleTracker()
        composeRule.onNodeWithText("Derniers coups (4 total)").assertExists()

        composeRule.onNodeWithTag("undo_last_shot").performClick()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodes(hasText("Derniers coups (3 total)")).fetchSemanticsNodes().isNotEmpty()
        }
        waitForBanner("Annulé\u00A0: Volée → Filet")
        composeRule.onNodeWithTag("last_shot").assert(hasAnyDescendant(hasText("Coup droit → Winner")))

        composeRule.onNodeWithText("Statistiques").performClick()
        composeRule.onNodeWithText("1er srv").assertExists()
        composeRule.onNodeWithText("0/1").assertExists()
        composeRule.onNodeWithText("1/1").assertExists()
        composeRule.onNodeWithText("Volée").assertDoesNotExist()

        composeRule.onNodeWithText("Terminer").performClick()
        composeRule.onNodeWithText("Terminer le match").assertExists()
        composeRule.onNodeWithText("Victoire").performClick()
        composeRule.waitUntil(3_000) {
            runBlocking { repository.observeMatch(matchId).first()?.status == MatchStatus.COMPLETED }
        }
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodes(hasText("Terminer le match")).fetchSemanticsNodes().isEmpty()
        }
        assertThat(runBlocking { repository.observeMatch(matchId).first()!!.result }).isEqualTo(MatchResult.WIN)
        composeRule.onNodeWithText("Victoire").assertExists()
        composeRule.onNodeWithText("Terminer").assertDoesNotExist()
        composeRule.onNodeWithTag("shot_tracker").assertDoesNotExist()
    }

    @Test
    fun deleteShot_fromFeed_removesThatShot() {
        composeRule.onNodeWithTag("miss_left").performClick()
        composeRule.onNodeWithTag("outcome_left_fault").performClick()
        waitForBanner("Revers → Out")
        waitIdleTracker()
        composeRule.onNodeWithTag("outcome_right_in").performClick()
        waitForBanner("Coup droit → En jeu")
        waitIdleTracker()

        composeRule.onNodeWithTag("delete_shot_1").performClick()

        composeRule.waitUntil(3_000) {
            composeRule.onAllNodes(hasText("Derniers coups (1 total)")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("last_shot").assert(hasAnyDescendant(hasText("Coup droit → En jeu")))
        composeRule.onNodeWithText("Revers → Out").assertDoesNotExist()
    }
}
