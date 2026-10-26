package fr.zaffran.tennisstats.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [MatchEntity::class, ShotEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TennisStatsDatabase : RoomDatabase() {
    abstract fun matchDao(): MatchDao

    abstract fun shotDao(): ShotDao

    companion object {
        const val NAME = "tennis_stats.db"
    }
}
