package com.ash.axis.data.repository

import com.ash.axis.data.api.AuthApi
import com.ash.axis.data.db.CacheDao
import com.ash.axis.data.db.CacheEntity
import com.ash.axis.domain.model.AppNotification
import com.ash.core.storage.CacheFreshness
import com.ash.core.storage.CachePolicy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository
    @Inject
    constructor(
        private val authApi: AuthApi,
        private val cacheDao: CacheDao,
        private val json: Json,
    ) {
        private val listSerializer = ListSerializer(AppNotification.serializer())

        suspend fun getNotifications(
            userId: String,
            forceRefresh: Boolean = false,
        ): List<AppNotification> {
            val key = "v1_notifications_$userId"
            if (!forceRefresh) {
                val entry = cacheDao.get(key)
                if (entry != null) {
                    val freshness = CachePolicy.DASHBOARD.evaluate(entry.cachedAt)
                    if (freshness != CacheFreshness.EXPIRED) {
                        return runCatching { json.decodeFromString(listSerializer, entry.data) }
                            .getOrDefault(emptyList())
                    }
                }
            }

            return try {
                val response = authApi.getNotifications(mapOf("user_id" to userId))
                val body = response.body()?.string()?.trim() ?: return emptyList()
                val notifications = parseNotifications(body)
                cacheDao.put(CacheEntity(key = key, data = json.encodeToString(listSerializer, notifications)))
                notifications
            } catch (_: Exception) {
                val entry = cacheDao.get(key)
                if (entry != null) {
                    runCatching { json.decodeFromString(listSerializer, entry.data) }
                        .getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }
        }

        private fun parseNotifications(raw: String): List<AppNotification> {
            val element = json.parseToJsonElement(raw)
            val array =
                when (element) {
                    is JsonArray -> element
                    is JsonObject -> {
                        element["data"] as? JsonArray
                            ?: element["notifications"] as? JsonArray
                            ?: element["result"] as? JsonArray
                            ?: return emptyList()
                    }
                    else -> return emptyList()
                }
            return array.mapNotNull { item ->
                runCatching { json.decodeFromJsonElement(AppNotification.serializer(), item) }.getOrNull()
            }
        }
    }
