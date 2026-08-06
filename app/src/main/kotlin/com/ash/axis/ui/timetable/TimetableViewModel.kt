package com.ash.axis.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.SELECTED_SEMESTER_CLASS_KEY
import com.ash.axis.data.repository.SELECTED_SEMESTER_YEAR_KEY
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.TimetableSlot
import com.ash.axis.domain.usecase.TimetableUseCase
import com.ash.axis.ui.ErrorText
import com.ash.core.network.NetworkMonitor
import com.ash.core.storage.PreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DisplaySlot(
    val slot: TimetableSlot,
    val displayName: String,
    val progress: Float?,
    val isSubstitution: Boolean = false,
    val originalTeacher: String? = null,
    val substituteTeacher: String? = null,
)

data class TimetableDay(
    val dayName: String,
    val dayOfMonth: Int = 0,
    val slots: ImmutableList<DisplaySlot> = persistentListOf(),
)

data class TimetableUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val weekStart: LocalDate = LocalDate.now().with(java.time.DayOfWeek.MONDAY),
    val weekEnd: LocalDate = LocalDate.now().with(java.time.DayOfWeek.MONDAY).plusDays(6),
    val weekLabel: String = "",
    val days: ImmutableList<TimetableDay> = persistentListOf(),
    val todayIndex: Int = 0,
    val isRefreshing: Boolean = false,
    val isCurrentWeek: Boolean = true,
    val isOffline: Boolean = false,
)

@HiltViewModel
@Suppress("TooGenericExceptionCaught")
class TimetableViewModel
    @Inject
    constructor(
        private val timetableRepo: TimetableRepository,
        private val attendanceRepo: AttendanceRepository,
        private val authRepository: AuthRepository,
        private val timetableUseCase: TimetableUseCase,
        private val preferencesStore: PreferencesStore,
        private val networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        private val _state = MutableStateFlow(TimetableUiState())
        val state: StateFlow<TimetableUiState> = _state.asStateFlow()

        private val dayOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        private var rawTimetable: Map<String, List<TimetableSlot>> = emptyMap()

        init {
            val (start, end) = timetableUseCase.getCurrentWeekRange()
            _state.update { it.copy(weekStart = start, weekEnd = end, weekLabel = timetableUseCase.formatDateRange(start, end)) }
            load(forceRefresh = false)
            observeSemesterSelection()
            startProgressTicker()
        }

        fun refresh() = load(forceRefresh = true)

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

        fun shiftWeek(delta: Int) {
            val (newStart, newEnd) = timetableUseCase.shiftWeek(_state.value.weekStart, delta)
            val (curStart, _) = timetableUseCase.getCurrentWeekRange()
            _state.update {
                it.copy(
                    weekStart = newStart,
                    weekEnd = newEnd,
                    weekLabel = timetableUseCase.formatDateRange(newStart, newEnd),
                    isCurrentWeek = newStart == curStart,
                )
            }
            load(forceRefresh = false)
        }

        fun resetToCurrentWeek() {
            val (start, end) = timetableUseCase.getCurrentWeekRange()
            if (_state.value.weekStart == start) return
            _state.update {
                it.copy(
                    weekStart = start,
                    weekEnd = end,
                    weekLabel = timetableUseCase.formatDateRange(start, end),
                    isCurrentWeek = true,
                )
            }
            load(forceRefresh = false)
        }

        @Suppress("MagicNumber")
        private fun startProgressTicker() {
            viewModelScope.launch {
                while (true) {
                    delay(60_000)
                    if (rawTimetable.isNotEmpty() && _state.value.isCurrentWeek) {
                        _state.update { it.copy(days = buildDisplayDays(rawTimetable)) }
                    }
                }
            }
        }

        @Suppress("LongMethod", "CyclomaticComplexMethod")
        private fun load(forceRefresh: Boolean) {
            val snapshot = _state.value
            val start = snapshot.weekStart
            val end = snapshot.weekEnd

            viewModelScope.launch {
                if (start != _state.value.weekStart) return@launch

                val user = authRepository.getUserInfo()

                var hadCache = false
                if (!forceRefresh && user != null) {
                    val peek = timetableRepo.peekTimetable(user.admno, start.toString(), end.toString())
                    if (peek != null) {
                        hadCache = true
                        if (start != _state.value.weekStart) return@launch
                        applyTimetable(peek.data, isRefreshing = peek.isStale)
                        if (!peek.isStale) return@launch
                    }
                }

                _state.update {
                    it.copy(isLoading = !hadCache && it.days.isEmpty(), isRefreshing = hadCache || forceRefresh)
                }
                try {
                    val resolvedUser = user ?: authRepository.getUserInfo() ?: error("Not logged in")
                    val selectedYearId = preferencesStore.getUserString(SELECTED_SEMESTER_YEAR_KEY).first()
                    val selectedClassId = preferencesStore.getUserString(SELECTED_SEMESTER_CLASS_KEY).first()
                    val semester =
                        attendanceRepo.getPreferredSemester(
                            resolvedUser.admno,
                            resolvedUser.brId,
                            selectedYearId,
                            selectedClassId,
                            forceRefresh,
                        )
                    val guessedYear = semester.yearId.ifBlank { timetableUseCase.getAcadYear() }
                    val timetable = fetchTimetable(resolvedUser, guessedYear, start, end, forceRefresh || hadCache)
                    if (start != _state.value.weekStart) return@launch
                    applyTimetable(timetable, isRefreshing = false)
                } catch (e: Exception) {
                    if (start != _state.value.weekStart) return@launch
                    val offline = networkMonitor.isOnline.first().not()
                    if (hadCache) {
                        _state.update { it.copy(isLoading = false, isRefreshing = false, isOffline = offline) }
                    } else {
                        _state.update {
                            it.copy(isLoading = false, isRefreshing = false, isOffline = offline, error = ErrorText.forData(e))
                        }
                    }
                }
            }
        }

        private suspend fun fetchTimetable(
            user: com.ash.axis.domain.model.UserInfo,
            guessedYear: String,
            start: LocalDate,
            end: LocalDate,
            force: Boolean,
        ): Map<String, List<TimetableSlot>> =
            try {
                timetableRepo.getTimetable(user.admno, user.brId, guessedYear, start.toString(), end.toString(), force)
            } catch (firstError: Exception) {
                val latestYear = attendanceRepo.getAcadYears(user.admno, user.brId, force).firstOrNull()?.id
                if (latestYear.isNullOrBlank() || latestYear == guessedYear) throw firstError
                timetableRepo.getTimetable(user.admno, user.brId, latestYear, start.toString(), end.toString(), force)
            }

        private suspend fun applyTimetable(
            timetable: Map<String, List<TimetableSlot>>,
            isRefreshing: Boolean,
        ) {
            rawTimetable = timetable
            val days = buildDisplayDays(timetable)
            val todayName =
                LocalDate.now().dayOfWeek.getDisplayName(
                    java.time.format.TextStyle.SHORT,
                    java.util.Locale.ENGLISH,
                )
            val todayIdx = days.indexOfFirst { it.dayName == todayName }.coerceAtLeast(0)
            val offline = networkMonitor.isOnline.first().not()
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = isRefreshing,
                    error = null,
                    days = days,
                    todayIndex = todayIdx,
                    isOffline = offline,
                )
            }
        }

        private fun buildDisplayDays(timetable: Map<String, List<TimetableSlot>>): ImmutableList<TimetableDay> {
            val weekStart = _state.value.weekStart
            val today = LocalDate.now()
            return dayOrder.map { day ->
                val slots = timetable[day] ?: emptyList()
                val dayIndex = dayOrder.indexOf(day)
                val date = weekStart.plusDays(dayIndex.toLong())
                val isToday = date == today
                TimetableDay(
                    dayName = day,
                    dayOfMonth = date.dayOfMonth,
                    slots =
                        timetableUseCase.sortSlotsByTime(slots).map { slot ->
                            val isSub = timetableUseCase.isSubstitution(slot)
                            DisplaySlot(
                                slot = slot,
                                displayName = timetableUseCase.displaySubjectName(slot),
                                progress = timetableUseCase.currentSlotProgress(slot, isToday),
                                isSubstitution = isSub,
                                originalTeacher = if (isSub) timetableUseCase.originalTeacher(slot) else null,
                                substituteTeacher = if (isSub) timetableUseCase.substituteTeacher(slot) else null,
                            )
                        }.toImmutableList(),
                )
            }.toImmutableList()
        }
    }
