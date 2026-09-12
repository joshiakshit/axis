package com.ash.axis.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.DataRefreshSignal
import com.ash.axis.data.RefreshTrigger
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.SELECTED_SEMESTER_CLASS_KEY
import com.ash.axis.data.repository.SELECTED_SEMESTER_YEAR_KEY
import com.ash.axis.data.repository.StudentMarkerRepository
import com.ash.axis.data.repository.TimetableRepository
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
import com.ash.axis.domain.usecase.TimetableUseCase
import com.ash.axis.domain.usecase.TodayAttendance
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
import kotlinx.coroutines.awaitAll
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
    // Real per-date future timetable used to decide which days are selectable in the simulator.
    val dateTimetable: ImmutableMap<LocalDate, ImmutableList<TimetableSlot>> = persistentMapOf(),
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
)

@HiltViewModel
@Suppress("TooGenericExceptionCaught")
class PlannerViewModel
    @Inject
    @Suppress("LongParameterList")
    constructor(
        private val attendanceRepo: AttendanceRepository,
        private val timetableRepo: TimetableRepository,
        private val authRepository: AuthRepository,
        private val markerRepository: StudentMarkerRepository,
        private val attendanceUseCase: AttendanceUseCase,
        private val plannerUseCase: PlannerUseCase,
        private val timetableUseCase: TimetableUseCase,
        private val preferencesStore: PreferencesStore,
        private val networkMonitor: NetworkMonitor,
        private val refreshSignal: DataRefreshSignal,
    ) : ViewModel() {
        private val refreshSourceId = DataRefreshSignal.newSourceId()

        private val _state = MutableStateFlow(PlannerUiState())
        val state: StateFlow<PlannerUiState> = _state.asStateFlow()

        private var loadJob: Job? = null
        private var markerJob: Job? = null
        private var markerOwnerId: String? = null
        private var cachedTimetableContext: StudentRequestContext? = null
        private var cachedSemesterEnd: LocalDate? = null
        private var dateTimetableCache: Map<LocalDate, List<TimetableSlot>> = emptyMap()
            set(value) {
                field = value
                immutableDateTimetable =
                    value.mapValues { (_, slots) -> slots.toImmutableList() }.toImmutableMap()
            }
        private var immutableDateTimetable: ImmutableMap<LocalDate, ImmutableList<TimetableSlot>> =
            persistentMapOf()
        private var dateTimetableRange: Pair<LocalDate, LocalDate>? = null

        init {
            load(forceRefresh = false)
            observePreferences()
            viewModelScope.launch {
                refreshSignal.signal.collect { event ->
                    if (event.sourceId != refreshSourceId &&
                        (event.trigger == RefreshTrigger.ALL || event.trigger == RefreshTrigger.ATTENDANCE)
                    ) {
                        load(forceRefresh = false)
                    }
                }
            }
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

            val cachedRange = dateTimetableRange
            if (horizon != null && (cachedRange == null || horizon > cachedRange.second)) {
                fetchDateTimetable(today, horizon)
            }

            val projectionData =
                dateTimetableCache.filterKeys { date ->
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
            return base.copy(projected = projected, dateTimetable = immutableDateTimetable)
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
            viewModelScope.launch {
                ensureDateCoverage(newMonth.withDayOfMonth(newMonth.lengthOfMonth()))
                _state.update { it.copy(dateTimetable = immutableDateTimetable) }
            }
        }

        // Extend the date-keyed timetable cache so the grid always has real data for the visible month.
        private suspend fun ensureDateCoverage(end: LocalDate) {
            val range = dateTimetableRange
            if (range == null || end.isAfter(range.second)) {
                fetchDateTimetable(LocalDate.now(), maxOf(end, range?.second ?: end))
            }
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
                        val timetableContext =
                            authRepository.requireStudentRequestContext(forceProfileRefresh = forceRefresh)
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
                        cachedTimetableContext = timetableContext
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
                                            timetableContext,
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
                                val totalSpare =
                                    plannerSubjects
                                        .filter { it.tone != AttendanceTone.BAD }
                                        .sumOf { it.bunkable }
                                PlannerComputed(plannerSubjects, totalSpare)
                            }

                        val today = LocalDate.now()
                        val cacheEnd = today.plusWeeks(4)
                        primeDateTimetableCache(timetableContext, today to cacheEnd, timetable, forceRefresh)
                        val todayHasClasses = dateTimetableCache[today].orEmpty().isNotEmpty()
                        val savedTodayDate = preferencesStore.getUserString(TODAY_ATTENDANCE_DATE_KEY).first()
                        val savedTodayStatus = preferencesStore.getUserString(TODAY_ATTENDANCE_STATUS_KEY).first()
                        val todayAttendance =
                            when {
                                !todayHasClasses -> TodayAttendance.NO_CLASSES
                                savedTodayDate != today.toString() -> null
                                else -> runCatching { TodayAttendance.valueOf(savedTodayStatus) }.getOrNull()
                            }

                        val offline = networkMonitor.isOnline.first().not()
                        val immutableTimetable =
                            timetable.mapValues { (_, v) -> v.toImmutableList() }.toImmutableMap()
                        val loaded =
                            _state.value.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = null,
                                threshold = threshold,
                                overallPresent = attendance.endrow.present,
                                overallTotal = attendance.endrow.total,
                                subjects = computed.subjects.toImmutableList(),
                                timetable = immutableTimetable,
                                dateTimetable = immutableDateTimetable,
                                totalSpare = computed.totalSpare,
                                selectedDates = persistentSetOf(),
                                anchorDate = null,
                                projected = persistentListOf(),
                                semesterEndSet = cachedSemesterEnd != null,
                                semesterEndDate = cachedSemesterEnd,
                                todayHasClasses = todayHasClasses,
                                todayClassCount = dateTimetableCache[today].orEmpty().size,
                                todayAttendance = todayAttendance,
                                isOffline = offline,
                            )
                        _state.value = if (todayAttendance == null) loaded else recomputeProjection(loaded)
                        observeMarkers(user.admno)
                        if (forceRefresh) refreshSignal.emit(RefreshTrigger.ATTENDANCE, refreshSourceId)
                    } catch (e: Exception) {
                        val offline = networkMonitor.isOnline.first().not()
                        _state.update {
                            it.copy(isLoading = false, isRefreshing = false, error = ErrorText.forData(e), isOffline = offline)
                        }
                    }
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
                            dateTimetableCache[today].orEmpty().isNotEmpty() &&
                                today !in markerNoClassDates(markers)
                        val currentAttendance = _state.value.todayAttendance
                        val todayAttendance =
                            when {
                                !hasClasses -> TodayAttendance.NO_CLASSES
                                currentAttendance == TodayAttendance.NO_CLASSES -> null
                                else -> currentAttendance
                            }
                        val base =
                            _state.value.copy(
                                markers = markers.toImmutableList(),
                                todayHasClasses = hasClasses,
                                todayClassCount = if (hasClasses) dateTimetableCache[today].orEmpty().size else 0,
                                todayAttendance = todayAttendance,
                            )
                        _state.value = recomputeProjection(base)
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
                    val context = cachedTimetableContext ?: authRepository.requireStudentRequestContext()
                    if (context.admno.isNotBlank()) {
                        fetchDateKeyedRange(context, start, end, forceRefresh = false)
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
            context: StudentRequestContext,
            range: Pair<LocalDate, LocalDate>,
            weekly: Map<String, List<TimetableSlot>>,
            forceRefresh: Boolean,
        ) {
            val (start, end) = range
            val apiData =
                runCatching {
                    fetchDateKeyedRange(context, start, end, forceRefresh)
                }.getOrDefault(emptyMap())

            dateTimetableCache = mergeWithWeeklyFallback(apiData, weekly, start, end)
            dateTimetableRange = start to end
        }

        // The timetable endpoint serves one Mon–Sun week per request, so a single wide-range call only
        // returns one week. Fetch every week that overlaps [start, end] in parallel and merge them.
        private suspend fun fetchDateKeyedRange(
            context: StudentRequestContext,
            start: LocalDate,
            end: LocalDate,
            forceRefresh: Boolean,
        ): Map<LocalDate, List<TimetableSlot>> =
            coroutineScope {
                val weekStarts =
                    generateSequence(start.with(java.time.DayOfWeek.MONDAY)) { it.plusWeeks(1) }
                        .takeWhile { !it.isAfter(end) }
                        .toList()
                weekStarts
                    .map { weekStart ->
                        async {
                            runCatching {
                                timetableRepo.getDateKeyedTimetable(
                                    context,
                                    weekStart.toString(),
                                    weekStart.plusDays(6).toString(),
                                    forceRefresh,
                                )
                            }.getOrDefault(emptyMap())
                        }
                    }
                    .awaitAll()
                    .fold(mutableMapOf<LocalDate, List<TimetableSlot>>()) { acc, week -> acc.apply { putAll(week) } }
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
