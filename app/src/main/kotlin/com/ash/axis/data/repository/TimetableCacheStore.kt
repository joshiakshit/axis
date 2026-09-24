package com.ash.axis.data.repository

import com.ash.axis.data.db.CacheDao
import com.ash.axis.data.db.JsonCache
import com.ash.axis.domain.model.TimetableSlot
import com.ash.core.storage.CachePolicy
import com.ash.core.storage.CachedResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

internal class TimetableCacheStore(
    cacheDao: CacheDao,
    json: Json,
) {
    private val cache = JsonCache(cacheDao, json)
    private val dao = cacheDao
    private val mapSerializer: KSerializer<Map<String, List<TimetableSlot>>> =
        MapSerializer(String.serializer(), ListSerializer(TimetableSlot.serializer()))

    suspend fun peek(key: String): CachedResult<Map<String, List<TimetableSlot>>>? {
        return cache.read(key, mapSerializer, CachePolicy.TIMETABLE)
    }

    suspend fun cached(
        key: String,
        policy: CachePolicy,
    ): Map<String, List<TimetableSlot>>? {
        return cache.readAccepted(key, mapSerializer, policy)?.data
    }

    suspend fun cachedAnyAge(key: String): Map<String, List<TimetableSlot>>? {
        return cache.read(key, mapSerializer, CachePolicy.TIMETABLE)?.data
    }

    suspend fun store(
        key: String,
        data: Map<String, List<TimetableSlot>>,
    ) {
        cache.write(key, data, mapSerializer)
    }

    suspend fun cachedDateKeyed(
        key: String,
        policy: CachePolicy,
    ): Map<LocalDate, List<TimetableSlot>>? {
        val data = cache.readAccepted(key, mapSerializer, policy)?.data ?: return null
        return runCatching { data.mapKeys { LocalDate.parse(it.key) } }.getOrNull()
    }

    suspend fun cachedDateKeyedAnyAge(key: String): Map<LocalDate, List<TimetableSlot>>? {
        val data = cache.read(key, mapSerializer, CachePolicy.TIMETABLE)?.data ?: return null
        return runCatching { data.mapKeys { LocalDate.parse(it.key) } }.getOrNull()
    }

    suspend fun storeDateKeyed(
        key: String,
        data: Map<LocalDate, List<TimetableSlot>>,
    ) {
        val stringKeyed = data.mapKeys { it.key.toString() }
        cache.write(key, stringKeyed, mapSerializer)
    }

    suspend fun clear() {
        dao.clearAll()
    }
}
