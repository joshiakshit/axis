package com.ash.axis.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.config.RemoteConfig
import com.ash.axis.data.config.RemoteConfigRepository
import com.ash.axis.data.update.UpdateInstaller
import com.ash.axis.data.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel
    @Inject
    constructor(
        private val installer: UpdateInstaller,
        private val remoteConfig: RemoteConfigRepository,
    ) : ViewModel() {
        val config: StateFlow<RemoteConfig> = remoteConfig.state
        val update: StateFlow<UpdateState> = installer.state

        private val mutableChecking = MutableStateFlow(false)
        val checking: StateFlow<Boolean> = mutableChecking.asStateFlow()
        private val mutableChecked = MutableStateFlow(false)
        val checked: StateFlow<Boolean> = mutableChecked.asStateFlow()
        private val mutableCheckError = MutableStateFlow<String?>(null)
        val checkError: StateFlow<String?> = mutableCheckError.asStateFlow()
        private val mutableCompletedVersion = MutableStateFlow(installer.consumeCompletedVersion())
        val completedVersion: StateFlow<String?> = mutableCompletedVersion.asStateFlow()

        fun available(config: RemoteConfig): Boolean = installer.updateAvailable(config)

        fun install(url: String) {
            viewModelScope.launch { installer.downloadAndInstall(url) }
        }

        @Suppress("TooGenericExceptionCaught")
        fun checkForUpdates() {
            if (mutableChecking.value) return
            mutableChecking.value = true
            viewModelScope.launch {
                mutableCheckError.value = null
                mutableChecked.value = false
                try {
                    mutableChecked.value = remoteConfig.refresh()
                    if (!mutableChecked.value) mutableCheckError.value = "Could not check for updates. Try again."
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (_: Exception) {
                    mutableCheckError.value = "Could not check for updates. Try again."
                } finally {
                    mutableChecking.value = false
                }
            }
        }

        fun clearError() = installer.clearError()

        fun dismissCompletedUpdate() {
            mutableCompletedVersion.value = null
        }
    }
