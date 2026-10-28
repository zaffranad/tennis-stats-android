package fr.zaffran.tennisstats.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import fr.zaffran.tennisstats.data.local.TennisStatsDatabase
import fr.zaffran.tennisstats.data.repository.MatchError
import fr.zaffran.tennisstats.data.repository.OfflineMatchRepository
import fr.zaffran.tennisstats.data.repository.RepositoryResult
import fr.zaffran.tennisstats.domain.input.ShotToRecord
import fr.zaffran.tennisstats.domain.model.Match
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.model.MatchStatus
import fr.zaffran.tennisstats.domain.model.ShotOutcome
import fr.zaffran.tennisstats.domain.model.ShotType
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineMatchRepositoryTest {

    private lateinit var database: TennisStatsDatabase
    private lateinit var clock: TestClock
    private lateinit var repository: OfflineMatchRepository

    private val forehandIn = ShotToRecord(ShotType.FOREHAND, ShotOutcome.IN, isSecondServe = false)

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        clock = TestClock(start = Instant.parse("2026-10-26T10:00:00Z"))
        repository = OfflineMatchRepository(
            database = database,
            matchDao = database.matchDao(),
            shotDao = database.shotDao(),
            clock = clock,
            idGenerator = SequentialIdGenerator(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun createMatch(name: String = "Jean Dupont", level: String? = "15/4"): Match =
        (repository.createMatch(name, level) as RepositoryResult.Success).value

    @Test
    fun createMatch_trimsNameAndStartsInProgress() = runTest {
        val result = repository.createMatch("  Jean Dupont  ", "debutant")

        val match = (result as RepositoryResult.Success).value
        assertThat(match.id).isEqualTo("id-1")
        assertThat(match.opponentName).isEqualTo("Jean Dupont")
        assertThat(match.opponentLevel).isEqualTo("debutant")
        assertThat(match.status).isEqualTo(MatchStatus.IN_PROGRESS)
        assertThat(match.result).isNull()
        assertThat(match.playedAt).isEqualTo(Instant.parse("2026-10-26T10:00:00Z"))
        assertThat(repository.observeMatch(match.id).first()).isEqualTo(match)
    }

    @Test
    fun createMatch_blankName_isRefused() = runTest {
        assertThat(repository.createMatch("   ", null))
            .isEqualTo(RepositoryResult.Failure(MatchError.OpponentNameRequired))
        assertThat(repository.observeActiveMatch().first()).isNull()
    }

    @Test
    fun createMatch_whileAnotherIsInProgress_isRefused() = runTest {
        createMatch()

        assertThat(repository.createMatch("Paul Martin", null))
            .isEqualTo(RepositoryResult.Failure(MatchError.MatchAlreadyInProgress))
    }

    @Test
    fun createMatch_afterPreviousMatchCompleted_isAccepted() = runTest {
        val first = createMatch()
        repository.finishMatch(first.id, MatchResult.WIN)

        assertThat(repository.createMatch("Paul Martin", null)).isInstanceOf(RepositoryResult.Success::class.java)
    }

    @Test
    fun addShot_recordsShotAndUpdatesObservers() = runTest {
        val match = createMatch()

        repository.observeShots(match.id).test {
            assertThat(awaitItem()).isEmpty()

            repository.addShot(match.id, ShotToRecord(ShotType.SERVE, ShotOutcome.FAULT, isSecondServe = false))
            assertThat(awaitItem().map { it.outcome }).containsExactly(ShotOutcome.FAULT)

            repository.addShot(match.id, ShotToRecord(ShotType.SERVE, ShotOutcome.IN, isSecondServe = true))
            val shots = awaitItem()
            assertThat(shots.map { it.isSecondServe }).containsExactly(true, false).inOrder()
        }
        assertThat(repository.observeActiveMatch().first()!!.shotCount).isEqualTo(2)
    }

    @Test
    fun addShot_onCompletedMatch_isRefused() = runTest {
        val match = createMatch()
        repository.finishMatch(match.id, MatchResult.LOSS)

        assertThat(repository.addShot(match.id, forehandIn))
            .isEqualTo(RepositoryResult.Failure(MatchError.MatchNotInProgress))
        assertThat(repository.observeShots(match.id).first()).isEmpty()
    }

    @Test
    fun addShot_onUnknownMatch_isRefused() = runTest {
        assertThat(repository.addShot("missing", forehandIn))
            .isEqualTo(RepositoryResult.Failure(MatchError.MatchNotFound))
    }

    @Test
    fun deleteLastShot_removesMostRecentShotUntilEmpty() = runTest {
        val match = createMatch()
        repository.addShot(match.id, forehandIn)
        val volley = (
            repository.addShot(match.id, ShotToRecord(ShotType.VOLLEY, ShotOutcome.NET, false))
                as RepositoryResult.Success
            ).value

        assertThat(repository.deleteLastShot(match.id)).isEqualTo(RepositoryResult.Success(volley))
        assertThat(repository.observeShots(match.id).first().map { it.type }).containsExactly(ShotType.FOREHAND)

        repository.deleteLastShot(match.id)
        assertThat(repository.deleteLastShot(match.id)).isEqualTo(RepositoryResult.Success(null))
    }

    @Test
    fun deleteShot_removesThatShotOnly() = runTest {
        val match = createMatch()
        val forehand = (repository.addShot(match.id, forehandIn) as RepositoryResult.Success).value
        repository.addShot(match.id, ShotToRecord(ShotType.VOLLEY, ShotOutcome.NET, false))

        assertThat(repository.deleteShot(match.id, forehand.id)).isEqualTo(RepositoryResult.Success(forehand))
        assertThat(repository.observeShots(match.id).first().map { it.type }).containsExactly(ShotType.VOLLEY)
        assertThat(repository.deleteShot(match.id, forehand.id)).isEqualTo(RepositoryResult.Success(null))
    }

    @Test
    fun deleteShot_onCompletedMatch_isRefused() = runTest {
        val match = createMatch()
        val forehand = (repository.addShot(match.id, forehandIn) as RepositoryResult.Success).value
        repository.finishMatch(match.id, MatchResult.WIN)

        assertThat(repository.deleteShot(match.id, forehand.id))
            .isEqualTo(RepositoryResult.Failure(MatchError.MatchNotInProgress))
        assertThat(repository.observeShots(match.id).first()).hasSize(1)
    }

    @Test
    fun deleteLastShot_onCompletedMatch_isRefused() = runTest {
        val match = createMatch()
        repository.addShot(match.id, forehandIn)
        repository.finishMatch(match.id, MatchResult.WIN)

        assertThat(repository.deleteLastShot(match.id))
            .isEqualTo(RepositoryResult.Failure(MatchError.MatchNotInProgress))
        assertThat(repository.observeShots(match.id).first()).hasSize(1)
    }

    @Test
    fun finishMatch_completesWithResult_andOnlyOnce() = runTest {
        val match = createMatch()

        assertThat(repository.finishMatch(match.id, MatchResult.WIN)).isEqualTo(RepositoryResult.Success(Unit))
        val finished = repository.observeMatch(match.id).first()!!
        assertThat(finished.status).isEqualTo(MatchStatus.COMPLETED)
        assertThat(finished.result).isEqualTo(MatchResult.WIN)
        assertThat(repository.observeActiveMatch().first()).isNull()
        assertThat(repository.observeCompletedMatches().first().map { it.match.id }).containsExactly(match.id)

        assertThat(repository.finishMatch(match.id, MatchResult.LOSS))
            .isEqualTo(RepositoryResult.Failure(MatchError.MatchNotInProgress))
        assertThat(repository.observeMatch(match.id).first()!!.result).isEqualTo(MatchResult.WIN)
    }

    @Test
    fun deleteMatch_removesMatchAndShots_inProgressOrCompleted() = runTest {
        val inProgress = createMatch()
        repository.addShot(inProgress.id, forehandIn)
        assertThat(repository.deleteMatch(inProgress.id)).isEqualTo(RepositoryResult.Success(Unit))
        assertThat(repository.observeMatch(inProgress.id).first()).isNull()
        assertThat(repository.observeShots(inProgress.id).first()).isEmpty()

        val completed = createMatch()
        repository.finishMatch(completed.id, MatchResult.LOSS)
        assertThat(repository.deleteMatch(completed.id)).isEqualTo(RepositoryResult.Success(Unit))
        assertThat(repository.observeCompletedMatches().first()).isEmpty()

        assertThat(repository.deleteMatch("missing")).isEqualTo(RepositoryResult.Failure(MatchError.MatchNotFound))
    }
}
