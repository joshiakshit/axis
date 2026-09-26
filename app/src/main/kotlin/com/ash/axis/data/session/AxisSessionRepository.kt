package com.ash.axis.data.session

import android.os.Build
import android.util.Log
import com.ash.axis.BuildConfig
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

// Unknown access permits use. Network errors retain the last known status.
data class Access(
    val enabled: Boolean,
    val status: String,
    val role: String,
    val checking: Boolean,
)

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

        private fun authHeader(): String? = token?.let { "Bearer $it" }

        // Apply cached access before the network responds.
        suspend fun hydrate() {
            if (!enabled) return
            val raw = preferencesStore.getUserString(KEY, "").first()
            if (raw.isBlank()) return
            runCatching { json.decodeFromString<AxisSession>(raw) }.getOrNull()?.let { publish(it) }
        }

        // Network errors retain the last known access status.
        @Suppress("TooGenericExceptionCaught")
        suspend fun refresh() {
            val client = api ?: return
            val access = tokenManager.getAccessToken() ?: return
            mutableState.update { it.copy(checking = true) }
            try {
                val session = client.session(sessionRequest(access))
                preferencesStore.putUserString(KEY, json.encodeToString(session))
                publish(session)
            } catch (e: Exception) {
                Log.w("AxisSession", "session refresh failed", e)
            } finally {
                mutableState.update { it.copy(checking = false) }
            }
        }

        private fun sessionRequest(token: String) =
            SessionRequest(
                token = token,
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                androidSdk = Build.VERSION.SDK_INT,
            )

        @Suppress("TooGenericExceptionCaught")
        suspend fun logEvents(events: List<UsageEvent>) {
            val client = api ?: return
            val auth = authHeader() ?: return
            if (events.isEmpty()) return
            runCatching { client.events(auth, EventsRequest(events)) }
                .onFailure { Log.w("AxisSession", "usage report failed", it) }
        }

        private fun publish(session: AxisSession) {
            token = session.sessionToken
            mutableState.update { it.copy(status = session.status, role = session.role) }
        }

        private companion object {
            const val KEY = "axis_session"
        }
    }
