package com.ash.axis.domain.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AppNotification(
    @JsonNames("notification_id", "id", "Id")
    val id: String = "",
    @JsonNames("notification_title", "title", "Title")
    val title: String = "",
    @JsonNames("notification_message", "message", "Message", "body", "description")
    val message: String = "",
    @JsonNames("notification_date", "date", "Date", "created_at", "createdAt")
    val date: String = "",
    @JsonNames("notification_type", "type", "Type")
    val type: String = "",
    @JsonNames("is_read", "isRead", "read")
    val read: Boolean = false,
)
