package fr.zaffran.tennisstats.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {

    @Query(
        """
        SELECT m.*, (SELECT COUNT(*) FROM shots s WHERE s.matchId = m.id) AS shotCount
        FROM matches m
        WHERE m.status = 'in_progress'
        ORDER BY m.playedAt DESC
        LIMIT 1
        """,
    )
    fun observeActiveMatch(): Flow<MatchWithShotCount?>

    @Query(
        """
        SELECT m.*, (SELECT COUNT(*) FROM shots s WHERE s.matchId = m.id) AS shotCount
        FROM matches m
        WHERE m.status = 'completed'
        ORDER BY m.playedAt DESC
        """,
    )
    fun observeCompletedMatches(): Flow<List<MatchWithShotCount>>

    @Query("SELECT * FROM matches WHERE id = :id")
    fun observeMatch(id: String): Flow<MatchEntity?>

    @Query("SELECT * FROM matches WHERE id = :id")
    suspend fun getMatch(id: String): MatchEntity?

    @Query("SELECT * FROM matches WHERE status = 'in_progress' LIMIT 1")
    suspend fun getActiveMatch(): MatchEntity?

    @Insert
    suspend fun insert(match: MatchEntity)

    /** Completes an in-progress match. Returns the number of updated rows (0 if not in progress). */
    @Query(
        """
        UPDATE matches SET status = 'completed', result = :result, updatedAt = :updatedAt
        WHERE id = :id AND status = 'in_progress'
        """,
    )
    suspend fun finishMatch(id: String, result: String, updatedAt: Instant): Int

    /** Deletes a match; its shots are deleted by cascade. Returns the number of deleted rows. */
    @Query("DELETE FROM matches WHERE id = :id")
    suspend fun deleteMatch(id: String): Int
}
