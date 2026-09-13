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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val updatedAt: Long? = null,
    val fromCache: Boolean = false,
    val readError: String? = null,
) {
    val unreadCount: Int get() = notifications.count { !it.read }
}

@HiltViewModel
@Suppress("TooGenericExceptionCaught")
class NotificationsViewModel
    @Inject
    constructor(
        private val notificationRepo: NotificationRepository,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(NotificationsUiState())
        val state: StateFlow<NotificationsUiState> = _state.asStateFlow()
        private var loadJob: Job? = null
        private var ownerId: String? = null

        init {
            load(forceRefresh = false)
        }

        fun refresh() = load(forceRefresh = true)

        fun sync() = load(forceRefresh = false)

        fun markAsRead(notification: AppNotification) {
            val owner = ownerId ?: return
            viewModelScope.launch {
                try {
                    notificationRepo.markAsRead(owner, notification)
                    _state.update { state ->
                        state.copy(
                            readError = null,
                            notifications =
                                state.notifications.map {
                                    if (it.readKey == notification.readKey) it.copy(read = true) else it
                                }.toImmutableList(),
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _state.update { it.copy(readError = "Could not save read status. Try again.") }
                }
            }
        }

        private fun load(forceRefresh: Boolean) {
            if (loadJob?.isActive == true) return
            loadJob =
                viewModelScope.launch {
                    _state.update { it.copy(isRefreshing = forceRefresh) }
                    try {
                        val user = authRepository.getUserInfo() ?: error("Not logged in")
                        val owner = "${user.clientId}:${user.admno}"
                        ownerId = owner
                        val result = notificationRepo.getNotifications(owner, forceRefresh)
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = result.error?.let(ErrorText::forData),
                                notifications = result.data.toImmutableList(),
                                updatedAt = result.updatedAt,
                                fromCache = result.fromCache,
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = ErrorText.forData(e),
                                fromCache = it.updatedAt != null,
                            )
                        }
                    }
                }
        }
    }
