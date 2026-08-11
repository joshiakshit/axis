package com.ash.axis.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.config.RemoteConfig
import com.ash.axis.data.session.AdminUser
import com.ash.axis.data.session.AxisSessionRepository
import com.ash.axis.data.session.ConfigPatch
import com.ash.axis.data.session.HealthResponse
import com.ash.axis.data.session.UserAction
import com.ash.axis.ui.ErrorText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminUiState(
    val loading: Boolean = false,
    val users: List<AdminUser> = emptyList(),
    val health: HealthResponse? = null,
    val busyAdmno: String? = null,
    val error: String? = null,
    val config: RemoteConfig? = null,
    val savingConfig: Boolean = false,
    val configMessage: String? = null,
)

@HiltViewModel
class AdminViewModel
    @Inject
    constructor(
        private val repository: AxisSessionRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(AdminUiState())
        val state: StateFlow<AdminUiState> = mutableState.asStateFlow()

        init {
            load()
        }

        @Suppress("TooGenericExceptionCaught")
        fun load() {
            viewModelScope.launch {
                mutableState.update { it.copy(loading = true, error = null) }
                try {
                    val users = repository.listUsers()
                    val config = repository.getConfig()
                    val health = repository.getHealth()
                    mutableState.update {
                        it.copy(loading = false, users = users, config = config ?: it.config, health = health ?: it.health)
                    }
                } catch (e: Exception) {
                    mutableState.update { it.copy(loading = false, error = ErrorText.forData(e)) }
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        fun act(
            admno: String,
            action: UserAction,
        ) {
            viewModelScope.launch {
                mutableState.update { it.copy(busyAdmno = admno, error = null) }
                try {
                    val updated = repository.setUserStatus(admno, action)
                    mutableState.update { s ->
                        s.copy(
                            busyAdmno = null,
                            users = if (updated == null) s.users else s.users.map { if (it.admno == admno) updated else it },
                        )
                    }
                } catch (e: Exception) {
                    mutableState.update { it.copy(busyAdmno = null, error = ErrorText.forData(e)) }
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        fun approveAll() {
            viewModelScope.launch {
                mutableState.update { it.copy(loading = true, error = null) }
                try {
                    repository.approveAll()
                    load()
                } catch (e: Exception) {
                    mutableState.update { it.copy(loading = false, error = ErrorText.forData(e)) }
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        fun saveConfig(patch: ConfigPatch) {
            viewModelScope.launch {
                mutableState.update { it.copy(savingConfig = true, configMessage = null, error = null) }
                try {
                    val updated = repository.putConfig(patch)
                    mutableState.update {
                        it.copy(savingConfig = false, config = updated ?: it.config, configMessage = "Saved")
                    }
                } catch (e: Exception) {
                    mutableState.update { it.copy(savingConfig = false, error = ErrorText.forData(e)) }
                }
            }
        }

        fun consumeConfigMessage() = mutableState.update { it.copy(configMessage = null) }

        // --- convenience wrappers for the redesigned Admin controls -------------------------------------

        fun setMinVersion(code: Int) = saveConfig(ConfigPatch(minSupportedVersionCode = code))

        fun setNotice(text: String) = saveConfig(ConfigPatch(notice = text))

        fun setKillSwitch(
            on: Boolean,
            message: String,
        ) = saveConfig(ConfigPatch(killSwitch = on, message = message))
    }
