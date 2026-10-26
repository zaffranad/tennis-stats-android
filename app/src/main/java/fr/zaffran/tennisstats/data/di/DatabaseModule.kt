package fr.zaffran.tennisstats.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.zaffran.tennisstats.data.local.MatchDao
import fr.zaffran.tennisstats.data.local.ShotDao
import fr.zaffran.tennisstats.data.local.TennisStatsDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TennisStatsDatabase =
        Room.databaseBuilder(context, TennisStatsDatabase::class.java, TennisStatsDatabase.NAME).build()

    @Provides
    fun provideMatchDao(database: TennisStatsDatabase): MatchDao = database.matchDao()

    @Provides
    fun provideShotDao(database: TennisStatsDatabase): ShotDao = database.shotDao()
}
