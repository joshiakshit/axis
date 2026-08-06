package com.ash.axis.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cache_entries")
data class CacheEntity(
    @PrimaryKey val key: String,
    val data: String,
    val cachedAt: Long = System.currentTimeMillis(),
)
