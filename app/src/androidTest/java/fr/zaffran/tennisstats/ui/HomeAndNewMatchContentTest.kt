package fr.zaffran.tennisstats.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import fr.zaffran.tennisstats.domain.model.Match
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.model.MatchStatus
import fr.zaffran.tennisstats.domain.model.MatchSummary
import fr.zaffran.tennisstats.navigation.MatchTab
import fr.zaffran.tennisstats.ui.home.HomeContent
import fr.zaffran.tennisstats.ui.home.HomeUiState
import fr.zaffran.tennisstats.ui.newmatch.NewMatchContent
import fr.zaffran.tennisstats.ui.newmatch.NewMatchError
import fr.zaffran.tennisstats.ui.newmatch.NewMatchUiState
import fr.zaffran.tennisstats.ui.theme.TennisStatsTheme
import java.time.Instant
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeAndNewMatchContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** The new match form opens the keyboard, which would cover the next test's content. */
    @After
    fun hideKeyboard() {
        Espresso.closeSoftKeyboard()
    }

    private fun match(id: String, name: String, status: MatchStatus, result: MatchResult?, level: String? = null) =
        Instant.parse("2026-10-26T10:00:00Z").let { Match(id, name, level, result, status, it, it, it) }

    @Test
    fun home_activeMatch_showsCardAndHidesFab() {
        var opened: Pair<String, MatchTab>? = null
        composeRule.setContent {
            TennisStatsTheme {
                HomeContent(
                    uiState = HomeUiState(
                        isLoading = false,
                        activeMatch = MatchSummary(
                            match("m1", "Jean Dupont", MatchStatus.IN_PROGRESS, null, "debutant"),
                            0,
                        ),
                    ),
                    onNewMatch = {},
                    onOpenMatch = { id, tab -> opened = id to tab },
                )
            }
        }

        composeRule.onNodeWithText("vs Jean Dupont").assertIsDisplayed()
        composeRule.onNodeWithText("Niveau : Débutant").assertIsDisplayed()
        composeRule.onNodeWithText("0 coups").assertIsDisplayed()
        composeRule.onNodeWithText("Terminez le match en cours avant d'en démarrer un nouveau.").assertIsDisplayed()
        composeRule.onNodeWithText("Nouveau match").assertDoesNotExist()

        composeRule.onNodeWithText("Reprendre le match").performClick()
        assertThat(opened).isEqualTo("m1" to MatchTab.INPUT)
    }

    @Test
    fun home_history_opensStatsTab() {
        var opened: Pair<String, MatchTab>? = null
        var newMatchClicked = false
        composeRule.setContent {
            TennisStatsTheme {
                HomeContent(
                    uiState = HomeUiState(
                        isLoading = false,
                        completedMatches = listOf(
                            MatchSummary(match("m2", "Paul Martin", MatchStatus.COMPLETED, MatchResult.WIN), 1),
                        ),
                    ),
                    onNewMatch = { newMatchClicked = true },
                    onOpenMatch = { id, tab -> opened = id to tab },
                )
            }
        }

        composeRule.onNodeWithText("1 coup").assertIsDisplayed()
        composeRule.onNodeWithText("Victoire").assertIsDisplayed()
        composeRule.onNode(hasText("Nouveau match") and hasClickAction()).assertIsDisplayed().performClick()
        assertThat(newMatchClicked).isTrue()
        composeRule.onNodeWithText("vs Paul Martin").performClick()
        assertThat(opened).isEqualTo("m2" to MatchTab.STATS)
    }

    @Test
    fun home_empty_showsEmptyState() {
        composeRule.setContent {
            TennisStatsTheme {
                HomeContent(uiState = HomeUiState(isLoading = false), onNewMatch = {}, onOpenMatch = { _, _ -> })
            }
        }

        composeRule.onNodeWithText("Aucun match terminé pour le moment.").assertIsDisplayed()
        composeRule.onNodeWithText("Commencez par créer un nouveau match !").assertIsDisplayed()
    }

    @Test
    fun newMatch_errorsAndCreatingState() {
        var state by mutableStateOf(NewMatchUiState(error = NewMatchError.OPPONENT_NAME_REQUIRED))
        composeRule.setContent {
            TennisStatsTheme {
                NewMatchContent(state, onBack = {}, onOpponentNameChange = {}, onOpponentLevelChange = {}, onStart = {})
            }
        }

        composeRule.onNodeWithText("Le nom de l'adversaire est requis.").assertIsDisplayed()

        state = NewMatchUiState(opponentName = "Jean", error = NewMatchError.MATCH_ALREADY_IN_PROGRESS)
        composeRule.onNodeWithText(
            "Un match est déjà en cours. Terminez-le avant d'en créer un nouveau.",
        ).assertIsDisplayed()

        state = NewMatchUiState(opponentName = "Jean", isCreating = true)
        composeRule.onNodeWithText("Création...").assertIsDisplayed()
        composeRule.onNodeWithText("Démarrer").assertDoesNotExist()
    }
}
