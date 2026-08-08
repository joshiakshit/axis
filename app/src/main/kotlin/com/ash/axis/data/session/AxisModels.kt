package com.ash.axis.data.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionRequest(
    val token: String,
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
        const val ROLE_USER = "user"
        const val ROLE_ADMIN = "admin"
    }
}

@Serializable
data class UsersResponse(
    val users: List<AdminUser> = emptyList(),
)

// A governed user as shown in the admin list.
@Serializable
data class AdminUser(
    val admno: String,
    val name: String = "",
    val email: String = "",
    val status: String = "pending",
    val role: String = "user",
    @SerialName("last_seen_at") val lastSeenAt: String = "",
)
