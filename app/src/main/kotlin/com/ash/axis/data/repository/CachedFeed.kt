package com.ash.axis.data.repository

import com.ash.axis.data.db.CacheDao
import com.ash.axis.data.db.CacheEntity
import com.ash.core.storage.CacheFreshness
import com.ash.core.storage.CachePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.ResponseBody
import retrofit2.Response

data class FeedSnapshot<T>(
    val data: T,
    val updatedAt: Long,
    val fromCache: Boolean = false,
    val error: Exception? = null,
)

internal class CachedFeed(
    private val cacheDao: CacheDao,
    private val json: Json,
) {
    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    suspend fun <T> load(
        key: String,
        serializer: KSerializer<T>,
        policy: CachePolicy,
        forceRefresh: Boolean,
        fetch: suspend () -> T,
    ): FeedSnapshot<T> {
        val entry = cacheDao.get(key)
        val cached =
            entry?.let {
                try {
                    FeedSnapshot(json.decodeFromString(serializer, it.data), it.cachedAt, fromCache = true)
                } catch (_: SerializationException) {
                    null
                }
            }
        if (!forceRefresh && cached != null && policy.evaluate(cached.updatedAt) == CacheFreshness.FRESH) return cached
        return try {
            val data = fetch()
            val updated = CacheEntity(key, json.encodeToString(serializer, data))
            cacheDao.put(updated)
            FeedSnapshot(data, updated.cachedAt)
        } catch (e: CancellationException) {
            throw e
        } catch (_: SerializationException) {
            val failure = IllegalStateException("Could not read the server response. Please try again.")
            cached?.copy(error = failure) ?: throw failure
        } catch (e: Exception) {
            cached?.copy(error = e) ?: throw e
        }
    }
}

internal fun Json.feedArray(
    response: Response<ResponseBody>,
    vararg keys: String,
): JsonArray {
    if (!response.isSuccessful) {
        response.errorBody()?.close()
        throw IcloudServerException(response.code(), "Request failed: HTTP ${response.code()}")
    }
    val raw = response.body()?.use { it.string() } ?: error("The server returned no data")
    return parseToJsonElement(raw).feedArray(keys.toSet() + setOf("data", "result"))
}

private fun JsonElement.feedArray(keys: Set<String>): JsonArray {
    if (this is JsonArray) return this
    val obj = this as? JsonObject ?: error("Unexpected response format")
    check((obj["status"] as? JsonPrimitive)?.content != "false") { "The server could not load the data" }
    val data = keys.firstNotNullOfOrNull { obj[it] } ?: error("The response is missing data")
    return data.feedArray(keys)
}
