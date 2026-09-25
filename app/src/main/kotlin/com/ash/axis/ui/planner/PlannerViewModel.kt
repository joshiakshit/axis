package com.ash.axis.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.repository.AttendanceKey
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.CalendarRepository
import com.ash.axis.data.repository.StudentMarkerRepository
import com.ash.axis.data.repository.TimetableData
import com.ash.axis.data.repository.TimetableKey
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.StudentMarker
import com.ash.axis.domain.model.StudentMarkerType
import com.ash.axis.domain.model.StudentRequestContext
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
import com.ash.core.storage.CacheFreshness
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
import kotlinx.coroutines.CoroutineDispatcher
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
    val coreReady: Boolean = false,
    val error: String? = null,
    val threshold: Int = 75,
    val overallPresent: Int = 0,
    val overallTotal: Int = 0,
    val subjects: ImmutableList<PlannerSubject> = persistentListOf(),
    val timetable: ImmutableMap<String, ImmutableList<TimetableSlot>> = persistentMapOf(),
    val dateTimetable: ImmutableMap<LocalDate, ImmutableList<TimetableSlot>> = persistentMapOf(),
    val coveredDates: ImmutableSet<LocalDate> = persistentSetOf(),
    val estimatedDates: ImmutableSet<LocalDate> = persistentSetOf(),
    val failedWeeks: ImmutableSet<LocalDate> = persistentSetOf(),
    val pendingWeeks: ImmutableSet<LocalDate> = persistentSetOf(),
    val projectionCoverage: ProjectionCoverage? = null,
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
@Suppress("TooGenericExceptionCaught", "ComplexCondition", "LargeClass")
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
        private val coverageJobs = mutableMapOf<LocalDate, Job>()
        private var projectionCoverageJob: Job? = null
        private var requiredWeeks: Set<LocalDate> = emptySet()
        private var observedWeeks: Set<LocalDate> = emptySet()
        private var selectedAttendanceKey: AttendanceKey? = null
        private var selectedWeekKey: TimetableKey? = null
        internal var calculationDispatcher: CoroutineDispatcher = Dispatchers.Default
        private var projectionRevision = 0L

        init {
            load(forceRefresh = false)
            loadCalendar()
            observePreferences()
            viewModelScope.launch { networkMonitor.isOnline.collect { online -> _state.update { it.copy(isOffline = !online) } } }
            viewModelScope.launch {
                combine(coordinator.attendanceDemandError, coordinator.timetableDemandError) { attendance, timetable ->
                    attendance ?: timetable
                }.collect { error ->
                    if (error != null) _state.update { it.copy(isLoading = false, error = ErrorText.forData(error)) }
                }
            }
            viewModelScope.launch {
                coordinator.activeContext.collect {
                    projectionRevision++
                    coverageJobs.values.forEach(Job::cancel)
                    coverageJobs.clear()
                    projectionCoverageJob?.cancel()
                    requiredWeeks = emptySet()
                    observedWeeks = emptySet()
                    selectedAttendanceKey = null
                    selectedWeekKey = null
                    markerJob?.cancel()
                    markerOwnerId = null
                    _state.update { current ->
                        current.copy(
                            subjects = persistentListOf(),
                            coreReady = false,
                            timetable = persistentMapOf(),
                            dateTimetable = persistentMapOf(),
                            coveredDates = persistentSetOf(),
                            estimatedDates = persistentSetOf(),
                            failedWeeks = persistentSetOf(),
                            pendingWeeks = persistentSetOf(),
                            projectionCoverage = null,
                            todayAttendance = null,
                            todayHasClasses = false,
                            projected = persistentListOf(),
                            markers = persistentListOf(),
                        )
                    }
                    load(forceRefresh = false)
                    if (it != null) loadCalendar()
                }
            }
            viewModelScope.launch {
                var observed = coordinator.transition.value
                coordinator.transition.collect { transition ->
                    if (transition == observed) return@collect
                    observed = transition
                    projectionRevision++
                    projectionCoverageJob?.cancel()
                    coverageJobs.values.forEach(Job::cancel)
                    coverageJobs.clear()
                    requiredWeeks = emptySet()
                    observedWeeks = emptySet()
                    _state.update { current ->
                        current.copy(
                            dateTimetable = persistentMapOf(),
                            coveredDates = persistentSetOf(),
                            estimatedDates = persistentSetOf(),
                            failedWeeks = persistentSetOf(),
                            pendingWeeks = persistentSetOf(),
                            projected = persistentListOf(),
                            projectionCoverage = null,
                        )
                    }
                }
            }
        }

        fun refresh() {
            requiredWeeks = emptySet()
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
                    if (user?.admno != coordinator.activeContext.value?.admno || _state.value.simulatorMonth != month) return@launch
                    _state.update { it.copy(calendar = calendar) }
                }
        }

        private fun observePreferences() {
            viewModelScope.launch {
                combine(
                    preferencesStore.getUserBoolean("combined_attendance"),
                    preferencesStore.getUserInt("attendance_threshold", 75),
                    preferencesStore.getUserString("semester_end_date", ""),
                    coordinator.selectedSemester,
                ) { _, _, _, selection -> selection }.collect { selection ->
                    val key = selectedAttendanceKey
                    val option = selection.option
                    if (option != null && selection.context == coordinator.activeContext.value &&
                        (key?.classId != option.classId || key?.year != option.yearId)
                    ) {
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
                publishProjection(base)
            }
        }

        fun setTodayAttendance(attendance: TodayAttendance) {
            if (attendance == TodayAttendance.NO_CLASSES) return
            viewModelScope.launch {
                preferencesStore.putUserString(TODAY_ATTENDANCE_DATE_KEY, LocalDate.now().toString())
                preferencesStore.putUserString(TODAY_ATTENDANCE_STATUS_KEY, attendance.name)
                val base = _state.value.copy(todayAttendance = attendance)
                publishProjection(base)
            }
        }

        fun markAbsent(date: LocalDate) {
            viewModelScope.launch {
                val current = _state.value
                if (current.holidayMode) {
                    val newHolidays = current.holidays.toMutableSet()
                    if (date in newHolidays) newHolidays.remove(date) else newHolidays.add(date)
                    val base = current.copy(holidays = newHolidays.toImmutableSet())
                    publishProjection(base)
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
                    publishProjection(base)
                }
            }
        }

        fun toggleHolidayMode() {
            _state.update { it.copy(holidayMode = !it.holidayMode) }
        }

        @Suppress("LongMethod", "CyclomaticComplexMethod")
        private suspend fun recomputeProjection(base: PlannerUiState): PlannerUiState {
            val today = LocalDate.now()
            val working = syncCoverage(base)
            val todayAttendance =
                working.todayAttendance
                    ?: return working.copy(
                        projected = persistentListOf(),
                        projectionCoverage =
                            when {
                                today !in working.coveredDates -> ProjectionCoverage.LOADING
                                today in working.estimatedDates -> ProjectionCoverage.ESTIMATE
                                else -> null
                            },
                    )
            val absenceDates = working.selectedDates
            val noClassDates = working.holidays + markerNoClassDates(working.markers)
            val horizon =
                listOfNotNull(
                    working.anchorDate,
                    absenceDates.filter { !it.isBefore(today) }.maxOrNull(),
                ).maxOrNull()
            val coverage =
                if (horizon != null && plannerWeekStart(horizon) !in requiredWeeks) {
                    ProjectionCoverage.LIMITED
                } else {
                    plannerProjectionCoverage(
                        today,
                        horizon,
                        working.coveredDates,
                        working.estimatedDates,
                        working.failedWeeks,
                        working.pendingWeeks,
                    )
                }
            if (coverage == ProjectionCoverage.LOADING || coverage == ProjectionCoverage.FAILED ||
                coverage == ProjectionCoverage.LIMITED
            ) {
                return working.copy(projected = persistentListOf(), projectionCoverage = coverage)
            }

            val projectionData =
                working.dateTimetable.filterKeys { date ->
                    date !in noClassDates && (date == today || horizon?.let { date in today..it } == true)
                }
            val projected =
                withContext(calculationDispatcher) {
                    val raw =
                        plannerUseCase.computeProjected(
                            working.subjects,
                            absenceDates,
                            projectionData,
                            working.threshold,
                            cachedSemesterEnd,
                            working.timetable,
                            today,
                            includeNoAbsence = true,
                            noClassDates = noClassDates,
                            projectionEnd = horizon,
                            todayAttendance = todayAttendance,
                        )
                    val focusDate = working.anchorDate ?: absenceDates.maxOrNull()
                    val dayKey = focusDate?.let { DAY_NAMES[it.dayOfWeek] }
                    val daySlotCodes =
                        if (dayKey != null) {
                            (working.timetable[dayKey] ?: emptyList())
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
            return working.copy(projected = projected, projectionCoverage = coverage)
        }

        private suspend fun publishProjection(base: PlannerUiState) {
            val revision = ++projectionRevision
            val transition = coordinator.transition.value
            val context = coordinator.activeContext.value
            val attendanceKey = selectedAttendanceKey
            val weekKey = selectedWeekKey
            val result = recomputeProjection(base)
            if (revision != projectionRevision || transition != coordinator.transition.value ||
                context != coordinator.activeContext.value || attendanceKey != selectedAttendanceKey ||
                weekKey != selectedWeekKey ||
                attendanceKey?.let {
                    coordinator.selectedSemester.value.option?.let { option ->
                        option.classId != it.classId || option.yearId != it.year
                    }
                } == true
            ) {
                return
            }
            _state.value = result
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
            viewModelScope.launch { publishProjection(_state.value) }
        }

        private fun load(forceRefresh: Boolean) {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    val context = coordinator.activeContext.value ?: return@launch
                    try {
                        val user = authRepository.getUserInfo() ?: error("Not logged in")
                        val selection = coordinator.selectedSemester.value
                        if (selection.context != context) return@launch
                        val option = selection.option ?: return@launch
                        val monday = plannerWeekStart(LocalDate.now())
                        val attendanceKey = AttendanceKey(context, option.classId, option.yearId)
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
                                    if (coordinator.activeContext.value == context && selectedAttendanceKey == attendanceKey) {
                                        _state.update { it.copy(isRefreshing = false, error = ErrorText.forData(error)) }
                                    }
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
                        if (coordinator.activeContext.value == context) {
                            _state.update { it.copy(isLoading = false, isRefreshing = false, error = ErrorText.forData(error)) }
                        }
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
                withContext(calculationDispatcher) {
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
            if (timetable == null) {
                coverageJobs.values.forEach(Job::cancel)
                coverageJobs.clear()
                projectionCoverageJob?.cancel()
                requiredWeeks = emptySet()
                observedWeeks = emptySet()
            }
            val allDates =
                if (timetable == null) {
                    emptyMap()
                } else {
                    old.dateTimetable.filterKeys { plannerWeekStart(it) != monday } +
                        dates.mapValues { it.value.toImmutableList() }
                }
            val covered =
                if (timetable == null) {
                    persistentSetOf()
                } else {
                    (old.coveredDates.filterNot { plannerWeekStart(it) == monday } + dates.keys).toImmutableSet()
                }
            val estimates = timetable?.let { plannerEstimatedDates(monday, it) }.orEmpty()
            val hasClasses = allDates[today]?.isNotEmpty() == true && today !in markerNoClassDates(old.markers)
            val todayAttendance =
                when {
                    today !in covered -> old.todayAttendance
                    today in estimates && !hasClasses -> null
                    !hasClasses -> TodayAttendance.NO_CLASSES
                    old.todayAttendance != null && old.todayAttendance != TodayAttendance.NO_CLASSES -> old.todayAttendance
                    savedTodayDate != today.toString() -> null
                    else -> runCatching { TodayAttendance.valueOf(savedTodayStatus) }.getOrNull()
                }
            val base =
                old.copy(
                    isLoading = attendance == null && timetable == null && summary.error == null && week.error == null,
                    coreReady = attendance != null && timetable != null,
                    isRefreshing = summary.refreshing || week.refreshing,
                    error = listOfNotNull(summary.error, week.error).firstOrNull()?.let(ErrorText::forData),
                    threshold = threshold,
                    overallPresent = attendance?.endrow?.present ?: 0,
                    overallTotal = attendance?.endrow?.total ?: 0,
                    subjects = computed.subjects.toImmutableList(),
                    timetable = weekly.mapValues { it.value.toImmutableList() }.toImmutableMap(),
                    dateTimetable = allDates.toImmutableMap(),
                    coveredDates = covered,
                    estimatedDates =
                        if (timetable == null) {
                            persistentSetOf()
                        } else {
                            (old.estimatedDates.filterNot { plannerWeekStart(it) == monday } + estimates).toImmutableSet()
                        },
                    failedWeeks =
                        if (week.error == null) {
                            (old.failedWeeks - monday).toImmutableSet()
                        } else {
                            (old.failedWeeks + monday).toImmutableSet()
                        },
                    pendingWeeks =
                        if (week.refreshing) {
                            (old.pendingWeeks + monday).toImmutableSet()
                        } else {
                            (old.pendingWeeks - monday).toImmutableSet()
                        },
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
            publishProjection(base)
        }

        @Suppress("CyclomaticComplexMethod", "LongMethod")
        private fun syncCoverage(base: PlannerUiState): PlannerUiState {
            val context = coordinator.activeContext.value ?: return base
            if (!base.coreReady) return base
            val today = LocalDate.now()
            val currentWeek = plannerWeekStart(today)
            val monthStart = base.simulatorMonth
            val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
            val horizon = listOfNotNull(base.anchorDate, base.selectedDates.maxOrNull()).maxOrNull()
            val visibleWeeks = plannerWeeks(monthStart, monthEnd)
            val projectionWeeks = if (base.todayAttendance != null && horizon != null) plannerWeeks(today, horizon) else emptyList()
            val wanted = (listOf(currentWeek) + visibleWeeks + projectionWeeks).distinct().toSet()
            val visibleSet = visibleWeeks.toSet() - currentWeek
            if (wanted != requiredWeeks || visibleSet != observedWeeks) {
                requiredWeeks = wanted
                observedWeeks = visibleSet
                coverageJobs.keys.filter { it !in observedWeeks }.forEach { coverageJobs.remove(it)?.cancel() }
                observedWeeks.filter { coverageJobs[it]?.isActive != true }.forEach { week ->
                    coverageJobs[week] =
                        viewModelScope.launch {
                            val key = TimetableKey(context, week.toString(), week.plusDays(6).toString())
                            try {
                                timetableRepo.observeWeek(key).collect { snapshot ->
                                    applyCoverageSnapshot(context, week, snapshot)
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                markCoverageFailure(context, week, error)
                            }
                        }
                }
                viewModelScope.launch { coordinator.plannerCoverage(wanted.filter { it != currentWeek }) }
                projectionCoverageJob?.cancel()
                projectionCoverageJob =
                    viewModelScope.launch {
                        wanted.filter { it != currentWeek && it !in observedWeeks }.forEach { week ->
                            val key = TimetableKey(context, week.toString(), week.plusDays(6).toString())
                            try {
                                val flow = timetableRepo.observeWeek(key)
                                val first =
                                    flow.first { snapshot ->
                                        snapshot.refreshing || snapshot.error != null ||
                                            (
                                                snapshot.data != null && snapshot.freshness != CacheFreshness.STALE &&
                                                    snapshot.freshness != CacheFreshness.EXPIRED
                                            )
                                    }
                                val settled = if (first.refreshing) flow.first { !it.refreshing } else first
                                applyCoverageSnapshot(context, week, settled)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                markCoverageFailure(context, week, error)
                            }
                        }
                    }
            }
            return base.copy(
                dateTimetable = base.dateTimetable.filterKeys { plannerWeekStart(it) in wanted }.toImmutableMap(),
                coveredDates = base.coveredDates.filterTo(mutableSetOf()) { plannerWeekStart(it) in wanted }.toImmutableSet(),
                estimatedDates = base.estimatedDates.filterTo(mutableSetOf()) { plannerWeekStart(it) in wanted }.toImmutableSet(),
                failedWeeks = base.failedWeeks.filterTo(mutableSetOf()) { it in wanted }.toImmutableSet(),
                pendingWeeks = base.pendingWeeks.filterTo(mutableSetOf()) { it in wanted }.toImmutableSet(),
            )
        }

        private suspend fun applyCoverageSnapshot(
            context: StudentRequestContext,
            week: LocalDate,
            snapshot: AcademicSnapshot<TimetableData>,
        ) {
            if (coordinator.activeContext.value != context || week !in requiredWeeks) return
            val dates = snapshot.data?.let { plannerWeekDates(week, it) }.orEmpty()
            val estimates = snapshot.data?.let { plannerEstimatedDates(week, it) }.orEmpty()
            _state.update { current ->
                current.copy(
                    dateTimetable =
                        (
                            current.dateTimetable.filterKeys { plannerWeekStart(it) != week } +
                                dates.mapValues { it.value.toImmutableList() }
                        ).toImmutableMap(),
                    coveredDates =
                        (current.coveredDates.filterNot { plannerWeekStart(it) == week } + dates.keys).toImmutableSet(),
                    estimatedDates =
                        (current.estimatedDates.filterNot { plannerWeekStart(it) == week } + estimates).toImmutableSet(),
                    failedWeeks =
                        if (snapshot.error == null) {
                            (current.failedWeeks - week).toImmutableSet()
                        } else {
                            (current.failedWeeks + week).toImmutableSet()
                        },
                    pendingWeeks =
                        if (snapshot.refreshing) {
                            (current.pendingWeeks + week).toImmutableSet()
                        } else {
                            (current.pendingWeeks - week).toImmutableSet()
                        },
                )
            }
            publishProjection(_state.value)
        }

        private suspend fun markCoverageFailure(
            context: StudentRequestContext,
            week: LocalDate,
            error: Exception,
        ) {
            if (coordinator.activeContext.value != context || week !in requiredWeeks) return
            _state.update { it.copy(failedWeeks = (it.failedWeeks + week).toImmutableSet(), error = ErrorText.forData(error)) }
            publishProjection(_state.value)
        }

        private fun plannerWeeks(
            start: LocalDate,
            end: LocalDate,
        ): List<LocalDate> = generateSequence(plannerWeekStart(start)) { it.plusWeeks(1) }.takeWhile { !it.isAfter(end) }.toList()

        private fun observeMarkers(ownerId: String) {
            if (markerOwnerId == ownerId) return
            markerOwnerId = ownerId
            markerJob?.cancel()
            markerJob =
                viewModelScope.launch {
                    markerRepository.observe(ownerId).collect { markers ->
                        if (markerOwnerId != ownerId || coordinator.activeContext.value?.admno != ownerId) return@collect
                        val today = LocalDate.now()
                        val hasClasses =
                            _state.value.dateTimetable[today].orEmpty().isNotEmpty() &&
                                today !in markerNoClassDates(markers)
                        val currentAttendance = _state.value.todayAttendance
                        val todayAttendance =
                            when {
                                today !in _state.value.coveredDates -> currentAttendance
                                today in _state.value.estimatedDates && !hasClasses -> null
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
                        publishProjection(base)
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
