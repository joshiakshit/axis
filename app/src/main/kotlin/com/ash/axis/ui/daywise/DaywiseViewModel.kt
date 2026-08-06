package com.ash.axis.ui.daywise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.SELECTED_SEMESTER_CLASS_KEY
import com.ash.axis.data.repository.SELECTED_SEMESTER_YEAR_KEY
import com.ash.axis.domain.model.DaywiseSlot
import com.ash.axis.ui.ErrorText
import com.ash.core.network.NetworkMonitor
import com.ash.core.storage.PreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
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
)

@HiltViewModel
@Suppress("TooGenericExceptionCaught")
class DaywiseViewModel
    @Inject
    constructor(
        private val attendanceRepo: AttendanceRepository,
        private val authRepository: AuthRepository,
        private val preferencesStore: PreferencesStore,
        private val networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        private val _state = MutableStateFlow(DaywiseUiState())
        val state: StateFlow<DaywiseUiState> = _state.asStateFlow()

        private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

        private var autoRefreshed = false

        init {
            setMonth(LocalDate.now(), selectedDate = LocalDate.now())
            load(forceRefresh = false)
            observeSemesterSelection()
        }

        fun refresh() = load(forceRefresh = true)

        fun onPageVisible() {
            if (autoRefreshed) return
            autoRefreshed = true
            viewModelScope.launch {
                if (networkMonitor.isOnline.first()) {
                    load(forceRefresh = true)
                }
            }
        }

        private fun observeSemesterSelection() {
            viewModelScope.launch {
                combine(
                    preferencesStore.getUserString(SELECTED_SEMESTER_YEAR_KEY),
                    preferencesStore.getUserString(SELECTED_SEMESTER_CLASS_KEY),
                ) { yearId, classId -> yearId to classId }
                    .drop(1)
                    .collect { load(forceRefresh = false) }
            }
        }

        fun shiftMonth(delta: Int) {
            val target = _state.value.monthStart.plusMonths(delta.toLong())
            val selected = target.withDayOfMonth(target.lengthOfMonth().coerceAtMost(_state.value.selectedDate.dayOfMonth))
            setMonth(target, selectedDate = selected)
            load(forceRefresh = false)
        }

        fun selectDate(date: LocalDate) {
            _state.update { it.copy(selectedDate = date) }
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
                )
            }
        }

        @Suppress("LongMethod")
        private fun load(forceRefresh: Boolean) {
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        isRefreshing = forceRefresh && it.days.isNotEmpty(),
                        isLoading = it.days.isEmpty(),
                    )
                }
                try {
                    val user = authRepository.getUserInfo() ?: error("Not logged in")
                    val yearId = preferencesStore.getUserString(SELECTED_SEMESTER_YEAR_KEY).first()
                    val classId = preferencesStore.getUserString(SELECTED_SEMESTER_CLASS_KEY).first()
                    val semester = attendanceRepo.getPreferredSemester(user.admno, user.brId, yearId, classId, forceRefresh)

                    val start = _state.value.monthStart
                    val end = _state.value.monthEnd

                    val response =
                        attendanceRepo.getDaywiseAttendance(
                            user.admno,
                            user.brId,
                            semester.yearId,
                            start.format(dateFmt),
                            end.format(dateFmt),
                            forceRefresh,
                        )

                    val days =
                        response.dateArray.entries
                            .mapNotNull { (key, dateStr) ->
                                val date =
                                    try {
                                        LocalDate.parse(dateStr, dateFmt)
                                    } catch (_: Exception) {
                                        null
                                    } ?: return@mapNotNull null

                                val slots =
                                    (response.attendanceArray[key]?.values?.toList() ?: emptyList())
                                        .filter { it.isPresent != null }
                                        .sortedBy { it.fromTime }
                                DaywiseDay(
                                    date = date,
                                    label =
                                        "${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}, " +
                                            date.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)),
                                    slots = slots.toImmutableList(),
                                )
                            }
                            .sortedBy { it.date }
                            .toImmutableList()

                    val offline = networkMonitor.isOnline.first().not()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                            days = days,
                            lastUpdated = if (forceRefresh) System.currentTimeMillis() else it.lastUpdated,
                            isOffline = offline,
                        )
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(isLoading = false, isRefreshing = false, error = ErrorText.forData(e)) }
                }
            }
        }
    }
