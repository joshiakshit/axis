package com.ash.axis.data.repository

import com.ash.axis.data.api.AuthApi
import com.ash.axis.data.db.CacheDao
import com.ash.axis.data.db.CacheEntity
import com.ash.axis.domain.model.Holiday
import com.ash.core.storage.CacheFreshness
import com.ash.core.storage.CachePolicy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepository
    @Inject
    constructor(
        private val authApi: AuthApi,
        private val cacheDao: CacheDao,
        private val json: Json,
    ) {
        private val holidayListSerializer = ListSerializer(Holiday.serializer())

        suspend fun getHolidays(
            brId: Int,
            acadYear: String,
            forceRefresh: Boolean = false,
        ): List<Holiday> {
            val key = "v1_holidays_${brId}_$acadYear"
            if (!forceRefresh) {
                val entry = cacheDao.get(key)
                if (entry != null) {
                    val freshness = CachePolicy.ATTENDANCE.evaluate(entry.cachedAt)
                    if (freshness != CacheFreshness.EXPIRED) {
                        return runCatching { json.decodeFromString(holidayListSerializer, entry.data) }
                            .getOrDefault(emptyList())
                    }
                }
            }

            return try {
                val response =
                    authApi.getHolidays(
                        mapOf("br_id" to brId.toString(), "acadyr" to acadYear),
                    )
                val body = response.body()?.string()?.trim() ?: return emptyList()
                val holidays = parseHolidays(body)
                cacheDao.put(CacheEntity(key = key, data = json.encodeToString(holidayListSerializer, holidays)))
                holidays
            } catch (_: Exception) {
                val entry = cacheDao.get(key)
                if (entry != null) {
                    runCatching { json.decodeFromString(holidayListSerializer, entry.data) }
                        .getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }
        }

        private fun parseHolidays(raw: String): List<Holiday> {
            val element = json.parseToJsonElement(raw)
            val array =
                when (element) {
                    is JsonArray -> element
                    is JsonObject -> {
                        element["data"] as? JsonArray
                            ?: element["holidays"] as? JsonArray
                            ?: element["result"] as? JsonArray
                            ?: return emptyList()
                    }
                    else -> return emptyList()
                }
            return array.mapNotNull { item ->
                runCatching { json.decodeFromJsonElement(Holiday.serializer(), item) }.getOrNull()
            }.filter { it.date.isNotBlank() && it.name.isNotBlank() }
        }
    }
