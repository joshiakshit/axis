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

enum class UpdateCheck { IDLE, CHECKING, CHECKED, FAILED }

@HiltViewModel
class UpdateViewModel
    @Inject
    constructor(
        private val installer: UpdateInstaller,
        private val remoteConfig: RemoteConfigRepository,
    ) : ViewModel() {
        val config: StateFlow<RemoteConfig> = remoteConfig.state
        val update: StateFlow<UpdateState> = installer.state

        private val mutableCheck = MutableStateFlow(UpdateCheck.IDLE)
        val check: StateFlow<UpdateCheck> = mutableCheck.asStateFlow()
        private val mutableCompletedVersion = MutableStateFlow(installer.consumeCompletedVersion())
        val completedVersion: StateFlow<String?> = mutableCompletedVersion.asStateFlow()

        fun available(config: RemoteConfig): Boolean = installer.updateAvailable(config)

        fun install(url: String) {
            viewModelScope.launch { installer.downloadAndInstall(url) }
        }

        @Suppress("TooGenericExceptionCaught")
        fun checkForUpdates() {
            if (mutableCheck.value == UpdateCheck.CHECKING) return
            mutableCheck.value = UpdateCheck.CHECKING
            viewModelScope.launch {
                try {
                    mutableCheck.value = if (remoteConfig.refresh()) UpdateCheck.CHECKED else UpdateCheck.FAILED
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (_: Exception) {
                    mutableCheck.value = UpdateCheck.FAILED
                } finally {
                    if (mutableCheck.value == UpdateCheck.CHECKING) mutableCheck.value = UpdateCheck.IDLE
                }
            }
        }

        fun dismissCompletedUpdate() {
            mutableCompletedVersion.value = null
        }
    }
