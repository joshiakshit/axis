package com.ash.axis.data.config

import com.ash.axis.data.api.RemoteConfigApi
import com.ash.core.storage.PreferencesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

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

        fun effectiveAuthToken(fallback: String): String = current.authToken?.takeIf { it.isNotBlank() } ?: fallback

        fun appVersion(): String = current.appVersion.ifBlank { RemoteConfig.DEFAULT_APP_VERSION }

        // Restore overrides before the first network request.
        suspend fun hydrate() {
            val raw = preferencesStore.getString(KEY, "").first()
            if (raw.isBlank()) return
            runCatching { json.decodeFromString<RemoteConfig>(raw) }.getOrNull()?.let { publish(it) }
        }

        // Keep the last good config on failure so startup can continue.
        @Suppress("TooGenericExceptionCaught")
        suspend fun refresh() {
            val client = api ?: return
            val fetched =
                try {
                    client.getConfig()
                } catch (e: CancellationException) {
                    throw e
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
