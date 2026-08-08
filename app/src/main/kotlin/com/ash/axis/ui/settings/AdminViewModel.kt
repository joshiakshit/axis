package com.ash.axis.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.session.AdminUser
import com.ash.axis.data.session.AxisSessionRepository
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
    val busyAdmno: String? = null,
    val error: String? = null,
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
                    mutableState.update { it.copy(loading = false, users = users) }
                } catch (e: Exception) {
                    mutableState.update { it.copy(loading = false, error = ErrorText.forData(e)) }
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        fun setStatus(
            admno: String,
            allow: Boolean,
        ) {
            viewModelScope.launch {
                mutableState.update { it.copy(busyAdmno = admno, error = null) }
                try {
                    val updated = repository.setUserStatus(admno, allow)
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
    }
