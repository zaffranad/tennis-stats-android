package fr.zaffran.tennisstats.ui.stats

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import fr.zaffran.tennisstats.data.SequentialIdGenerator
import fr.zaffran.tennisstats.data.inMemoryDatabase
import fr.zaffran.tennisstats.data.local.TennisStatsDatabase
import fr.zaffran.tennisstats.data.repository.OfflineMatchRepository
import fr.zaffran.tennisstats.data.repository.RepositoryResult
import fr.zaffran.tennisstats.data.util.Clock
import fr.zaffran.tennisstats.domain.input.ShotToRecord
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType
import fr.zaffran.tennisstats.navigation.MatchTab
import fr.zaffran.tennisstats.ui.theme.TennisStatsTheme
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Statistics screen with a real ViewModel and an in-memory Room repository. */
@RunWith(AndroidJUnit4::class)
class StatsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: TennisStatsDatabase
    private lateinit var repository: OfflineMatchRepository
    private var time = Instant.parse("2026-10-01T10:00:00Z")
    private var opened: Pair<String, MatchTab>? = null

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        repository = OfflineMatchRepository(
            database = database,
            matchDao = database.matchDao(),
            shotDao = database.shotDao(),
            clock = Clock { time.also { time = time.plusSeconds(3600) } },
            idGenerator = SequentialIdGenerator(),
        )
        // Two completed matches: 1st serves 1/2 (loss) then 2/2 (win).
        runBlocking {
            playMatch("Paul Martin", firstServes = listOf(ShotOutcome.IN, ShotOutcome.FAULT), MatchResult.LOSS)
            playMatch("Luc Bernard", firstServes = listOf(ShotOutcome.IN, ShotOutcome.ACE), MatchResult.WIN)
        }
        val viewModel = StatsViewModel(repository, SavedStateHandle())
        composeRule.setContent {
            TennisStatsTheme {
                StatsScreen(onBack = {}, onOpenMatch = { id, tab -> opened = id to tab }, viewModel = viewModel)
            }
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun playMatch(name: String, firstServes: List<ShotOutcome>, result: MatchResult): String {
        val id = (repository.createMatch(name, null) as RepositoryResult.Success).value.id
        firstServes.forEach { repository.addShot(id, ShotToRecord(ShotType.SERVE, it, isSecondServe = false)) }
        repository.finishMatch(id, result)
        return id
    }

    /** The tab's vertical list (the « Évolution » tab also has a horizontal one). */
    private val verticalList =
        hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private fun waitForText(text: String) = composeRule.waitUntil(timeoutMillis = 3_000) {
        composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun matchTab_showsLastCompletedMatch_comparedWithTheOther() {
        waitForText("Dernier match terminé")
        composeRule.onNodeWithText("vs Luc Bernard").assertExists()
        composeRule.onNode(verticalList).performScrollToNode(hasText("+50 pts"))
        composeRule.onNodeWithText("moy. 50%").assertExists()

        composeRule.onNodeWithText("Ouvrir le match").performClick()
        assertThat(opened?.second).isEqualTo(MatchTab.STATS)
    }

    @Test
    fun historyAndEvolutionTabs() {
        waitForText("Historique")
        composeRule.onNodeWithText("Historique").performClick()
        waitForText("2 matchs terminés")
        // Pooled first serves: 3/4.
        composeRule.onNodeWithText("3/4").assertExists()

        composeRule.onNodeWithText("Évolution").performClick()
        waitForText("Détail par match")
        composeRule.onNode(verticalList).performScrollToNode(hasText("vs Paul Martin"))
        composeRule.onNodeWithText("vs Paul Martin").performClick()
        assertThat(opened?.second).isEqualTo(MatchTab.STATS)
    }
}
