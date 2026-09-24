package com.ash.axis.data.repository

import com.ash.axis.data.db.CacheDao
import com.ash.axis.data.db.JsonCache
import com.ash.core.storage.CachePolicy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal class AttendanceCacheStore(
    cacheDao: CacheDao,
    json: Json,
) {
    private val cache = JsonCache(cacheDao, json)
    private val dao = cacheDao

    suspend fun <T> cached(
        key: String,
        policy: CachePolicy,
        serializer: KSerializer<T>,
    ): T? {
        return cache.readAccepted(key, serializer, policy)?.data
    }

    suspend fun <T> cachedAnyAge(
        key: String,
        serializer: KSerializer<T>,
    ): T? {
        return cache.read(key, serializer, CachePolicy.ATTENDANCE)?.data
    }

    suspend fun <T> store(
        key: String,
        data: T,
        serializer: KSerializer<T>,
    ) {
        cache.write(key, data, serializer)
    }

    suspend fun clear() {
        dao.clearAll()
    }
}
