package fr.zaffran.tennisstats.data.repository

import androidx.room.withTransaction
import fr.zaffran.tennisstats.data.local.MatchDao
import fr.zaffran.tennisstats.data.local.ShotDao
import fr.zaffran.tennisstats.data.local.TennisStatsDatabase
import fr.zaffran.tennisstats.data.local.toDomain
import fr.zaffran.tennisstats.data.local.toEntity
import fr.zaffran.tennisstats.data.local.toTallies
import fr.zaffran.tennisstats.data.repository.RepositoryResult.Failure
import fr.zaffran.tennisstats.data.repository.RepositoryResult.Success
import fr.zaffran.tennisstats.data.util.Clock
import fr.zaffran.tennisstats.data.util.IdGenerator
import fr.zaffran.tennisstats.domain.input.ShotToRecord
import fr.zaffran.tennisstats.domain.model.Match
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.model.MatchStatus
import fr.zaffran.tennisstats.domain.model.MatchSummary
import fr.zaffran.tennisstats.domain.model.Shot
import fr.zaffran.tennisstats.domain.stats.MatchTally
import fr.zaffran.tennisstats.domain.stats.ShotTally
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** [MatchRepository] backed by the local Room database only. */
class OfflineMatchRepository @Inject constructor(
    private val database: TennisStatsDatabase,
    private val matchDao: MatchDao,
    private val shotDao: ShotDao,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
) : MatchRepository {

    override fun observeActiveMatch(): Flow<MatchSummary?> =
        matchDao.observeActiveMatch().map { it?.toDomain() }.distinctUntilChanged()

    override fun observeCompletedMatches(): Flow<List<MatchSummary>> =
        matchDao.observeCompletedMatches().map { list -> list.map { it.toDomain() } }.distinctUntilChanged()

    override fun observeCompletedMatchTallies(): Flow<List<MatchTally>> = combine(
        matchDao.observeCompletedMatches(),
        shotDao.observeCompletedShotCounts(),
    ) { matches, counts ->
        val tallies = counts.toTallies()
        matches.map { MatchTally(it.match.toDomain(), tallies[it.match.id] ?: ShotTally.EMPTY) }
    }.distinctUntilChanged()

    override fun observeMatch(matchId: String): Flow<Match?> =
        matchDao.observeMatch(matchId).map { it?.toDomain() }.distinctUntilChanged()

    override fun observeShots(matchId: String): Flow<List<Shot>> =
        shotDao.observeShots(matchId).map { list -> list.map { it.toDomain() } }.distinctUntilChanged()

    override suspend fun createMatch(opponentName: String, opponentLevel: String?): RepositoryResult<Match> {
        val name = opponentName.trim()
        if (name.isEmpty()) return Failure(MatchError.OpponentNameRequired)
        return database.withTransaction {
            if (matchDao.getActiveMatch() != null) return@withTransaction Failure(MatchError.MatchAlreadyInProgress)
            val now = clock.now()
            val match = Match(
                id = idGenerator.newId(),
                opponentName = name,
                opponentLevel = opponentLevel?.takeIf { it.isNotBlank() },
                result = null,
                status = MatchStatus.IN_PROGRESS,
                playedAt = now,
                createdAt = now,
                updatedAt = now,
            )
            matchDao.insert(match.toEntity())
            Success(match)
        }
    }

    override suspend fun addShot(matchId: String, shot: ShotToRecord): RepositoryResult<Shot> =
        database.withTransaction {
            requireInProgress(matchId)?.let { return@withTransaction Failure(it) }
            val recorded = Shot(
                id = idGenerator.newId(),
                matchId = matchId,
                type = shot.type,
                outcome = shot.outcome,
                isSecondServe = shot.isSecondServe,
                createdAt = clock.now(),
            )
            shotDao.insertShot(recorded.toEntity())
            Success(recorded)
        }

    override suspend fun deleteLastShot(matchId: String): RepositoryResult<Shot?> = database.withTransaction {
        requireInProgress(matchId)?.let { return@withTransaction Failure(it) }
        val last = shotDao.getLastShot(matchId)
        if (last != null) shotDao.deleteLastShot(matchId)
        Success(last?.toDomain())
    }

    override suspend fun deleteShot(matchId: String, shotId: String): RepositoryResult<Shot?> =
        database.withTransaction {
            requireInProgress(matchId)?.let { return@withTransaction Failure(it) }
            val shot = shotDao.getShot(matchId, shotId)
            if (shot != null) shotDao.deleteShot(matchId, shotId)
            Success(shot?.toDomain())
        }

    override suspend fun finishMatch(matchId: String, result: MatchResult): RepositoryResult<Unit> =
        database.withTransaction {
            requireInProgress(matchId)?.let { return@withTransaction Failure(it) }
            matchDao.finishMatch(matchId, result.value, clock.now())
            Success(Unit)
        }

    override suspend fun deleteMatch(matchId: String): RepositoryResult<Unit> =
        if (matchDao.deleteMatch(matchId) > 0) Success(Unit) else Failure(MatchError.MatchNotFound)

    /** Returns the error preventing a write on [matchId], or null if it is in progress. */
    private suspend fun requireInProgress(matchId: String): MatchError? {
        val match = matchDao.getMatch(matchId) ?: return MatchError.MatchNotFound
        return if (match.status == MatchStatus.IN_PROGRESS.value) null else MatchError.MatchNotInProgress
    }
}
