package fr.zaffran.tennisstats.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "matches", indices = [Index("status"), Index("playedAt")])
data class MatchEntity(
    /** UUID v4. */
    @PrimaryKey val id: String,
    val opponentName: String,
    val opponentLevel: String?,
    /** "win" | "loss", null while in progress. */
    val result: String?,
    /** "in_progress" | "completed". */
    val status: String,
    /** Stored as epoch millis (see [Converters]). */
    val playedAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
)
