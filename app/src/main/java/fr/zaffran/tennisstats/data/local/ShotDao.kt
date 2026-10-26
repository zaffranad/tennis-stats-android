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

    @Query("SELECT * FROM shots WHERE matchId = :matchId ORDER BY createdAt DESC, rowid DESC LIMIT 1")
    suspend fun getLastShot(matchId: String): ShotEntity?

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
}
