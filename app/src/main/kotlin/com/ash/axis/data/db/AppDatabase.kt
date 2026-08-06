package com.ash.axis.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CacheEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
}
