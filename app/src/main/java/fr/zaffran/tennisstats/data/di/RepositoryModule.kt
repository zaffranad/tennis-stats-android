package fr.zaffran.tennisstats.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.zaffran.tennisstats.data.repository.MatchRepository
import fr.zaffran.tennisstats.data.repository.OfflineMatchRepository
import fr.zaffran.tennisstats.data.util.Clock
import fr.zaffran.tennisstats.data.util.IdGenerator
import fr.zaffran.tennisstats.data.util.SystemClock
import fr.zaffran.tennisstats.data.util.UuidIdGenerator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {

    @Binds
    @Singleton
    fun bindMatchRepository(repository: OfflineMatchRepository): MatchRepository

    @Binds
    fun bindClock(clock: SystemClock): Clock

    @Binds
    fun bindIdGenerator(generator: UuidIdGenerator): IdGenerator
}
