package com.vendo.core.datastore.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CachedCustomerEntity::class, CachedItemEntity::class, CachedOrderEntity::class,
               CachedQraHeaderEntity::class],
    version = 5,
    exportSchema = false,
)
@TypeConverters(CacheConverters::class)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
}
