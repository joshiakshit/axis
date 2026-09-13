package com.ash.axis.ui

import com.ash.axis.data.repository.CalendarRepository
import com.ash.axis.domain.model.CalendarEvent
import com.ash.axis.domain.model.UserInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

data class CalendarUiState(
    val entries: ImmutableList<CalendarEvent> = persistentListOf(),
    val isLoading: Boolean = true,
    val updatedAt: Long? = null,
    val fromCache: Boolean = false,
    val error: String? = null,
)

@Suppress("TooGenericExceptionCaught")
internal suspend fun CalendarRepository.loadState(
    user: UserInfo,
    month: LocalDate,
    forceRefresh: Boolean,
): CalendarUiState =
    try {
        val start = month.withDayOfMonth(1)
        val result = getCalendar(user, start, start.withDayOfMonth(start.lengthOfMonth()), forceRefresh)
        CalendarUiState(result.data.toImmutableList(), false, result.updatedAt, result.fromCache, result.error?.let(ErrorText::forData))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CalendarUiState(isLoading = false, error = ErrorText.forData(e))
    }
