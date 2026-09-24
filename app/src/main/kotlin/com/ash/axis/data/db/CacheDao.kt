package com.ash.axis.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache_entries WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): CacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CacheEntity)

    @Query("DELETE FROM cache_entries WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM cache_entries WHERE substr(`key`, 1, length(:prefix)) = :prefix")
    suspend fun deletePrefix(prefix: String)

    @Query("DELETE FROM cache_entries")
    suspend fun clearAll()
}
