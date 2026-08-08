package com.ash.axis.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.config.RemoteConfig
import com.ash.axis.data.config.RemoteConfigRepository
import com.ash.axis.data.update.UpdateInstaller
import com.ash.axis.data.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel
    @Inject
    constructor(
        private val installer: UpdateInstaller,
        remoteConfig: RemoteConfigRepository,
    ) : ViewModel() {
        val config: StateFlow<RemoteConfig> = remoteConfig.state
        val update: StateFlow<UpdateState> = installer.state

        fun available(config: RemoteConfig): Boolean = installer.updateAvailable(config)

        fun install(url: String) {
            viewModelScope.launch { installer.downloadAndInstall(url) }
        }

        fun clearError() = installer.clearError()
    }
