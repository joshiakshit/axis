package com.ash.axis.data.session

import android.util.Log
import com.ash.axis.data.api.AxisBackendApi
import com.ash.core.security.TokenManager
import com.ash.core.storage.PreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// Access decision surfaced to the gate. Only an explicit `pending` blocks — unknown / offline / disabled all
// fall through (fail-open), so a network hiccup can never lock a user out of the app.
data class Access(
    val enabled: Boolean,
    val status: String,
    val role: String,
    val checking: Boolean,
)

// Client half of the governance layer. Calls POST /v1/session for the active account, caches the result
// per-admno, and hands the gate/admin UI the current access + admin session token. `api` is null when the
// build has no REMOTE_CONFIG_URL, which disables governance entirely (the app runs ungoverned).
@Singleton
class AxisSessionRepository
    @Inject
    constructor(
        private val api: AxisBackendApi?,
        private val tokenManager: TokenManager,
        private val preferencesStore: PreferencesStore,
        private val json: Json,
    ) {
        private val enabled = api != null
        private val mutableState =
            MutableStateFlow(Access(enabled, AxisSession.STATUS_UNKNOWN, AxisSession.ROLE_USER, checking = false))
        val state: StateFlow<Access> = mutableState.asStateFlow()

        @Volatile
        private var token: String? = null

        fun isAdmin(): Boolean {
            val access = mutableState.value
            return access.status == AxisSession.STATUS_APPROVED && access.role == AxisSession.ROLE_ADMIN
        }

        private fun authHeader(): String? = token?.let { "Bearer $it" }

        // Load the active account's last-known access from disk so a returning pending/approved user is gated
        // correctly before the network responds.
        suspend fun hydrate() {
            if (!enabled) return
            val raw = preferencesStore.getUserString(KEY, "").first()
            if (raw.isBlank()) return
            runCatching { json.decodeFromString<AxisSession>(raw) }.getOrNull()?.let { publish(it) }
        }

        // Re-check access for the active account. Network/server errors keep the last-known status.
        @Suppress("TooGenericExceptionCaught")
        suspend fun refresh() {
            val client = api ?: return
            val access = tokenManager.getAccessToken() ?: return
            mutableState.update { it.copy(checking = true) }
            try {
                val session = client.session(SessionRequest(access))
                preferencesStore.putUserString(KEY, json.encodeToString(session))
                publish(session)
            } catch (e: Exception) {
                Log.w("AxisSession", "session refresh failed", e)
            } finally {
                mutableState.update { it.copy(checking = false) }
            }
        }

        suspend fun listUsers(): List<AdminUser> {
            val client = api ?: return emptyList()
            val auth = authHeader() ?: return emptyList()
            return client.listUsers(auth).users
        }

        suspend fun setUserStatus(
            admno: String,
            allow: Boolean,
        ): AdminUser? {
            val client = api ?: return null
            val auth = authHeader() ?: return null
            return if (allow) client.allow(admno, auth) else client.kick(admno, auth)
        }

        private fun publish(session: AxisSession) {
            token = session.sessionToken
            mutableState.update { it.copy(status = session.status, role = session.role) }
        }

        private companion object {
            const val KEY = "axis_session"
        }
    }
