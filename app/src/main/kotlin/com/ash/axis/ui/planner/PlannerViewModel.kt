package com.ash.axis.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.SELECTED_SEMESTER_CLASS_KEY
import com.ash.axis.data.repository.SELECTED_SEMESTER_YEAR_KEY
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.TimetableSlot
import com.ash.axis.domain.model.UserInfo
import com.ash.axis.domain.usecase.AttendanceTone
import com.ash.axis.domain.usecase.AttendanceUseCase
import com.ash.axis.domain.usecase.DaySafety
import com.ash.axis.domain.usecase.ForecastRow
import com.ash.axis.domain.usecase.ForecastUseCase
import com.ash.axis.domain.usecase.PlannerSubject
import com.ash.axis.domain.usecase.PlannerUseCase
import com.ash.axis.domain.usecase.ProjectedSubject
import com.ash.axis.domain.usecase.SubjectAttendance
import com.ash.axis.domain.usecase.TimetableUseCase
import com.ash.axis.domain.usecase.TomorrowClass
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class PlannerUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val threshold: Int = 75,
    val overallPresent: Int = 0,
    val overallTotal: Int = 0,
    val subjects: ImmutableList<PlannerSubject> = persistentListOf(),
    val timetable: ImmutableMap<String, ImmutableList<TimetableSlot>> = persistentMapOf(),
    val daySafety: ImmutableList<DaySafety> = persistentListOf(),
    val forecast: ImmutableList<ForecastRow> = persistentListOf(),
    val selectedDates: ImmutableSet<LocalDate> = persistentSetOf(),
    val holidays: ImmutableSet<LocalDate> = persistentSetOf(),
    val holidayMode: Boolean = false,
    val anchorDate: LocalDate? = null,
    val projected: ImmutableList<ProjectedSubject> = persistentListOf(),
    val totalSpare: Int = 0,
    val tomorrowSlots: ImmutableList<TomorrowClass> = persistentListOf(),
    val isRefreshing: Boolean = false,
    val simulatorMonth: LocalDate = LocalDate.now().withDayOfMonth(1),
    val isOffline: Boolean = false,
)

@HiltViewModel
@Suppress("TooGenericExceptionCaught")
class PlannerViewModel
    @Inject
    constructor(
        private val attendanceRepo: AttendanceRepository,
        private val timetableRepo: TimetableRepository,
        private val authRepository: AuthRepository,
        private val attendanceUseCase: AttendanceUseCase,
        private val plannerUseCase: PlannerUseCase,
        private val forecastUseCase: ForecastUseCase,
        private val timetableUseCase: TimetableUseCase,
        private val preferencesStore: PreferencesStore,
        private val networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PlannerUiState())
        val state: StateFlow<PlannerUiState> = _state.asStateFlow()

        private val displayDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        private var loadJob: Job? = null
        private var cachedAcadYear: String = ""
        private var cachedSemesterEnd: LocalDate? = null
        private var dateTimetableCache: Map<LocalDate, List<TimetableSlot>> = emptyMap()
        private var dateTimetableRange: Pair<LocalDate, LocalDate>? = null

        init {
            load(forceRefresh = false)
            observePreferences()
        }

        fun refresh() = load(forceRefresh = true)

        private fun observePreferences() {
            viewModelScope.launch {
                combine(
                    preferencesStore.getUserBoolean("combined_attendance"),
                    preferencesStore.getUserInt("attendance_threshold", 75),
                    preferencesStore.getUserString("semester_end_date", ""),
                    preferencesStore.getUserString(SELECTED_SEMESTER_YEAR_KEY),
                    preferencesStore.getUserString(SELECTED_SEMESTER_CLASS_KEY),
                ) { combined, threshold, semEnd, yearId, classId ->
                    "$combined:$threshold:$semEnd:$yearId:$classId"
                }
                    .drop(1)
                    .collect { load(forceRefresh = false) }
            }
        }

        fun previewDate(date: LocalDate) {
            viewModelScope.launch {
                val base = _state.value.copy(anchorDate = date)
                _state.value = recomputeProjection(base)
            }
        }

        fun markAbsent(date: LocalDate) {
            viewModelScope.launch {
                val current = _state.value
                if (current.holidayMode) {
                    val newHolidays = current.holidays.toMutableSet()
                    if (date in newHolidays) newHolidays.remove(date) else newHolidays.add(date)
                    val base = current.copy(holidays = newHolidays.toImmutableSet())
                    _state.value = recomputeProjection(base)
                } else {
                    val newSelected = current.selectedDates.toMutableSet()
                    if (date in newSelected) newSelected.remove(date) else newSelected.add(date)
                    val newAnchor =
                        if (newSelected.isEmpty()) {
                            null
                        } else {
                            newSelected.maxOrNull()
                        }
                    val base = current.copy(selectedDates = newSelected.toImmutableSet(), anchorDate = newAnchor)
                    _state.value = recomputeProjection(base)
                }
            }
        }

        fun toggleHolidayMode() {
            _state.update { it.copy(holidayMode = !it.holidayMode) }
        }

        private suspend fun recomputeProjection(base: PlannerUiState): PlannerUiState {
            val today = LocalDate.now()
            val horizon =
                listOfNotNull(base.anchorDate, base.selectedDates.maxOrNull()).maxOrNull()
                    ?: return base.copy(projected = persistentListOf())

            val cachedRange = dateTimetableRange
            if (cachedRange == null || horizon > cachedRange.second) {
                fetchDateTimetable(today, horizon)
            }

            val projectionData =
                dateTimetableCache.filterKeys { it in today..horizon && it !in base.holidays }
            val projected =
                withContext(Dispatchers.Default) {
                    val raw =
                        plannerUseCase.computeProjected(
                            base.subjects,
                            base.selectedDates,
                            projectionData,
                            base.threshold,
                            cachedSemesterEnd,
                            base.timetable,
                            today,
                            includeNoAbsence = true,
                        )
                    val focusDate = base.anchorDate ?: base.selectedDates.maxOrNull()
                    val dayKey = focusDate?.let { DAY_NAMES[it.dayOfWeek] }
                    val daySlotCodes =
                        if (dayKey != null) {
                            (base.timetable[dayKey] ?: emptyList())
                                .map { it.subCode.uppercase().trim() }
                                .toSet()
                        } else {
                            emptySet()
                        }
                    raw.sortedWith(
                        compareByDescending<ProjectedSubject> { it.code.uppercase() in daySlotCodes }
                            .thenBy { it.delta },
                    ).toImmutableList()
                }
            return base.copy(projected = projected)
        }

        fun clearDates() {
            _state.update {
                it.copy(
                    selectedDates = persistentSetOf(),
                    holidays = persistentSetOf(),
                    anchorDate = null,
                    projected = persistentListOf(),
                )
            }
        }

        fun shiftSimulatorMonth(delta: Int) {
            _state.update { it.copy(simulatorMonth = it.simulatorMonth.plusMonths(delta.toLong())) }
        }

        @Suppress("LongMethod")
        private fun load(forceRefresh: Boolean) {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    _state.update { it.copy(isRefreshing = forceRefresh, isLoading = !forceRefresh && it.subjects.isEmpty()) }
                    try {
                        val threshold = preferencesStore.getUserInt("attendance_threshold", 75).first()
                        val combinedPref = preferencesStore.getUserBoolean("combined_attendance").first()
                        val endDateStr = preferencesStore.getUserString("semester_end_date", "").first()
                        cachedSemesterEnd =
                            endDateStr.takeIf { it.isNotBlank() }?.let {
                                runCatching { LocalDate.parse(it) }.getOrNull()
                            }
                        val user = authRepository.getUserInfo() ?: error("Not logged in")
                        val selectedYearId = preferencesStore.getUserString(SELECTED_SEMESTER_YEAR_KEY).first()
                        val selectedClassId = preferencesStore.getUserString(SELECTED_SEMESTER_CLASS_KEY).first()
                        val semester =
                            attendanceRepo.getPreferredSemester(
                                user.admno,
                                user.brId,
                                selectedYearId,
                                selectedClassId,
                                forceRefresh,
                            )
                        cachedAcadYear = semester.yearId
                        val (weekStart, weekEnd) = timetableUseCase.getCurrentWeekRange()

                        val (attendance, timetable) =
                            coroutineScope {
                                val attendanceDeferred =
                                    async {
                                        attendanceRepo.getAttendance(
                                            user.admno,
                                            user.brId,
                                            semester.classId,
                                            semester.yearId,
                                            forceRefresh,
                                        )
                                    }
                                val timetableDeferred =
                                    async {
                                        timetableRepo.getTimetable(
                                            user.admno,
                                            user.brId,
                                            semester.yearId,
                                            weekStart.toString(),
                                            weekEnd.toString(),
                                            forceRefresh,
                                        )
                                    }
                                attendanceDeferred.await() to timetableDeferred.await()
                            }

                        val rawAll =
                            attendance.table.values.map {
                                SubjectAttendance(it.subCode, it.subname, it.lecType, it.present, it.total, it.percent)
                            }
                        val rawSubjects = if (combinedPref) attendanceUseCase.combineSubjects(rawAll) else rawAll

                        val computed =
                            withContext(Dispatchers.Default) {
                                val plannerSubjects =
                                    plannerUseCase.buildPlannerSubjects(rawSubjects, timetable, threshold)
                                        .sortedWith(compareBy<PlannerSubject> { it.tone.ordinal }.thenBy { it.name })
                                val daySafety =
                                    displayDays.map { day ->
                                        plannerUseCase.analyzeDaySafety(day, plannerSubjects, timetable)
                                    }
                                val totalSpare =
                                    plannerSubjects
                                        .filter { it.tone != AttendanceTone.BAD }
                                        .sumOf { it.bunkable }
                                val forecast =
                                    forecastUseCase.buildForecast(rawSubjects, timetable, threshold, cachedSemesterEnd)
                                PlannerComputed(plannerSubjects, daySafety, totalSpare, forecast)
                            }

                        val today = LocalDate.now()
                        val tomorrow = today.plusDays(1)
                        val tomorrowDayKey = DAY_NAMES[tomorrow.dayOfWeek] ?: ""
                        val tomorrowClasses =
                            plannerUseCase.buildTomorrowClasses(
                                timetable[tomorrowDayKey] ?: emptyList(),
                                computed.subjects,
                                threshold,
                            )

                        val cacheEnd = today.plusWeeks(4)
                        primeDateTimetableCache(user, semester.yearId, today to cacheEnd, timetable, forceRefresh)

                        val offline = networkMonitor.isOnline.first().not()
                        val immutableTimetable =
                            timetable.mapValues { (_, v) -> v.toImmutableList() }.toImmutableMap()
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = null,
                                threshold = threshold,
                                overallPresent = attendance.endrow.present,
                                overallTotal = attendance.endrow.total,
                                subjects = computed.subjects.toImmutableList(),
                                timetable = immutableTimetable,
                                daySafety = computed.daySafety.toImmutableList(),
                                totalSpare = computed.totalSpare,
                                tomorrowSlots = tomorrowClasses.toImmutableList(),
                                forecast = computed.forecast.toImmutableList(),
                                selectedDates = persistentSetOf(),
                                anchorDate = null,
                                projected = persistentListOf(),
                                isOffline = offline,
                            )
                        }
                    } catch (e: Exception) {
                        val offline = networkMonitor.isOnline.first().not()
                        _state.update {
                            it.copy(isLoading = false, isRefreshing = false, error = ErrorText.forData(e), isOffline = offline)
                        }
                    }
                }
        }

        private suspend fun fetchDateTimetable(
            start: LocalDate,
            end: LocalDate,
        ) {
            val weekly = _state.value.timetable
            val apiData =
                try {
                    val user = authRepository.getUserInfo()
                    if (user != null) {
                        val acadYear = cachedAcadYear.ifBlank { timetableUseCase.getAcadYear() }
                        timetableRepo.getDateKeyedTimetable(
                            user.admno,
                            user.brId,
                            acadYear,
                            start.toString(),
                            end.toString(),
                        )
                    } else {
                        emptyMap()
                    }
                } catch (_: Exception) {
                    emptyMap()
                }
            dateTimetableCache = mergeWithWeeklyFallback(apiData, weekly, start, end)
            dateTimetableRange = start to end
        }

        private suspend fun primeDateTimetableCache(
            user: UserInfo,
            acadYear: String,
            range: Pair<LocalDate, LocalDate>,
            weekly: Map<String, List<TimetableSlot>>,
            forceRefresh: Boolean,
        ) {
            val (start, end) = range
            val apiData =
                runCatching {
                    timetableRepo.getDateKeyedTimetable(
                        user.admno,
                        user.brId,
                        acadYear,
                        start.toString(),
                        end.toString(),
                        forceRefresh,
                    )
                }.getOrDefault(emptyMap())

            dateTimetableCache = mergeWithWeeklyFallback(apiData, weekly, start, end)
            dateTimetableRange = start to end
        }

        private fun mergeWithWeeklyFallback(
            apiData: Map<LocalDate, List<TimetableSlot>>,
            weekly: Map<String, List<TimetableSlot>>,
            start: LocalDate,
            end: LocalDate,
        ): Map<LocalDate, List<TimetableSlot>> {
            val expanded = expandWeeklyToDateKeyed(weekly, start, end)
            if (apiData.isEmpty()) return expanded
            return expanded + apiData
        }

        private fun expandWeeklyToDateKeyed(
            weekly: Map<String, List<TimetableSlot>>,
            start: LocalDate,
            end: LocalDate,
        ): Map<LocalDate, List<TimetableSlot>> {
            val result = mutableMapOf<LocalDate, List<TimetableSlot>>()
            var date = start
            while (date <= end) {
                val dayName = DAY_NAMES[date.dayOfWeek] ?: ""
                val slots = weekly[dayName]
                if (!slots.isNullOrEmpty()) result[date] = slots
                date = date.plusDays(1)
            }
            return result
        }

        private data class PlannerComputed(
            val subjects: List<PlannerSubject>,
            val daySafety: List<DaySafety>,
            val totalSpare: Int,
            val forecast: List<ForecastRow>,
        )

        private companion object {
            val DAY_NAMES =
                mapOf(
                    java.time.DayOfWeek.MONDAY to "Mon",
                    java.time.DayOfWeek.TUESDAY to "Tue",
                    java.time.DayOfWeek.WEDNESDAY to "Wed",
                    java.time.DayOfWeek.THURSDAY to "Thu",
                    java.time.DayOfWeek.FRIDAY to "Fri",
                    java.time.DayOfWeek.SATURDAY to "Sat",
                    java.time.DayOfWeek.SUNDAY to "Sun",
                )
        }
    }
