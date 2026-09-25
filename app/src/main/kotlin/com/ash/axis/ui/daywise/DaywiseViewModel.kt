package com.ash.axis.ui.daywise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.DaywiseKey
import com.ash.axis.domain.model.DaywiseResponse
import com.ash.axis.domain.model.DaywiseSlot
import com.ash.axis.ui.ErrorText
import com.ash.axis.ui.academics.SemesterSelection
import com.ash.axis.ui.academics.selectedSemester
import com.ash.core.network.NetworkMonitor
import com.ash.core.storage.PreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class DaywiseDay(
    val date: LocalDate,
    val label: String,
    val slots: ImmutableList<DaywiseSlot> = persistentListOf(),
)

data class DaywiseUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val monthStart: LocalDate = LocalDate.now().withDayOfMonth(1),
    val monthEnd: LocalDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()),
    val monthLabel: String = "",
    val selectedDate: LocalDate = LocalDate.now(),
    val days: ImmutableList<DaywiseDay> = persistentListOf(),
    val isRefreshing: Boolean = false,
    val lastUpdated: Long? = null,
    val isOffline: Boolean = false,
    val hasData: Boolean = false,
    val semesterError: String? = null,
)

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DaywiseViewModel
    @Inject
    constructor(
        private val attendanceRepo: AttendanceRepository,
        private val coordinator: AcademicDataCoordinator,
        preferencesStore: PreferencesStore,
        networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        private val _state = MutableStateFlow(DaywiseUiState())
        val state: StateFlow<DaywiseUiState> = _state.asStateFlow()
        private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
        private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
        private val semesterRetry = MutableStateFlow(0L)
        private val semester = selectedSemester(coordinator, attendanceRepo, preferencesStore, retry = semesterRetry)
        private val selectedSemesterState = MutableStateFlow(SemesterSelection())
        private val manualError = MutableStateFlow<Throwable?>(null)
        private val visible = MutableStateFlow(false)

        init {
            setMonth(LocalDate.now(), LocalDate.now())
            viewModelScope.launch {
                coordinator.activeContext.collect { context ->
                    _state.update {
                        DaywiseUiState(
                            monthStart = it.monthStart,
                            monthEnd = it.monthEnd,
                            monthLabel = it.monthLabel,
                            selectedDate = it.selectedDate,
                        )
                    }
                    if (context != null && visible.value) demand()
                }
            }
            viewModelScope.launch {
                semester.collect { selection ->
                    val previous = selectedSemesterState.value
                    selectedSemesterState.value = selection
                    if (selection.context != previous.context ||
                        selection.option?.yearId != previous.option?.yearId
                    ) {
                        _state.update {
                            it.copy(days = persistentListOf(), hasData = false, lastUpdated = null, isLoading = true, error = null)
                        }
                    }
                    if (visible.value && selection.option != previous.option) demand()
                }
            }
            viewModelScope.launch {
                val binding =
                    combine(coordinator.activeContext, selectedSemesterState, _state) { context, selection, screen ->
                        val option = selection.option
                        if (context == null || selection.context != context || option == null) {
                            (if (selection.context == context) selection else SemesterSelection(context)) to null
                        } else {
                            selection to
                                DaywiseKey(context, option.yearId, screen.monthStart.format(dateFmt), screen.monthEnd.format(dateFmt))
                        }
                    }.distinctUntilChanged().flatMapLatest { key ->
                        if (key.second == null) {
                            flowOf(key.first to AcademicSnapshot<DaywiseResponse>())
                        } else {
                            attendanceRepo.observeDaywise(key.second!!).map { key.first to it }
                        }
                    }
                combine(binding, coordinator.daywiseDemandError, manualError) { selected, demand, manual ->
                    DaywisePresentation(selected.first, selected.second, demand, manual)
                }.collect { applySnapshot(it) }
            }
            viewModelScope.launch { networkMonitor.isOnline.collect { online -> _state.update { it.copy(isOffline = !online) } } }
        }

        fun onPageVisibilityChanged(isVisible: Boolean) {
            visible.value = isVisible
            if (isVisible) {
                if (_state.value.semesterError != null) semesterRetry.value++ else demand()
            }
        }

        @Suppress("TooGenericExceptionCaught")
        fun refresh() {
            val option = selectedSemesterState.value.option
            if (option == null) {
                semesterRetry.value++
                return
            }
            val screen = _state.value
            viewModelScope.launch {
                manualError.value = null
                try {
                    coordinator.refreshDaywise(option.yearId, screen.monthStart.format(dateFmt), screen.monthEnd.format(dateFmt))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    manualError.value = error
                }
            }
        }

        fun shiftMonth(delta: Int) {
            val target = _state.value.monthStart.plusMonths(delta.toLong())
            val selected = target.withDayOfMonth(target.lengthOfMonth().coerceAtMost(_state.value.selectedDate.dayOfMonth))
            setMonth(target, selected)
            demand()
        }

        fun selectDate(date: LocalDate) {
            _state.update { it.copy(selectedDate = date) }
        }

        private fun demand() {
            val selection = selectedSemesterState.value
            val option = selection.option ?: return
            if (!visible.value || coordinator.activeContext.value != selection.context) return
            val screen = _state.value
            viewModelScope.launch {
                coordinator.daywiseVisible(option.yearId, screen.monthStart.format(dateFmt), screen.monthEnd.format(dateFmt))
            }
        }

        private fun setMonth(
            date: LocalDate,
            selectedDate: LocalDate,
        ) {
            val start = date.withDayOfMonth(1)
            val end = start.withDayOfMonth(start.lengthOfMonth())
            _state.update {
                it.copy(
                    monthStart = start,
                    monthEnd = end,
                    monthLabel = start.format(monthFmt),
                    selectedDate = selectedDate.coerceIn(start, end),
                    days = persistentListOf(),
                    hasData = false,
                    lastUpdated = null,
                    isLoading = true,
                    isRefreshing = false,
                    error = null,
                )
            }
        }

        private fun applySnapshot(presentation: DaywisePresentation) {
            val snapshot = presentation.snapshot
            val error = presentation.selection.error ?: snapshot.error ?: presentation.demandError ?: presentation.manualError
            val message = error?.let(ErrorText::forData)
            val response = snapshot.data
            if (response == null) {
                _state.update {
                    it.copy(
                        days = persistentListOf(),
                        hasData = false,
                        isLoading = error == null,
                        isRefreshing = snapshot.refreshing,
                        error = message,
                        semesterError = presentation.selection.error?.let(ErrorText::forData),
                        lastUpdated = null,
                    )
                }
                return
            }
            val days =
                response.dateArray.entries.mapNotNull { (key, text) ->
                    val date = runCatching { LocalDate.parse(text, dateFmt) }.getOrNull() ?: return@mapNotNull null
                    val slots =
                        response.attendanceArray[key]?.values.orEmpty()
                            .filter { it.isPresent != null }.sortedBy { it.fromTime }
                    DaywiseDay(
                        date,
                        "${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}, " +
                            date.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)),
                        slots.toImmutableList(),
                    )
                }.sortedBy { it.date }.toImmutableList()
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = snapshot.refreshing,
                    error = message,
                    semesterError = presentation.selection.error?.let(ErrorText::forData),
                    days = days,
                    lastUpdated = snapshot.updatedAtMillis,
                    hasData = true,
                )
            }
        }

        private data class DaywisePresentation(
            val selection: SemesterSelection,
            val snapshot: AcademicSnapshot<DaywiseResponse>,
            val demandError: Throwable?,
            val manualError: Throwable?,
        )
    }
