package com.ash.axis.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.export.ExportKeys
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.CalendarRepository
import com.ash.axis.data.repository.TimetableKey
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.TimetableSlot
import com.ash.axis.domain.usecase.TimetableUseCase
import com.ash.axis.ui.CalendarUiState
import com.ash.axis.ui.ErrorText
import com.ash.axis.ui.loadState
import com.ash.core.network.NetworkMonitor
import com.ash.core.storage.PreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

data class DisplaySlot(
    val slot: TimetableSlot,
    val displayName: String,
    val progress: Float?,
    val isSubstitution: Boolean = false,
    val originalTeacher: String? = null,
    val substituteTeacher: String? = null,
    val teacherName: String? = null,
)

sealed interface TimetableItem {
    data class Slot(val display: DisplaySlot) : TimetableItem

    data class Break(val durationMinutes: Int, val startTime: String, val endTime: String) : TimetableItem
}

data class TimetableDay(
    val dayName: String,
    val dayOfMonth: Int = 0,
    val items: ImmutableList<TimetableItem> = persistentListOf(),
    val holiday: String? = null,
)

data class TimetableUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    // Keep the page-to-date mapping fixed for the session.
    val anchorDate: LocalDate = LocalDate.now(),
    val currentDate: LocalDate = LocalDate.now(),
    val dayCache: ImmutableMap<LocalDate, TimetableDay> = persistentMapOf(),
    val loadedWeeks: ImmutableSet<LocalDate> = persistentSetOf(),
    val loadingWeeks: ImmutableSet<LocalDate> = persistentSetOf(),
    val failedWeeks: ImmutableSet<LocalDate> = persistentSetOf(),
    // The pager clears this request with consumeJump() after scrolling.
    val jumpTarget: LocalDate? = null,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val calendar: CalendarUiState = CalendarUiState(),
)

@HiltViewModel
@Suppress("TooGenericExceptionCaught")
class TimetableViewModel
    @Inject
    constructor(
        private val timetableRepo: TimetableRepository,
        private val coordinator: AcademicDataCoordinator,
        private val authRepository: AuthRepository,
        private val calendarRepo: CalendarRepository,
        private val timetableUseCase: TimetableUseCase,
        private val preferencesStore: PreferencesStore,
        private val networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        private val _state = MutableStateFlow(TimetableUiState())
        val state: StateFlow<TimetableUiState> = _state.asStateFlow()

        private val dayOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        private val weekJobs = mutableMapOf<LocalDate, Job>()
        private var boundContext = coordinator.activeContext.value
        private var calendarJob: Job? = null
        private var calendarMonth: LocalDate? = null

        init {
            val today = LocalDate.now()
            _state.update { it.copy(anchorDate = today, currentDate = today) }
            viewModelScope.launch {
                coordinator.activeContext.collectLatest { context ->
                    if (context == boundContext) return@collectLatest
                    boundContext = context
                    weekJobs.values.forEach { it.cancel() }
                    weekJobs.clear()
                    _state.update {
                        it.copy(
                            dayCache = persistentMapOf(),
                            loadedWeeks = persistentSetOf(),
                            loadingWeeks = persistentSetOf(),
                            failedWeeks = persistentSetOf(),
                            isLoading = context != null,
                            error = null,
                        )
                    }
                    if (context != null) ensureWeek(_state.value.currentDate)
                }
            }
            ensureWeek(today)
            startProgressTicker()
            loadCalendar()
            viewModelScope.launch { networkMonitor.isOnline.collect { online -> _state.update { it.copy(isOffline = !online) } } }
            viewModelScope.launch {
                coordinator.timetableDemandError.collect { error ->
                    if (error != null && _state.value.dayCache.isEmpty()) {
                        _state.update { it.copy(isLoading = false, error = ErrorText.forData(error)) }
                    }
                }
            }
        }

        fun onDateShown(date: LocalDate) {
            if (_state.value.currentDate != date) _state.update { it.copy(currentDate = date) }
            loadCalendar()
            persistViewDate(date)
            ensureWeek(date)
        }

        fun jumpTo(date: LocalDate) {
            _state.update { it.copy(currentDate = date, jumpTarget = date) }
            loadCalendar()
            persistViewDate(date)
            ensureWeek(date)
        }

        private fun persistViewDate(date: LocalDate) {
            viewModelScope.launch { preferencesStore.putUserString(ExportKeys.TIMETABLE_VIEW_DATE, date.toString()) }
        }

        fun consumeJump() {
            if (_state.value.jumpTarget != null) _state.update { it.copy(jumpTarget = null) }
        }

        fun ensureWeek(date: LocalDate) = bindWeek(date, retry = false)

        fun retryWeek(date: LocalDate) = bindWeek(date, retry = true)

        fun refresh() {
            loadCalendar(forceRefresh = true)
            viewModelScope.launch {
                try {
                    coordinator.refreshTimetable()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    _state.update { it.copy(isRefreshing = false, error = ErrorText.forData(error)) }
                }
            }
        }

        @Suppress("CyclomaticComplexMethod", "LongMethod")
        private fun bindWeek(
            date: LocalDate,
            retry: Boolean,
        ) {
            val ws = weekStart(date)
            weekJobs.keys.filter {
                it != ws && (it !in _state.value.loadedWeeks || it.isBefore(ws.minusWeeks(1)) || it.isAfter(ws.plusWeeks(1)))
            }
                .forEach { old -> weekJobs.remove(old)?.cancel() }
            _state.update { current ->
                val retained = current.loadedWeeks.filter { it in weekJobs.keys || it == ws }.toSet()
                current.copy(
                    dayCache = current.dayCache.filterKeys { weekStart(it) in retained }.toImmutableMap(),
                    loadedWeeks = retained.toImmutableSet(),
                )
            }
            if (weekJobs[ws]?.isActive == true && !retry) return
            weekJobs[ws]?.cancel()
            weekJobs[ws] =
                viewModelScope.launch {
                    try {
                        val context = coordinator.activeContext.value ?: return@launch
                        val key = TimetableKey(context, ws.toString(), ws.plusDays(6).toString())
                        val snapshot = timetableRepo.observeWeek(key)
                        launch {
                            if (retry) {
                                coordinator.refreshTimetable()
                            } else {
                                coordinator.timetableVisible(ws)
                            }
                        }
                        snapshot.collect { result ->
                            if (coordinator.activeContext.value != context) return@collect
                            val days =
                                result.data?.let { data ->
                                    (0..6).associate { i ->
                                        val day = ws.plusDays(i.toLong())
                                        val slots = data.dated?.get(day.toString()) ?: data.weekly[dayOrder[i]].orEmpty()
                                        day to buildDay(day, dayOrder[i], slots)
                                    }
                                }
                            _state.update { current ->
                                val retainedDays = current.dayCache.filterKeys { weekStart(it) != ws }
                                current.copy(
                                    dayCache = (retainedDays + days.orEmpty()).toImmutableMap(),
                                    loadedWeeks =
                                        if (days == null) {
                                            (current.loadedWeeks - ws).toImmutableSet()
                                        } else {
                                            (current.loadedWeeks + ws).toImmutableSet()
                                        },
                                    loadingWeeks =
                                        if (result.refreshing) {
                                            (current.loadingWeeks + ws).toImmutableSet()
                                        } else {
                                            (current.loadingWeeks - ws).toImmutableSet()
                                        },
                                    failedWeeks =
                                        if (result.error != null && days == null) {
                                            (current.failedWeeks + ws).toImmutableSet()
                                        } else {
                                            (current.failedWeeks - ws).toImmutableSet()
                                        },
                                    isLoading = false,
                                    isRefreshing = result.refreshing && ws == weekStart(current.currentDate),
                                    error = result.error?.let(ErrorText::forData),
                                )
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        _state.update { it.copy(isLoading = false, error = ErrorText.forData(error)) }
                    }
                }
        }

        @Suppress("CyclomaticComplexMethod")
        private fun buildDay(
            date: LocalDate,
            dayName: String,
            slots: List<TimetableSlot>,
        ): TimetableDay {
            val isToday = date == LocalDate.now()
            val sorted = timetableUseCase.sortSlotsByTime(slots)
            val items = mutableListOf<TimetableItem>()

            sorted.forEachIndexed { index, slot ->
                if (index > 0) {
                    val prevEnd = timetableUseCase.timeToMinutes(sorted[index - 1].toTime)
                    val curStart = timetableUseCase.timeToMinutes(slot.fromTime)
                    val gap = curStart - prevEnd
                    if (gap > 5) {
                        items.add(
                            TimetableItem.Break(
                                durationMinutes = gap,
                                startTime = sorted[index - 1].toTime,
                                endTime = slot.fromTime,
                            ),
                        )
                    }
                }
                val isSub = timetableUseCase.isSubstitution(slot)
                items.add(
                    TimetableItem.Slot(
                        DisplaySlot(
                            slot = slot,
                            displayName = timetableUseCase.displaySubjectName(slot),
                            progress = timetableUseCase.currentSlotProgress(slot, isToday),
                            isSubstitution = isSub,
                            originalTeacher = if (isSub) timetableUseCase.originalTeacher(slot) else null,
                            substituteTeacher = if (isSub) timetableUseCase.substituteTeacher(slot) else null,
                            teacherName = timetableUseCase.originalTeacher(slot),
                        ),
                    ),
                )
            }

            return TimetableDay(
                dayName = dayName,
                dayOfMonth = date.dayOfMonth,
                items = items.toImmutableList(),
            )
        }

        @Suppress("MagicNumber")
        private fun startProgressTicker() {
            viewModelScope.launch {
                while (true) {
                    delay(60_000)
                    val today = LocalDate.now()
                    val ws = weekStart(today)
                    val context = coordinator.activeContext.value
                    if (context != null && ws in _state.value.loadedWeeks) {
                        val data = timetableRepo.observeWeek(TimetableKey(context, ws.toString(), ws.plusDays(6).toString())).value.data
                        val dayName = dayOrder[(today.dayOfWeek.value - 1).coerceIn(0, 6)]
                        if (data != null) {
                            val rebuilt = buildDay(today, dayName, data.dated?.get(today.toString()) ?: data.weekly[dayName].orEmpty())
                            _state.update { it.copy(dayCache = (it.dayCache + (today to rebuilt)).toImmutableMap()) }
                        }
                    }
                }
            }
        }

        fun loadCalendar(forceRefresh: Boolean = false) {
            val month = _state.value.currentDate.withDayOfMonth(1)
            if (!forceRefresh && calendarMonth == month) return
            calendarMonth = month
            calendarJob?.cancel()
            _state.update { it.copy(calendar = CalendarUiState()) }
            calendarJob =
                viewModelScope.launch {
                    val user = authRepository.getUserInfo()
                    val calendar =
                        if (user == null) {
                            CalendarUiState(isLoading = false, error = "Sign in to load the calendar")
                        } else {
                            calendarRepo.loadState(user, month, forceRefresh)
                        }
                    _state.update { it.copy(calendar = calendar) }
                }
        }

        private fun weekStart(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)
    }
