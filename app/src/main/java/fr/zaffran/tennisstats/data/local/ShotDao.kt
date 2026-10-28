package fr.zaffran.tennisstats.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShotDao {

    /** Shots of a match, most recent first (rowid breaks ties within the same millisecond). */
    @Query("SELECT * FROM shots WHERE matchId = :matchId ORDER BY createdAt DESC, rowid DESC")
    fun observeShots(matchId: String): Flow<List<ShotEntity>>

    /** Shot counts of every completed match, grouped by type, outcome and serve number (history statistics). */
    @Query(
        """
        SELECT s.matchId, s.type, s.outcome, s.isSecondServe, COUNT(*) AS count
        FROM shots s INNER JOIN matches m ON m.id = s.matchId
        WHERE m.status = 'completed'
        GROUP BY s.matchId, s.type, s.outcome, s.isSecondServe
        """,
    )
    fun observeCompletedShotCounts(): Flow<List<ShotCountRow>>

    @Query("SELECT * FROM shots WHERE matchId = :matchId ORDER BY createdAt DESC, rowid DESC LIMIT 1")
    suspend fun getLastShot(matchId: String): ShotEntity?

    @Query("SELECT * FROM shots WHERE id = :shotId AND matchId = :matchId")
    suspend fun getShot(matchId: String, shotId: String): ShotEntity?

    @Insert
    suspend fun insertShot(shot: ShotEntity)

    /** Deletes the most recent shot of a match. Returns the number of deleted rows (0 if none). */
    @Query(
        """
        DELETE FROM shots WHERE rowid = (
            SELECT rowid FROM shots WHERE matchId = :matchId ORDER BY createdAt DESC, rowid DESC LIMIT 1
        )
        """,
    )
    suspend fun deleteLastShot(matchId: String): Int

    /** Changes the outcome of one shot of a match. Returns the number of updated rows (0 if not found). */
    @Query("UPDATE shots SET outcome = :outcome WHERE id = :shotId AND matchId = :matchId")
    suspend fun updateOutcome(matchId: String, shotId: String, outcome: String): Int

    /** Deletes one shot of a match. Returns the number of deleted rows (0 if not found). */
    @Query("DELETE FROM shots WHERE id = :shotId AND matchId = :matchId")
    suspend fun deleteShot(matchId: String, shotId: String): Int
}
