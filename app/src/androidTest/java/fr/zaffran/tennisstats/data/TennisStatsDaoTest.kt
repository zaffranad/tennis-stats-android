package fr.zaffran.tennisstats.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import fr.zaffran.tennisstats.data.local.MatchDao
import fr.zaffran.tennisstats.data.local.ShotCountRow
import fr.zaffran.tennisstats.data.local.ShotDao
import fr.zaffran.tennisstats.data.local.TennisStatsDatabase
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TennisStatsDaoTest {

    private lateinit var database: TennisStatsDatabase
    private lateinit var matchDao: MatchDao
    private lateinit var shotDao: ShotDao

    private val t0 = Instant.parse("2026-10-26T10:00:00Z")

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        matchDao = database.matchDao()
        shotDao = database.shotDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun shotsAreOrderedMostRecentFirst_withInsertionOrderAsTieBreaker() = runTest {
        matchDao.insert(matchEntity("m1"))
        shotDao.insertShot(shotEntity("a", "m1", t0))
        shotDao.insertShot(shotEntity("b", "m1", t0.plusSeconds(1)))
        shotDao.insertShot(shotEntity("c", "m1", t0.plusSeconds(1)))
        shotDao.insertShot(shotEntity("d", "m1", t0.plusSeconds(2)))

        assertThat(shotDao.observeShots("m1").first().map { it.id }).containsExactly("d", "c", "b", "a").inOrder()
    }

    @Test
    fun deleteLastShot_removesMostRecentShotOfThatMatchOnly() = runTest {
        matchDao.insert(matchEntity("m1"))
        matchDao.insert(matchEntity("m2", status = "completed", result = "win"))
        shotDao.insertShot(shotEntity("a", "m1", t0))
        shotDao.insertShot(shotEntity("b", "m1", t0))
        shotDao.insertShot(shotEntity("other", "m2", t0.plusSeconds(10)))

        assertThat(shotDao.deleteLastShot("m1")).isEqualTo(1)
        assertThat(shotDao.observeShots("m1").first().map { it.id }).containsExactly("a")
        assertThat(shotDao.observeShots("m2").first().map { it.id }).containsExactly("other")

        assertThat(shotDao.deleteLastShot("m1")).isEqualTo(1)
        assertThat(shotDao.deleteLastShot("m1")).isEqualTo(0)
    }

    @Test
    fun deletingMatchCascadesToShots() = runTest {
        matchDao.insert(matchEntity("m1"))
        shotDao.insertShot(shotEntity("a", "m1", t0))
        shotDao.insertShot(shotEntity("b", "m1", t0.plusSeconds(1)))

        assertThat(matchDao.deleteMatch("m1")).isEqualTo(1)

        assertThat(matchDao.getMatch("m1")).isNull()
        assertThat(shotDao.observeShots("m1").first()).isEmpty()
    }

    @Test
    fun activeMatchIncludesShotCount() = runTest {
        matchDao.insert(matchEntity("done", status = "completed", result = "loss"))
        matchDao.insert(matchEntity("m1"))
        shotDao.insertShot(shotEntity("a", "m1", t0))
        shotDao.insertShot(shotEntity("b", "m1", t0))

        val active = matchDao.observeActiveMatch().first()
        assertThat(active!!.match.id).isEqualTo("m1")
        assertThat(active.shotCount).isEqualTo(2)
    }

    @Test
    fun completedMatchesAreSortedByPlayedAtDescending() = runTest {
        matchDao.insert(matchEntity("old", status = "completed", result = "win", playedAt = t0))
        matchDao.insert(matchEntity("new", status = "completed", result = "loss", playedAt = t0.plusSeconds(3600)))
        matchDao.insert(matchEntity("current", playedAt = t0.plusSeconds(7200)))
        shotDao.insertShot(shotEntity("a", "old", t0))

        val completed = matchDao.observeCompletedMatches().first()
        assertThat(completed.map { it.match.id }).containsExactly("new", "old").inOrder()
        assertThat(completed.map { it.shotCount }).containsExactly(0, 1).inOrder()
    }

    @Test
    fun finishMatch_onlyUpdatesInProgressMatch() = runTest {
        matchDao.insert(matchEntity("m1"))

        assertThat(matchDao.finishMatch("m1", "win", t0.plusSeconds(60))).isEqualTo(1)
        val finished = matchDao.getMatch("m1")!!
        assertThat(finished.status).isEqualTo("completed")
        assertThat(finished.result).isEqualTo("win")
        assertThat(finished.updatedAt).isEqualTo(t0.plusSeconds(60))

        assertThat(matchDao.finishMatch("m1", "loss", t0.plusSeconds(120))).isEqualTo(0)
        assertThat(matchDao.getMatch("m1")!!.result).isEqualTo("win")
    }

    @Test
    fun completedShotCounts_groupByMatchTypeOutcomeAndServe_onlyForCompletedMatches() = runTest {
        matchDao.insert(matchEntity("done", status = "completed", result = "win"))
        matchDao.insert(matchEntity("live"))
        shotDao.insertShot(shotEntity("a", "done", t0, type = "serve", outcome = "fault"))
        shotDao.insertShot(shotEntity("b", "done", t0, type = "serve", outcome = "fault"))
        shotDao.insertShot(shotEntity("c", "done", t0, type = "serve", outcome = "fault", isSecondServe = true))
        shotDao.insertShot(shotEntity("d", "done", t0, type = "forehand", outcome = "winner"))
        shotDao.insertShot(shotEntity("e", "live", t0))

        val rows = shotDao.observeCompletedShotCounts().first()

        assertThat(rows).containsExactly(
            ShotCountRow("done", "serve", "fault", false, 2),
            ShotCountRow("done", "serve", "fault", true, 1),
            ShotCountRow("done", "forehand", "winner", false, 1),
        )
    }
}
