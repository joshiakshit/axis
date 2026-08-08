package com.ash.axis.data.session

import kotlinx.serialization.SerialName
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
)

// Partial patch for PUT /v1/admin/config. Only non-null fields are serialized (Json encodeDefaults = false),
// so the backend applies just what the admin changed.
@Serializable
data class ConfigPatch(
    val minSupportedVersionCode: Int? = null,
    val latestVersionCode: Int? = null,
    val latestVersionName: String? = null,
    val updateUrl: String? = null,
    val killSwitch: Boolean? = null,
    val message: String? = null,
    val notice: String? = null,
    val autoApprovePrefix: String? = null,
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

@Serializable
data class ApproveAllResponse(
    val approved: Int = 0,
)

// GET /v1/admin/health — dashboard summary.
@Serializable
data class HealthResponse(
    val service: String = "",
    val users: Int = 0,
    val pending: Int = 0,
    val approved: Int = 0,
    val banned: Int = 0,
    val apkUploaded: Boolean = false,
    val metrics: Map<String, Int> = emptyMap(),
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

@Serializable
data class UsersResponse(
    val users: List<AdminUser> = emptyList(),
)

// A governed user as shown in the admin list, including the usage telemetry the backend records per launch.
@Serializable
data class AdminUser(
    val admno: String,
    val name: String = "",
    val email: String = "",
    val status: String = "pending",
    val role: String = "user",
    @SerialName("last_seen_at") val lastSeenAt: String = "",
    @SerialName("first_seen_at") val firstSeenAt: String = "",
    @SerialName("approved_at") val approvedAt: String = "",
    @SerialName("app_version_name") val appVersionName: String = "",
    @SerialName("app_version_code") val appVersionCode: Int = 0,
    @SerialName("device_model") val deviceModel: String = "",
    @SerialName("android_sdk") val androidSdk: Int = 0,
    @SerialName("session_count") val sessionCount: Int = 0,
)
