package com.ash.axis.data.session

import kotlinx.serialization.Serializable

// token is the active iCloudEMS access token.
@Serializable
data class SessionRequest(
    val token: String,
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    val deviceModel: String = "",
    val androidSdk: Int = 0,
)

@Serializable
data class UsageEvent(
    val name: String,
    val count: Int = 1,
)

@Serializable
data class EventsRequest(
    val events: List<UsageEvent>,
)

@Serializable
data class AxisSession(
    val status: String = STATUS_UNKNOWN,
    val role: String = ROLE_USER,
    val admno: String = "",
    val name: String = "",
    val sessionToken: String? = null,
) {
    companion object {
        const val STATUS_UNKNOWN = "unknown"
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_BANNED = "banned"
        const val ROLE_USER = "user"
        const val ROLE_ADMIN = "admin"
    }
}
