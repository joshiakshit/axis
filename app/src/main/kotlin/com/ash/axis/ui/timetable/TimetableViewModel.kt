package com.ash.axis.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.SELECTED_SEMESTER_CLASS_KEY
import com.ash.axis.data.repository.SELECTED_SEMESTER_YEAR_KEY
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.TimetableSlot
import com.ash.axis.domain.model.UserInfo
import com.ash.axis.domain.usecase.TimetableUseCase
import com.ash.axis.ui.ErrorText
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
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
)

data class TimetableDay(
    val dayName: String,
    val dayOfMonth: Int = 0,
    val slots: ImmutableList<DisplaySlot> = persistentListOf(),
)

data class TimetableUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    // Page 0 of the continuous day pager maps to this date; captured once at init so the UI and
    // ViewModel agree on the page<->date mapping for the whole session.
    val anchorDate: LocalDate = LocalDate.now(),
    // The day currently in view; drives the header label and which week gets fetched.
    val currentDate: LocalDate = LocalDate.now(),
    val dayCache: ImmutableMap<LocalDate, TimetableDay> = persistentMapOf(),
    val loadedWeeks: ImmutableSet<LocalDate> = persistentSetOf(),
    val loadingWeeks: ImmutableSet<LocalDate> = persistentSetOf(),
    val failedWeeks: ImmutableSet<LocalDate> = persistentSetOf(),
    // One-shot scroll request: the pager animates/jumps here, then calls consumeJump().
    val jumpTarget: LocalDate? = null,
    val isRefreshing: Boolean = false,
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

        // Raw (unprocessed) slots per week, kept so the progress ticker can rebuild today's "LIVE" bars.
        private val rawByWeek = mutableMapOf<LocalDate, Map<String, List<TimetableSlot>>>()

        init {
            val today = LocalDate.now()
            _state.update { it.copy(anchorDate = today, currentDate = today) }
            ensureWeekInternal(today, forceRefresh = false, isInitial = true)
            observeSemesterSelection()
            startProgressTicker()
        }

        // --- Public actions driven by the UI -------------------------------------------------

        // The pager settled on [date]: relabel the header and make sure its week (plus the immediate
        // neighbours, so crossing a week boundary is instant) is loaded.
        fun onDateShown(date: LocalDate) {
            if (_state.value.currentDate != date) _state.update { it.copy(currentDate = date) }
            ensureWeek(date)
            ensureWeek(date.plusDays(1))
            ensureWeek(date.minusDays(1))
        }

        // Teleport to any date (date picker / Today button / day-strip tap).
        fun jumpTo(date: LocalDate) {
            _state.update { it.copy(currentDate = date, jumpTarget = date) }
            ensureWeek(date)
        }

        fun consumeJump() {
            if (_state.value.jumpTarget != null) _state.update { it.copy(jumpTarget = null) }
        }

        fun ensureWeek(date: LocalDate) = ensureWeekInternal(date, forceRefresh = false, isInitial = false)

        fun retryWeek(date: LocalDate) = ensureWeekInternal(date, forceRefresh = true, isInitial = false)

        fun refresh() {
            val ws = weekStart(_state.value.currentDate)
            viewModelScope.launch {
                _state.update { it.copy(isRefreshing = true) }
                try {
                    loadWeek(ws, forceRefresh = true)
                } catch (e: Exception) {
                    val offline = networkMonitor.isOnline.first().not()
                    _state.update {
                        it.copy(
                            isOffline = offline,
                            failedWeeks = (it.failedWeeks + ws).toImmutableSet(),
                            error = if (it.dayCache.isEmpty()) ErrorText.forData(e) else it.error,
                        )
                    }
                } finally {
                    _state.update { it.copy(isRefreshing = false) }
                }
            }
        }

        // --- Week loading --------------------------------------------------------------------

        private fun ensureWeekInternal(
            date: LocalDate,
            forceRefresh: Boolean,
            isInitial: Boolean,
        ) {
            val ws = weekStart(date)
            val snapshot = _state.value
            if (!forceRefresh && (ws in snapshot.loadedWeeks || ws in snapshot.loadingWeeks)) {
                if (isInitial) _state.update { it.copy(isLoading = false) }
                return
            }
            // Reserve the week synchronously so concurrent callers (neighbour prefetch) don't double-fetch.
            _state.update {
                it.copy(
                    loadingWeeks = (it.loadingWeeks + ws).toImmutableSet(),
                    failedWeeks = (it.failedWeeks - ws).toImmutableSet(),
                )
            }

            viewModelScope.launch {
                try {
                    var showedCache = false
                    val user = authRepository.getUserInfo()
                    if (!forceRefresh && user != null) {
                        val peek =
                            runCatching {
                                timetableRepo.peekTimetable(user.admno, ws.toString(), ws.plusDays(6).toString())
                            }.getOrNull()
                        if (peek != null) {
                            applyWeek(ws, peek.data)
                            showedCache = true
                            if (!peek.isStale) return@launch
                        }
                    }
                    loadWeek(ws, forceRefresh = forceRefresh || showedCache)
                } catch (e: Exception) {
                    val offline = networkMonitor.isOnline.first().not()
                    _state.update {
                        it.copy(
                            loadingWeeks = (it.loadingWeeks - ws).toImmutableSet(),
                            failedWeeks = (it.failedWeeks + ws).toImmutableSet(),
                            isOffline = offline,
                            error = if (isInitial && it.dayCache.isEmpty()) ErrorText.forData(e) else it.error,
                        )
                    }
                } finally {
                    if (isInitial) _state.update { it.copy(isLoading = false) }
                }
            }
        }

        private suspend fun loadWeek(
            ws: LocalDate,
            forceRefresh: Boolean,
        ) {
            val user = authRepository.getUserInfo() ?: error("Not logged in")
            val selectedYearId = preferencesStore.getUserString(SELECTED_SEMESTER_YEAR_KEY).first()
            val selectedClassId = preferencesStore.getUserString(SELECTED_SEMESTER_CLASS_KEY).first()
            val semester =
                attendanceRepo.getPreferredSemester(user.admno, user.brId, selectedYearId, selectedClassId, forceRefresh)
            val guessedYear = semester.yearId.ifBlank { timetableUseCase.getAcadYear() }
            val timetable = fetchTimetable(user, guessedYear, ws, ws.plusDays(6), forceRefresh)
            applyWeek(ws, timetable)
        }

        private suspend fun fetchTimetable(
            user: UserInfo,
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

        private suspend fun applyWeek(
            ws: LocalDate,
            timetable: Map<String, List<TimetableSlot>>,
        ) {
            rawByWeek[ws] = timetable
            val newDays =
                (0..6).associate { i ->
                    val date = ws.plusDays(i.toLong())
                    date to buildDay(date, dayOrder[i], timetable[dayOrder[i]] ?: emptyList())
                }
            val offline = networkMonitor.isOnline.first().not()
            _state.update {
                it.copy(
                    dayCache = (it.dayCache + newDays).toImmutableMap(),
                    loadedWeeks = (it.loadedWeeks + ws).toImmutableSet(),
                    loadingWeeks = (it.loadingWeeks - ws).toImmutableSet(),
                    failedWeeks = (it.failedWeeks - ws).toImmutableSet(),
                    isOffline = offline,
                    error = null,
                )
            }
        }

        private fun buildDay(
            date: LocalDate,
            dayName: String,
            slots: List<TimetableSlot>,
        ): TimetableDay {
            val isToday = date == LocalDate.now()
            return TimetableDay(
                dayName = dayName,
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
        }

        private fun observeSemesterSelection() {
            viewModelScope.launch {
                combine(
                    preferencesStore.getUserString(SELECTED_SEMESTER_YEAR_KEY),
                    preferencesStore.getUserString(SELECTED_SEMESTER_CLASS_KEY),
                ) { yearId, classId -> yearId to classId }
                    .drop(1)
                    .collect {
                        rawByWeek.clear()
                        _state.update {
                            it.copy(
                                dayCache = persistentMapOf(),
                                loadedWeeks = persistentSetOf(),
                                loadingWeeks = persistentSetOf(),
                                failedWeeks = persistentSetOf(),
                            )
                        }
                        ensureWeek(_state.value.currentDate)
                    }
            }
        }

        @Suppress("MagicNumber")
        private fun startProgressTicker() {
            viewModelScope.launch {
                while (true) {
                    delay(60_000)
                    val today = LocalDate.now()
                    val ws = weekStart(today)
                    val raw = rawByWeek[ws]
                    if (raw != null && ws in _state.value.loadedWeeks) {
                        val dayName = dayOrder[(today.dayOfWeek.value - 1).coerceIn(0, 6)]
                        val rebuilt = buildDay(today, dayName, raw[dayName] ?: emptyList())
                        _state.update { it.copy(dayCache = (it.dayCache + (today to rebuilt)).toImmutableMap()) }
                    }
                }
            }
        }

        private fun weekStart(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)
    }
