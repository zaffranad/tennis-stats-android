package fr.zaffran.tennisstats.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.zaffran.tennisstats.data.local.MatchEntity
import fr.zaffran.tennisstats.data.local.ShotEntity
import fr.zaffran.tennisstats.data.local.TennisStatsDatabase
import fr.zaffran.tennisstats.data.util.Clock
import fr.zaffran.tennisstats.data.util.IdGenerator
import java.time.Instant

fun inMemoryDatabase(): TennisStatsDatabase = Room.inMemoryDatabaseBuilder(
    ApplicationProvider.getApplicationContext<Context>(),
    TennisStatsDatabase::class.java,
).build()

/** Clock advancing by [stepMillis] at each call. */
class TestClock(start: Instant = Instant.parse("2026-10-26T10:00:00Z"), private val stepMillis: Long = 1_000) : Clock {
    var current: Instant = start

    override fun now(): Instant = current.also { current = current.plusMillis(stepMillis) }
}

class SequentialIdGenerator(private val prefix: String = "id") : IdGenerator {
    private var next = 1

    override fun newId(): String = "$prefix-${next++}"
}

fun matchEntity(
    id: String,
    status: String = "in_progress",
    result: String? = null,
    playedAt: Instant = Instant.parse("2026-10-26T10:00:00Z"),
    opponentName: String = "Jean Dupont",
) = MatchEntity(
    id = id,
    opponentName = opponentName,
    opponentLevel = null,
    result = result,
    status = status,
    playedAt = playedAt,
    createdAt = playedAt,
    updatedAt = playedAt,
)

fun shotEntity(
    id: String,
    matchId: String,
    createdAt: Instant,
    type: String = "forehand",
    outcome: String = "in",
    isSecondServe: Boolean = false,
) = ShotEntity(
    id = id,
    matchId = matchId,
    type = type,
    outcome = outcome,
    isSecondServe = isSecondServe,
    createdAt = createdAt,
)
