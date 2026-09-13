package com.ash.axis.data.repository

import com.ash.axis.data.api.UserApi
import com.ash.axis.data.db.CacheDao
import com.ash.axis.data.db.CacheEntity
import com.ash.axis.domain.model.AppNotification
import com.ash.core.storage.CachePolicy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository
    @Inject
    constructor(
        private val userApi: UserApi,
        private val cacheDao: CacheDao,
        private val json: Json,
    ) {
        private val feed = CachedFeed(cacheDao, json)
        private val listSerializer = ListSerializer(AppNotification.serializer())
        private val readSerializer = SetSerializer(String.serializer())
        private val mutex = Mutex()

        suspend fun getNotifications(
            ownerId: String,
            forceRefresh: Boolean = false,
        ): FeedSnapshot<List<AppNotification>> =
            mutex.withLock {
                val result =
                    feed.load("v2_notifications_$ownerId", listSerializer, CachePolicy.DASHBOARD, forceRefresh) {
                        json.feedArray(userApi.getNotifications(emptyMap()), "notifications").map { item ->
                            val obj = item as? JsonObject ?: error("Invalid notification")
                            json.decodeFromJsonElement(AppNotification.serializer(), normalize(obj)).also {
                                check(it.title.isNotBlank() || it.message.isNotBlank()) { "Invalid notification content" }
                            }
                        }
                    }
                val readIds = readIds(ownerId)
                result.copy(data = result.data.map { it.copy(read = it.read || it.readKey in readIds) })
            }

        suspend fun markAsRead(
            ownerId: String,
            notification: AppNotification,
        ) = mutex.withLock {
            val ids = readIds(ownerId) + notification.readKey
            cacheDao.put(CacheEntity("notification_reads_$ownerId", json.encodeToString(readSerializer, ids)))
        }

        private fun normalize(obj: JsonObject): JsonObject {
            val values =
                obj.mapValues { (key, value) ->
                    if (key in setOf("id", "notification_id", "Id") && value is JsonPrimitive) JsonPrimitive(value.content) else value
                }.toMutableMap()
            val read = listOf("is_read", "isRead", "read").firstNotNullOfOrNull { obj[it] as? JsonPrimitive }
            if (read != null) {
                values.remove("is_read")
                values.remove("isRead")
                values["read"] = JsonPrimitive(read.content in setOf("true", "1"))
            }
            return JsonObject(values)
        }

        private suspend fun readIds(ownerId: String): Set<String> =
            cacheDao.get("notification_reads_$ownerId")?.let { json.decodeFromString(readSerializer, it.data) } ?: emptySet()
    }
