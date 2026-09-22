package com.ash.axis.data.session

import kotlinx.serialization.Serializable

// POST /v1/session body. `token` is the active iCloudEMS access token; the rest is best-effort telemetry the
// backend stores for the admin dashboard (which build/device each user runs, how active they are).
@Serializable
data class SessionRequest(
    val token: String,
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    val deviceModel: String = "",
    val androidSdk: Int = 0,
    val deviceId: String = "",
)

// A single usage counter to bump, e.g. name = "qr_scan".
@Serializable
data class UsageEvent(
    val name: String,
    val count: Int = 1,
)

@Serializable
data class EventsRequest(
    val events: List<UsageEvent>,
)

// Response of POST /v1/session. `status`/`role` mirror the backend; defaults keep a partial/absent response usable.
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
