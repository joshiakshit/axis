package com.ash.axis.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.NotificationRepository
import com.ash.axis.domain.model.AppNotification
import com.ash.axis.ui.ErrorText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val notifications: ImmutableList<AppNotification> = persistentListOf(),
)

@HiltViewModel
class NotificationsViewModel
    @Inject
    constructor(
        private val notificationRepo: NotificationRepository,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(NotificationsUiState())
        val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

        init {
            load(forceRefresh = false)
        }

        fun refresh() {
            _state.update { it.copy(isRefreshing = true) }
            load(forceRefresh = true)
        }

        @Suppress("TooGenericExceptionCaught")
        private fun load(forceRefresh: Boolean) {
            viewModelScope.launch {
                try {
                    val user = authRepository.getUserInfo() ?: error("Not logged in")
                    val list = notificationRepo.getNotifications(user.admno, forceRefresh)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                            notifications = list.toImmutableList(),
                        )
                    }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = if (it.notifications.isEmpty()) ErrorText.forData(e) else it.error,
                        )
                    }
                }
            }
        }
    }
