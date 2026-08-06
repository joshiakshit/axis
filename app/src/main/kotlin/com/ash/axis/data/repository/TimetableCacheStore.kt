package com.ash.axis.data.repository

import com.ash.axis.data.db.CacheDao
import com.ash.axis.data.db.CacheEntity
import com.ash.axis.domain.model.TimetableSlot
import com.ash.core.storage.CacheFreshness
import com.ash.core.storage.CachePolicy
import com.ash.core.storage.CachedResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

internal class TimetableCacheStore(
    private val cacheDao: CacheDao,
    private val json: Json,
) {
    private val mapSerializer: KSerializer<Map<String, List<TimetableSlot>>> =
        MapSerializer(String.serializer(), ListSerializer(TimetableSlot.serializer()))

    suspend fun peek(key: String): CachedResult<Map<String, List<TimetableSlot>>>? {
        val entry = cacheDao.get(key) ?: return null
        val data = runCatching { json.decodeFromString(mapSerializer, entry.data) }.getOrNull() ?: return null
        return CachedResult(data, CachePolicy.TIMETABLE.evaluate(entry.cachedAt), entry.cachedAt)
    }

    suspend fun cached(
        key: String,
        policy: CachePolicy,
    ): Map<String, List<TimetableSlot>>? {
        val entry = cacheDao.get(key) ?: return null
        return when (policy.evaluate(entry.cachedAt)) {
            CacheFreshness.FRESH, CacheFreshness.STALE -> json.decodeFromString(mapSerializer, entry.data)
            CacheFreshness.EXPIRED -> null
        }
    }

    suspend fun cachedAnyAge(key: String): Map<String, List<TimetableSlot>>? {
        val entry = cacheDao.get(key) ?: return null
        return runCatching { json.decodeFromString(mapSerializer, entry.data) }.getOrNull()
    }

    suspend fun store(
        key: String,
        data: Map<String, List<TimetableSlot>>,
    ) {
        cacheDao.put(CacheEntity(key = key, data = json.encodeToString(mapSerializer, data)))
    }

    suspend fun cachedDateKeyed(
        key: String,
        policy: CachePolicy,
    ): Map<LocalDate, List<TimetableSlot>>? {
        val entry = cacheDao.get(key) ?: return null
        return when (policy.evaluate(entry.cachedAt)) {
            CacheFreshness.FRESH, CacheFreshness.STALE -> deserializeDateKeyed(entry.data)
            CacheFreshness.EXPIRED -> null
        }
    }

    suspend fun cachedDateKeyedAnyAge(key: String): Map<LocalDate, List<TimetableSlot>>? {
        val entry = cacheDao.get(key) ?: return null
        return runCatching { deserializeDateKeyed(entry.data) }.getOrNull()
    }

    suspend fun storeDateKeyed(
        key: String,
        data: Map<LocalDate, List<TimetableSlot>>,
    ) {
        val stringKeyed = data.mapKeys { it.key.toString() }
        cacheDao.put(CacheEntity(key = key, data = json.encodeToString(mapSerializer, stringKeyed)))
    }

    suspend fun clear() {
        cacheDao.clearAll()
    }

    private fun deserializeDateKeyed(raw: String): Map<LocalDate, List<TimetableSlot>> {
        val stringKeyed: Map<String, List<TimetableSlot>> = json.decodeFromString(mapSerializer, raw)
        return stringKeyed.mapKeys { LocalDate.parse(it.key) }
    }
}
