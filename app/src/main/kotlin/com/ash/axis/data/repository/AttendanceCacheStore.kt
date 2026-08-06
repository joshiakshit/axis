package com.ash.axis.data.repository

import com.ash.axis.data.db.CacheDao
import com.ash.axis.data.db.CacheEntity
import com.ash.core.storage.CacheFreshness
import com.ash.core.storage.CachePolicy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal class AttendanceCacheStore(
    private val cacheDao: CacheDao,
    private val json: Json,
) {
    suspend fun <T> cached(
        key: String,
        policy: CachePolicy,
        serializer: KSerializer<T>,
    ): T? {
        val entry = cacheDao.get(key) ?: return null
        return when (policy.evaluate(entry.cachedAt)) {
            CacheFreshness.FRESH, CacheFreshness.STALE -> json.decodeFromString(serializer, entry.data)
            CacheFreshness.EXPIRED -> null
        }
    }

    suspend fun <T> cachedAnyAge(
        key: String,
        serializer: KSerializer<T>,
    ): T? {
        val entry = cacheDao.get(key) ?: return null
        return runCatching { json.decodeFromString(serializer, entry.data) }.getOrNull()
    }

    suspend fun <T> store(
        key: String,
        data: T,
        serializer: KSerializer<T>,
    ) {
        cacheDao.put(CacheEntity(key = key, data = json.encodeToString(serializer, data)))
    }

    suspend fun clear() {
        cacheDao.clearAll()
    }
}
