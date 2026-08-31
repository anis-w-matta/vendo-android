package com.vendo.core.datastore.cache

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Provides
    @Singleton
    fun provideCacheDatabase(@ApplicationContext context: Context): CacheDatabase =
        Room.databaseBuilder(context, CacheDatabase::class.java, "vendo_cache.db")
            // The cache is a disposable mirror of server data (see
            // CacheRepository) - a schema bump can just drop and repopulate
            // it on next Refresh rather than needing a real migration path.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideCacheDao(db: CacheDatabase): CacheDao = db.cacheDao()
}
