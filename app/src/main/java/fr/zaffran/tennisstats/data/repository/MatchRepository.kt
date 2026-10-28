package fr.zaffran.tennisstats.data.repository

import fr.zaffran.tennisstats.domain.input.ShotToRecord
import fr.zaffran.tennisstats.domain.model.Match
import fr.zaffran.tennisstats.domain.model.MatchResult
import fr.zaffran.tennisstats.domain.model.MatchSummary
import fr.zaffran.tennisstats.domain.model.Shot
import fr.zaffran.tennisstats.domain.stats.MatchTally
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

    /** All completed matches with the tally of their shots, most recently played first (history statistics). */
    fun observeCompletedMatchTallies(): Flow<List<MatchTally>>

    fun observeMatch(matchId: String): Flow<Match?>

    /** Shots of a match, most recent first. */
    fun observeShots(matchId: String): Flow<List<Shot>>

    /** RG-01, RG-02: creates an in-progress match (opponent name trimmed). */
    suspend fun createMatch(opponentName: String, opponentLevel: String?): RepositoryResult<Match>

    /** RG-03, RG-08: records a shot on an in-progress match. */
    suspend fun addShot(matchId: String, shot: ShotToRecord): RepositoryResult<Shot>

    /** RG-06: deletes the most recent shot of an in-progress match; returns it, or null if there was none. */
    suspend fun deleteLastShot(matchId: String): RepositoryResult<Shot?>

    /** RG-06: deletes one shot of an in-progress match; returns it, or null if it does not exist. */
    suspend fun deleteShot(matchId: String, shotId: String): RepositoryResult<Shot?>

    /**
     * RG-10: qualifies a successful shot of an in-progress match as a winner (`ace` for a serve) or, with
     * [isWinner] false, back to `in`. Returns the updated shot, or null if it does not exist or was missed.
     */
    suspend fun setShotWinner(matchId: String, shotId: String, isWinner: Boolean): RepositoryResult<Shot?>

    /** RG-04: completes an in-progress match. */
    suspend fun finishMatch(matchId: String, result: MatchResult): RepositoryResult<Unit>

    /** RG-05, RG-07: deletes a match and all its shots. */
    suspend fun deleteMatch(matchId: String): RepositoryResult<Unit>
}
