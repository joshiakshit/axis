package com.ash.axis.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.repository.AttendanceKey
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.CalendarRepository
import com.ash.axis.data.repository.SELECTED_SEMESTER_CLASS_KEY
import com.ash.axis.data.repository.SELECTED_SEMESTER_YEAR_KEY
import com.ash.axis.data.repository.StudentMarkerRepository
import com.ash.axis.data.repository.TimetableData
import com.ash.axis.data.repository.TimetableKey
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.StudentMarker
import com.ash.axis.domain.model.StudentMarkerType
import com.ash.axis.domain.model.TimetableSlot
import com.ash.axis.domain.model.markerNoClassDates
import com.ash.axis.domain.usecase.AttendanceTone
import com.ash.axis.domain.usecase.AttendanceUseCase
import com.ash.axis.domain.usecase.PlannerSubject
import com.ash.axis.domain.usecase.PlannerUseCase
import com.ash.axis.domain.usecase.ProjectedSubject
import com.ash.axis.domain.usecase.SubjectAttendance
import com.ash.axis.domain.usecase.TodayAttendance
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val dateTimetable: ImmutableMap<LocalDate, ImmutableList<TimetableSlot>> = persistentMapOf(),
    val coveredDates: ImmutableSet<LocalDate> = persistentSetOf(),
    val selectedDates: ImmutableSet<LocalDate> = persistentSetOf(),
    val holidays: ImmutableSet<LocalDate> = persistentSetOf(),
    val markers: ImmutableList<StudentMarker> = persistentListOf(),
    val holidayMode: Boolean = false,
    val anchorDate: LocalDate? = null,
    val projected: ImmutableList<ProjectedSubject> = persistentListOf(),
    val totalSpare: Int = 0,
    val isRefreshing: Boolean = false,
    val simulatorMonth: LocalDate = LocalDate.now().withDayOfMonth(1),
    val semesterEndSet: Boolean = false,
    val semesterEndDate: LocalDate? = null,
    val todayHasClasses: Boolean = false,
    val todayClassCount: Int = 0,
    val todayAttendance: TodayAttendance? = null,
    val isOffline: Boolean = false,
    val calendar: CalendarUiState = CalendarUiState(),
)

@HiltViewModel
@Suppress("TooGenericExceptionCaught")
class PlannerViewModel
    @Inject
    @Suppress("LongParameterList")
    constructor(
        private val attendanceRepo: AttendanceRepository,
        private val timetableRepo: TimetableRepository,
        private val coordinator: AcademicDataCoordinator,
        private val authRepository: AuthRepository,
        private val markerRepository: StudentMarkerRepository,
        private val calendarRepository: CalendarRepository,
        private val attendanceUseCase: AttendanceUseCase,
        private val plannerUseCase: PlannerUseCase,
        private val preferencesStore: PreferencesStore,
        private val networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PlannerUiState())
        val state: StateFlow<PlannerUiState> = _state.asStateFlow()

        private var loadJob: Job? = null
        private var markerJob: Job? = null
        private var calendarJob: Job? = null
        private var markerOwnerId: String? = null
        private var cachedSemesterEnd: LocalDate? = null
        private val coveredWeeks = mutableSetOf<LocalDate>()
        private val coverageJobs = mutableMapOf<LocalDate, Job>()
        private var coverageTail: Job? = null
        private var selectedAttendanceKey: AttendanceKey? = null
        private var selectedWeekKey: TimetableKey? = null
        private var selectedSemesterIds: Pair<String, String>? = null

        init {
            load(forceRefresh = false)
            loadCalendar()
            observePreferences()
            viewModelScope.launch { networkMonitor.isOnline.collect { online -> _state.update { it.copy(isOffline = !online) } } }
            viewModelScope.launch {
                coordinator.activeContext.collect {
                    coverageJobs.values.forEach(Job::cancel)
                    coverageJobs.clear()
                    coveredWeeks.clear()
                    coverageTail = null
                    selectedAttendanceKey = null
                    selectedWeekKey = null
                    markerJob?.cancel()
                    markerOwnerId = null
                    _state.update { current ->
                        current.copy(
                            subjects = persistentListOf(),
                            timetable = persistentMapOf(),
                            dateTimetable = persistentMapOf(),
                            coveredDates = persistentSetOf(),
                            todayAttendance = null,
                            todayHasClasses = false,
                            projected = persistentListOf(),
                            markers = persistentListOf(),
                        )
                    }
                    load(forceRefresh = false)
                }
            }
        }

        fun refresh() {
            load(forceRefresh = true)
            loadCalendar(forceRefresh = true)
        }

        fun loadCalendar(forceRefresh: Boolean = false) {
            val month = _state.value.simulatorMonth
            calendarJob?.cancel()
            _state.update { it.copy(calendar = CalendarUiState()) }
            calendarJob =
                viewModelScope.launch {
                    val user = authRepository.getUserInfo()
                    val calendar =
                        if (user == null) {
                            CalendarUiState(isLoading = false, error = "Sign in to load the calendar")
                        } else {
                            calendarRepository.loadState(user, month, forceRefresh)
                        }
                    _state.update { it.copy(calendar = calendar) }
                }
        }

        private fun observePreferences() {
            viewModelScope.launch {
                combine(
                    preferencesStore.getUserBoolean("combined_attendance"),
                    preferencesStore.getUserInt("attendance_threshold", 75),
                    preferencesStore.getUserString("semester_end_date", ""),
                    preferencesStore.getUserString(SELECTED_SEMESTER_YEAR_KEY),
                    preferencesStore.getUserString(SELECTED_SEMESTER_CLASS_KEY),
                ) { _, _, _, yearId, classId -> yearId to classId }.collect { selection ->
                    if (selectedSemesterIds != selection) {
                        selectedSemesterIds = selection
                        load(forceRefresh = false)
                    } else {
                        val attendanceKey = selectedAttendanceKey
                        val weekKey = selectedWeekKey
                        if (attendanceKey != null && weekKey != null) {
                            applySnapshots(
                                attendanceRepo.observeSummary(attendanceKey).value,
                                timetableRepo.observeWeek(weekKey).value,
                                LocalDate.parse(weekKey.startDate),
                            )
                        }
                    }
                }
            }
        }

        fun previewDate(date: LocalDate) {
            viewModelScope.launch {
                val base = _state.value.copy(anchorDate = date)
                _state.value = recomputeProjection(base)
            }
        }

        fun setTodayAttendance(attendance: TodayAttendance) {
            if (attendance == TodayAttendance.NO_CLASSES) return
            viewModelScope.launch {
                preferencesStore.putUserString(TODAY_ATTENDANCE_DATE_KEY, LocalDate.now().toString())
                preferencesStore.putUserString(TODAY_ATTENDANCE_STATUS_KEY, attendance.name)
                val base = _state.value.copy(todayAttendance = attendance)
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
            val todayAttendance = base.todayAttendance ?: return base.copy(projected = persistentListOf())
            val absenceDates = base.selectedDates
            val noClassDates = base.holidays + markerNoClassDates(base.markers)
            val horizon =
                listOfNotNull(
                    base.anchorDate,
                    absenceDates.filter { !it.isBefore(today) }.maxOrNull(),
                ).maxOrNull()

            if (horizon != null) requestCoverage(today, horizon)

            val projectionData =
                base.dateTimetable.filterKeys { date ->
                    date !in noClassDates && (date == today || horizon?.let { date in today..it } == true)
                }
            val projected =
                withContext(Dispatchers.Default) {
                    val raw =
                        plannerUseCase.computeProjected(
                            base.subjects,
                            absenceDates,
                            projectionData,
                            base.threshold,
                            cachedSemesterEnd,
                            base.timetable,
                            today,
                            includeNoAbsence = true,
                            noClassDates = noClassDates,
                            projectionEnd = horizon,
                            todayAttendance = todayAttendance,
                        )
                    val focusDate = base.anchorDate ?: absenceDates.maxOrNull()
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

        fun setSemesterEndDate(dateStr: String) {
            viewModelScope.launch {
                preferencesStore.putUserString("semester_end_date", dateStr)
            }
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

        fun addMarker(
            title: String,
            type: StudentMarkerType,
            startDate: LocalDate,
            endDate: LocalDate,
        ) {
            val ownerId = markerOwnerId ?: return
            if (title.isBlank()) return
            viewModelScope.launch {
                markerRepository.add(ownerId, title, type, startDate, endDate)
            }
        }

        fun deleteMarker(markerId: Long) {
            val ownerId = markerOwnerId ?: return
            viewModelScope.launch {
                markerRepository.delete(ownerId, markerId)
            }
        }

        fun shiftSimulatorMonth(delta: Int) {
            val newMonth = _state.value.simulatorMonth.plusMonths(delta.toLong())
            _state.update { it.copy(simulatorMonth = newMonth) }
            loadCalendar()
            requestCoverage(newMonth, newMonth.withDayOfMonth(newMonth.lengthOfMonth()))
        }

        private fun load(forceRefresh: Boolean) {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    val context = coordinator.activeContext.value ?: return@launch
                    try {
                        val user = authRepository.getUserInfo() ?: error("Not logged in")
                        val selectedYearId = preferencesStore.getUserString(SELECTED_SEMESTER_YEAR_KEY).first()
                        val selectedClassId = preferencesStore.getUserString(SELECTED_SEMESTER_CLASS_KEY).first()
                        val semester =
                            attendanceRepo.getPreferredSemester(
                                user.admno, user.brId, selectedYearId, selectedClassId, forceRefresh,
                            )
                        val monday = plannerWeekStart(LocalDate.now())
                        val attendanceKey = AttendanceKey(context, semester.classId, semester.yearId)
                        val weekKey = TimetableKey(context, monday.toString(), monday.plusDays(6).toString())
                        selectedAttendanceKey = attendanceKey
                        selectedWeekKey = weekKey
                        val attendance = attendanceRepo.observeSummary(attendanceKey)
                        val timetable = timetableRepo.observeWeek(weekKey)
                        launch { coordinator.plannerVisible(monday) }
                        if (forceRefresh) {
                            launch {
                                try {
                                    coordinator.refreshAttendance()
                                    coordinator.refreshTimetable()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    _state.update { it.copy(isRefreshing = false, error = ErrorText.forData(error)) }
                                }
                            }
                        }
                        observeMarkers(user.admno)
                        combine(attendance, timetable) { summary, week -> summary to week }.collect { (summary, week) ->
                            if (coordinator.activeContext.value != context) return@collect
                            applySnapshots(summary, week, monday)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        _state.update { it.copy(isLoading = false, isRefreshing = false, error = ErrorText.forData(error)) }
                    }
                }
        }

        @Suppress("CyclomaticComplexMethod", "LongMethod")
        private suspend fun applySnapshots(
            summary: AcademicSnapshot<AttendanceResponse>,
            week: AcademicSnapshot<TimetableData>,
            monday: LocalDate,
        ) {
            val attendanceKey = selectedAttendanceKey
            val weekKey = selectedWeekKey
            val attendance = summary.data
            val timetable = week.data
            val threshold = preferencesStore.getUserInt("attendance_threshold", 75).first()
            val combined = preferencesStore.getUserBoolean("combined_attendance").first()
            val endDate = preferencesStore.getUserString("semester_end_date", "").first()
            cachedSemesterEnd = runCatching { LocalDate.parse(endDate) }.getOrNull()
            val raw =
                attendance?.table?.values?.map {
                    SubjectAttendance(it.subCode, it.subname, it.lecType, it.present, it.total, it.percent)
                }.orEmpty()
            val subjects = if (combined) attendanceUseCase.combineSubjects(raw) else raw
            val weekly = timetable?.weekly.orEmpty()
            val computed =
                withContext(Dispatchers.Default) {
                    val rows =
                        plannerUseCase.buildPlannerSubjects(subjects, weekly, threshold)
                            .sortedWith(compareBy<PlannerSubject> { it.tone.ordinal }.thenBy { it.name })
                    PlannerComputed(rows, rows.filter { it.tone != AttendanceTone.BAD }.sumOf { it.bunkable })
                }
            val dates = timetable?.let { plannerWeekDates(monday, it) }.orEmpty()
            val today = LocalDate.now()
            val savedTodayDate = preferencesStore.getUserString(TODAY_ATTENDANCE_DATE_KEY).first()
            val savedTodayStatus = preferencesStore.getUserString(TODAY_ATTENDANCE_STATUS_KEY).first()
            val old = _state.value
            val allDates = old.dateTimetable + dates.mapValues { it.value.toImmutableList() }
            val covered = if (timetable == null) old.coveredDates else (old.coveredDates + dates.keys).toImmutableSet()
            val hasClasses = allDates[today]?.isNotEmpty() == true && today !in markerNoClassDates(old.markers)
            val todayAttendance =
                when {
                    today !in covered -> old.todayAttendance
                    !hasClasses -> TodayAttendance.NO_CLASSES
                    old.todayAttendance != null && old.todayAttendance != TodayAttendance.NO_CLASSES -> old.todayAttendance
                    savedTodayDate != today.toString() -> null
                    else -> runCatching { TodayAttendance.valueOf(savedTodayStatus) }.getOrNull()
                }
            val base =
                old.copy(
                    isLoading = attendance == null && timetable == null && summary.error == null && week.error == null,
                    isRefreshing = summary.refreshing || week.refreshing,
                    error = listOfNotNull(summary.error, week.error).firstOrNull()?.let(ErrorText::forData),
                    threshold = threshold,
                    overallPresent = attendance?.endrow?.present ?: old.overallPresent,
                    overallTotal = attendance?.endrow?.total ?: old.overallTotal,
                    subjects = computed.subjects.toImmutableList(),
                    timetable = weekly.mapValues { it.value.toImmutableList() }.toImmutableMap(),
                    dateTimetable = allDates.toImmutableMap(),
                    coveredDates = covered,
                    totalSpare = computed.totalSpare,
                    semesterEndSet = cachedSemesterEnd != null,
                    semesterEndDate = cachedSemesterEnd,
                    todayHasClasses = hasClasses,
                    todayClassCount = if (hasClasses) allDates[today].orEmpty().size else 0,
                    todayAttendance = todayAttendance,
                )
            if (attendanceKey != selectedAttendanceKey || weekKey != selectedWeekKey ||
                coordinator.activeContext.value != weekKey?.context
            ) {
                return
            }
            val next = if (todayAttendance == null) base.copy(projected = persistentListOf()) else recomputeProjection(base)
            if (attendanceKey != selectedAttendanceKey || weekKey != selectedWeekKey ||
                coordinator.activeContext.value != weekKey?.context
            ) {
                return
            }
            _state.value = next
            if (attendance != null && timetable != null) {
                requestCoverage(old.simulatorMonth, old.simulatorMonth.withDayOfMonth(old.simulatorMonth.lengthOfMonth()))
            }
        }

        private fun requestCoverage(
            start: LocalDate,
            end: LocalDate,
        ) {
            val context = coordinator.activeContext.value ?: return
            var monday = plannerWeekStart(start)
            while (!monday.isAfter(end)) {
                val week = monday
                if (week !in coveredWeeks && coverageJobs[week]?.isActive != true) {
                    val previous = coverageTail
                    coverageJobs[week] =
                        viewModelScope.launch {
                            previous?.join()
                            if (coordinator.activeContext.value != context) return@launch
                            val key = TimetableKey(context, week.toString(), week.plusDays(6).toString())
                            try {
                                val flow = timetableRepo.observeWeek(key)
                                launch { timetableRepo.requestWeek(key) }
                                var started = false
                                val snapshot =
                                    flow.first {
                                        if (it.refreshing) started = true
                                        it.data != null || it.error != null || (started && !it.refreshing)
                                    }
                                if (coordinator.activeContext.value == context) {
                                    val data = snapshot.data ?: return@launch
                                    val dates = plannerWeekDates(week, data)
                                    coveredWeeks += week
                                    val immutableDates = dates.mapValues { it.value.toImmutableList() }
                                    _state.update { current ->
                                        current.copy(
                                            dateTimetable = (current.dateTimetable + immutableDates).toImmutableMap(),
                                            coveredDates = (current.coveredDates + dates.keys).toImmutableSet(),
                                        )
                                    }
                                    val current = _state.value
                                    _state.value = recomputeProjection(current)
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                coverageJobs.remove(week)
                            }
                        }
                    coverageTail = coverageJobs[week]
                }
                monday = monday.plusWeeks(1)
            }
        }

        private fun observeMarkers(ownerId: String) {
            if (markerOwnerId == ownerId) return
            markerOwnerId = ownerId
            markerJob?.cancel()
            markerJob =
                viewModelScope.launch {
                    markerRepository.observe(ownerId).collect { markers ->
                        val today = LocalDate.now()
                        val hasClasses =
                            _state.value.dateTimetable[today].orEmpty().isNotEmpty() &&
                                today !in markerNoClassDates(markers)
                        val currentAttendance = _state.value.todayAttendance
                        val todayAttendance =
                            when {
                                today !in _state.value.coveredDates -> currentAttendance
                                !hasClasses -> TodayAttendance.NO_CLASSES
                                currentAttendance == TodayAttendance.NO_CLASSES -> null
                                else -> currentAttendance
                            }
                        val base =
                            _state.value.copy(
                                markers = markers.toImmutableList(),
                                todayHasClasses = hasClasses,
                                todayClassCount = if (hasClasses) _state.value.dateTimetable[today].orEmpty().size else 0,
                                todayAttendance = todayAttendance,
                            )
                        _state.value = recomputeProjection(base)
                    }
                }
        }

        private data class PlannerComputed(
            val subjects: List<PlannerSubject>,
            val totalSpare: Int,
        )

        private companion object {
            const val TODAY_ATTENDANCE_DATE_KEY = "planner_today_attendance_date"
            const val TODAY_ATTENDANCE_STATUS_KEY = "planner_today_attendance_status"

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
