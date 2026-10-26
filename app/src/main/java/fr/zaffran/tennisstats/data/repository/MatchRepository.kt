package fr.zaffran.tennisstats.data.repository

import fr.zaffran.tennisstats.domain.input.ShotToRecord
import fr.zaffran.tennisstats.domain.model.Match
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.model.MatchSummary
import fr.zaffran.tennisstats.domain.model.Shot
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point to matches and shots. Enforces the business rules (functional spec §4)
 * regardless of the UI. Storage failures are thrown as exceptions.
 */
interface MatchRepository {

    /** The in-progress match, if any (RG-01 guarantees at most one). */
    fun observeActiveMatch(): Flow<MatchSummary?>

    /** All completed matches, most recently played first. */
    fun observeCompletedMatches(): Flow<List<MatchSummary>>

    fun observeMatch(matchId: String): Flow<Match?>

    /** Shots of a match, most recent first. */
    fun observeShots(matchId: String): Flow<List<Shot>>

    /** RG-01, RG-02: creates an in-progress match (opponent name trimmed). */
    suspend fun createMatch(opponentName: String, opponentLevel: String?): RepositoryResult<Match>

    /** RG-03, RG-08: records a shot on an in-progress match. */
    suspend fun addShot(matchId: String, shot: ShotToRecord): RepositoryResult<Shot>

    /** RG-06: deletes the most recent shot of an in-progress match; returns it, or null if there was none. */
    suspend fun deleteLastShot(matchId: String): RepositoryResult<Shot?>

    /** RG-04: completes an in-progress match. */
    suspend fun finishMatch(matchId: String, result: MatchResult): RepositoryResult<Unit>

    /** RG-05, RG-07: deletes a match and all its shots. */
    suspend fun deleteMatch(matchId: String): RepositoryResult<Unit>
}
