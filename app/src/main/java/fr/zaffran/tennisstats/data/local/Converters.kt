package fr.zaffran.tennisstats.data.local

import androidx.room.TypeConverter
import java.time.Instant

/** Instants are stored as epoch milliseconds (INTEGER). */
class Converters {
    @TypeConverter
    fun fromEpochMillis(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun toEpochMillis(instant: Instant?): Long? = instant?.toEpochMilli()
}
