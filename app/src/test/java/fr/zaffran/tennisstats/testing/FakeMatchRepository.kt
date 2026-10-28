package fr.zaffran.tennisstats.testing

import fr.zaffran.tennisstats.data.repository.MatchError
import fr.zaffran.tennisstats.data.repository.MatchRepository
import fr.zaffran.tennisstats.data.repository.RepositoryResult
import fr.zaffran.tennisstats.data.repository.RepositoryResult.Failure
import fr.zaffran.tennisstats.data.repository.RepositoryResult.Success
import fr.zaffran.tennisstats.domain.input.ShotToRecord
import fr.zaffran.tennisstats.domain.model.Match
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.model.MatchStatus
import fr.zaffran.tennisstats.domain.model.MatchSummary
import fr.zaffran.tennisstats.domain.model.Shot
import fr.zaffran.tennisstats.domain.model.winnerOutcome
import fr.zaffran.tennisstats.domain.stats.MatchTally
import fr.zaffran.tennisstats.domain.stats.ShotTally
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [MatchRepository] applying the same business rules as the Room implementation. */
class FakeMatchRepository : MatchRepository {

    private val matches = MutableStateFlow<List<Match>>(emptyList())

    /** Shots in insertion order. */
    private val shots = MutableStateFlow<List<Shot>>(emptyList())

    private var nextId = 1
    private var time = Instant.parse("2026-10-26T10:00:00Z")

    /** When true, write operations throw an [IOException]. */
    var failWrites = false

    /** When set, write operations suspend until it completes (to observe in-flight states). */
    var writeGate: CompletableDeferred<Unit>? = null

    private fun now(): Instant = time.also { time = time.plusSeconds(1) }

    private suspend fun beforeWrite() {
        writeGate?.await()
        if (failWrites) throw IOException("Simulated storage failure")
    }

    fun seedMatch(match: Match) = matches.update { it + match }

    fun seedShots(vararg seeded: Shot) = shots.update { it + seeded }

    fun shotsOf(matchId: String): List<Shot> = shots.value.filter { it.matchId == matchId }.reversed()

    fun matchById(matchId: String): Match? = matches.value.firstOrNull { it.id == matchId }

    private fun summaries(): Flow<List<MatchSummary>> = combine(matches, shots) { matchList, shotList ->
        matchList.map { m -> MatchSummary(m, shotList.count { it.matchId == m.id }) }
    }

    override fun observeActiveMatch(): Flow<MatchSummary?> =
        summaries().map { list -> list.firstOrNull { it.match.isInProgress } }.distinctUntilChanged()

    override fun observeCompletedMatches(): Flow<List<MatchSummary>> = summaries().map { list ->
        list.filter { it.match.status == MatchStatus.COMPLETED }.sortedByDescending { it.match.playedAt }
    }.distinctUntilChanged()

    override fun observeCompletedMatchTallies(): Flow<List<MatchTally>> = combine(matches, shots) {
            matchList,
            shotList,
        ->
        matchList.filter { it.status == MatchStatus.COMPLETED }
            .sortedByDescending { it.playedAt }
            .map { m -> MatchTally(m, ShotTally.of(shotList.filter { it.matchId == m.id })) }
    }.distinctUntilChanged()

    override fun observeMatch(matchId: String): Flow<Match?> =
        matches.map { list -> list.firstOrNull { it.id == matchId } }.distinctUntilChanged()

    override fun observeShots(matchId: String): Flow<List<Shot>> =
        shots.map { list -> list.filter { it.matchId == matchId }.reversed() }.distinctUntilChanged()

    override suspend fun createMatch(opponentName: String, opponentLevel: String?): RepositoryResult<Match> {
        beforeWrite()
        val name = opponentName.trim()
        if (name.isEmpty()) return Failure(MatchError.OpponentNameRequired)
        if (matches.value.any { it.isInProgress }) return Failure(MatchError.MatchAlreadyInProgress)
        val now = now()
        val match = Match("match-${nextId++}", name, opponentLevel, null, MatchStatus.IN_PROGRESS, now, now, now)
        matches.update { it + match }
        return Success(match)
    }

    override suspend fun addShot(matchId: String, shot: ShotToRecord): RepositoryResult<Shot> {
        beforeWrite()
        inProgressError(matchId)?.let { return Failure(it) }
        val recorded = Shot("shot-${nextId++}", matchId, shot.type, shot.outcome, shot.isSecondServe, now())
        shots.update { it + recorded }
        return Success(recorded)
    }

    override suspend fun deleteLastShot(matchId: String): RepositoryResult<Shot?> {
        beforeWrite()
        inProgressError(matchId)?.let { return Failure(it) }
        val last = shots.value.lastOrNull { it.matchId == matchId }
        if (last != null) shots.update { it - last }
        return Success(last)
    }

    override suspend fun deleteShot(matchId: String, shotId: String): RepositoryResult<Shot?> {
        beforeWrite()
        inProgressError(matchId)?.let { return Failure(it) }
        val shot = shots.value.firstOrNull { it.matchId == matchId && it.id == shotId }
        if (shot != null) shots.update { it - shot }
        return Success(shot)
    }

    override suspend fun setShotWinner(matchId: String, shotId: String, isWinner: Boolean): RepositoryResult<Shot?> {
        beforeWrite()
        inProgressError(matchId)?.let { return Failure(it) }
        val shot = shots.value.firstOrNull { it.matchId == matchId && it.id == shotId }
        if (shot == null || !shot.isSuccess) return Success(null)
        val qualified = shot.copy(outcome = winnerOutcome(shot, isWinner))
        shots.update { list -> list.map { if (it == shot) qualified else it } }
        return Success(qualified)
    }

    override suspend fun finishMatch(matchId: String, result: MatchResult): RepositoryResult<Unit> {
        beforeWrite()
        inProgressError(matchId)?.let { return Failure(it) }
        matches.update { list ->
            list.map {
                if (it.id ==
                    matchId
                ) {
                    it.copy(status = MatchStatus.COMPLETED, result = result, updatedAt = now())
                } else {
                    it
                }
            }
        }
        return Success(Unit)
    }

    override suspend fun deleteMatch(matchId: String): RepositoryResult<Unit> {
        beforeWrite()
        if (matches.value.none { it.id == matchId }) return Failure(MatchError.MatchNotFound)
        matches.update { list -> list.filterNot { it.id == matchId } }
        shots.update { list -> list.filterNot { it.matchId == matchId } }
        return Success(Unit)
    }

    private fun inProgressError(matchId: String): MatchError? {
        val match = matches.value.firstOrNull { it.id == matchId } ?: return MatchError.MatchNotFound
        return if (match.isInProgress) null else MatchError.MatchNotInProgress
    }
}

fun testMatch(
    id: String = "m1",
    opponentName: String = "Jean Dupont",
    opponentLevel: String? = null,
    status: MatchStatus = MatchStatus.IN_PROGRESS,
    result: MatchResult? = null,
    playedAt: Instant = Instant.parse("2026-10-26T09:00:00Z"),
) = Match(id, opponentName, opponentLevel, result, status, playedAt, playedAt, playedAt)
