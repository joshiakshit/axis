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

        // Manual "check for updates": true while re-fetching remote config, and true once a check has completed
        // (so the UI can show "up to date" instead of the initial "check" button).
        private val mutableChecking = MutableStateFlow(false)
        val checking: StateFlow<Boolean> = mutableChecking.asStateFlow()
        private val mutableChecked = MutableStateFlow(false)
        val checked: StateFlow<Boolean> = mutableChecked.asStateFlow()

        fun available(config: RemoteConfig): Boolean = installer.updateAvailable(config)

        fun install(url: String) {
            viewModelScope.launch { installer.downloadAndInstall(url) }
        }

        fun checkForUpdates() {
            viewModelScope.launch {
                mutableChecking.value = true
                remoteConfig.refresh()
                mutableChecking.value = false
                mutableChecked.value = true
            }
        }

        fun clearError() = installer.clearError()
    }
