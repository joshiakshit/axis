package com.ash.axis.data.config

import com.ash.axis.data.api.RemoteConfigApi
import com.ash.core.storage.PreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// Source of truth for remote config on the client. Holds the current config in memory (read synchronously
// by the auth layer at request time) plus a StateFlow for the startup gate. `api` is null when the build has
// no REMOTE_CONFIG_URL, which makes the whole feature a no-op and preserves the app's compiled-in defaults.
@Singleton
class RemoteConfigRepository
    @Inject
    constructor(
        private val api: RemoteConfigApi?,
        private val preferencesStore: PreferencesStore,
        private val json: Json,
    ) {
        private val mutableState = MutableStateFlow(RemoteConfig())
        val state: StateFlow<RemoteConfig> = mutableState.asStateFlow()

        @Volatile
        private var current: RemoteConfig = RemoteConfig()

        val enabled: Boolean get() = api != null

        // Effective iCloudEMS bearer: the server override when present, otherwise the caller's fallback
        // (the app's compiled-in BuildConfig token).
        fun effectiveAuthToken(fallback: String): String = current.authToken?.takeIf { it.isNotBlank() } ?: fallback

        fun appVersion(): String = current.appVersion.ifBlank { RemoteConfig.DEFAULT_APP_VERSION }

        // Load the last-fetched config from disk so overrides survive restarts and are ready before the
        // first network call. A corrupt record is ignored (defaults stand).
        suspend fun hydrate() {
            val raw = preferencesStore.getString(KEY, "").first()
            if (raw.isBlank()) return
            runCatching { json.decodeFromString<RemoteConfig>(raw) }.getOrNull()?.let { publish(it) }
        }

        // Fetch the latest config, persist it, and publish. Disabled builds and network errors keep the
        // last good value — this call must never break app startup.
        @Suppress("TooGenericExceptionCaught")
        suspend fun refresh() {
            val client = api ?: return
            val fetched =
                try {
                    client.getConfig()
                } catch (_: Exception) {
                    return
                }
            preferencesStore.putString(KEY, json.encodeToString(fetched))
            publish(fetched)
        }

        private fun publish(config: RemoteConfig) {
            current = config
            mutableState.value = config
        }

        private companion object {
            const val KEY = "remote_config"
        }
    }
