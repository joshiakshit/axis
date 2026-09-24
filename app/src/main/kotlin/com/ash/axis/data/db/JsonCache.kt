package com.ash.axis.data.db

import com.ash.core.storage.CacheFreshness
import com.ash.core.storage.CachePolicy
import com.ash.core.storage.CachedResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Reads typed cache entries without deciding whether a caller should refresh. */
class JsonCache(
    private val dao: CacheDao,
    private val json: Json,
) {
    suspend fun <T> read(
        key: String,
        serializer: KSerializer<T>,
        policy: CachePolicy,
    ): CachedResult<T>? {
        val entry = dao.get(key) ?: return null
        val data =
            try {
                json.decodeFromString(serializer, entry.data)
            } catch (_: SerializationException) {
                return null
            }
        return CachedResult(data, policy.evaluate(entry.cachedAt), entry.cachedAt)
    }

    suspend fun <T> readAccepted(
        key: String,
        serializer: KSerializer<T>,
        policy: CachePolicy,
    ): CachedResult<T>? = read(key, serializer, policy)?.takeUnless { it.freshness == CacheFreshness.EXPIRED }

    suspend fun <T> write(
        key: String,
        data: T,
        serializer: KSerializer<T>,
    ): Long {
        val entry = CacheEntity(key, json.encodeToString(serializer, data))
        dao.put(entry)
        return entry.cachedAt
    }
}
