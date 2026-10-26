package fr.zaffran.tennisstats.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "shots",
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["matchId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["matchId", "createdAt"])],
)
data class ShotEntity(
    /** UUID v4. */
    @PrimaryKey val id: String,
    val matchId: String,
    /** serve, forehand, backhand, volley, smash, lob, drop_shot. */
    val type: String,
    /** in, winner, ace, fault, net, unforced_error. */
    val outcome: String,
    val isSecondServe: Boolean,
    /** Stored as epoch millis (see [Converters]). */
    val createdAt: Instant,
)
