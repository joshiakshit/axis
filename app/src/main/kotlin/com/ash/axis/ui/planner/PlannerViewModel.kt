package com.ash.axis.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.academic.attendanceKey
import com.ash.axis.data.academic.attendanceSelection
import com.ash.axis.data.repository.AttendanceKey
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.StudentMarkerRepository
import com.ash.axis.data.repository.TimetableData
import com.ash.axis.data.repository.TimetableKey
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.StudentMarker
import com.ash.axis.domain.model.StudentMarkerType
import com.ash.axis.domain.model.markerNoClassDates
import com.ash.axis.domain.usecase.AbsenceRange
import com.ash.axis.domain.usecase.AttendanceUseCase
import com.ash.axis.domain.usecase.ForecastEndDate
import com.ash.axis.domain.usecase.PlannerUseCase
import com.ash.axis.domain.usecase.ProjectedSubject
import com.ash.axis.domain.usecase.SubjectAttendance
import com.ash.axis.domain.usecase.TodayAttendance
import com.ash.axis.ui.ErrorText
import com.ash.core.network.NetworkMonitor
import com.ash.core.storage.PreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class PlannerUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null,
    val forecastEnd: LocalDate? = null,
    val absences: ImmutableList<AbsenceRange> = persistentListOf(),
    val markers: ImmutableList<StudentMarker> = persistentListOf(),
    val projected: ImmutableList<ProjectedSubject> = persistentListOf(),
    val threshold: Int = 75,
    val todayClassCount: Int = 0,
    val todayAttendance: TodayAttendance? = null,
    val attendanceUpdatedAt: Long? = null,
    val timetableUpdatedAt: Long? = null,
)

@HiltViewModel
@Suppress("TooGenericExceptionCaught")
class PlannerViewModel
    @Inject
    constructor(
        private val attendanceRepo: AttendanceRepository,
        private val timetableRepo: TimetableRepository,
        private val coordinator: AcademicDataCoordinator,
        private val markerRepository: StudentMarkerRepository,
        private val attendanceUseCase: AttendanceUseCase,
        private val plannerUseCase: PlannerUseCase,
        private val preferencesStore: PreferencesStore,
        private val networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PlannerUiState())
        val state = _state.asStateFlow()
        private val inputs = MutableStateFlow(ForecastInputs())
        private val refreshRequest = MutableStateFlow(0)
        private var summary: AcademicSnapshot<AttendanceResponse>? = null
        internal var calculationDispatcher: CoroutineDispatcher = Dispatchers.Default
        internal var clock: Clock = Clock.systemDefaultZone()

        init {
            viewModelScope.launch {
                var previousKey: AttendanceKey? = null
                var handledRefresh = 0
                combine(
                    coordinator.attendanceSelection(),
                    coordinator.transition,
                    refreshRequest,
                ) { selection, _, refresh ->
                    Triple(selection.attendanceKey, selection.error, refresh)
                }.collectLatest { (key, error, refresh) ->
                    if (key != previousKey) inputs.value = ForecastInputs()
                    previousKey = key
                    summary = null
                    inputs.update { it.copy(answer = null) }
                    _state.value = PlannerUiState(error = error?.let(ErrorText::forData), isLoading = error == null)
                    if (key == null) return@collectLatest
                    val force = refresh > handledRefresh
                    handledRefresh = refresh
                    try {
                        observeForecast(key, force)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        _state.update { it.copy(isLoading = false, error = ErrorText.forData(failure)) }
                    }
                }
            }
        }

        private suspend fun observeForecast(
            key: AttendanceKey,
            force: Boolean,
        ) = coroutineScope {
            val today = LocalDate.now(clock)
            val monday = today.with(DayOfWeek.MONDAY)
            val attendance = attendanceRepo.observeSummary(key)
            val timetable = timetableRepo.observeWeek(TimetableKey(key.context, monday.toString(), monday.plusDays(6).toString()))
            launch {
                try {
                    coordinator.plannerVisible(monday)
                    if (force) {
                        coordinator.refreshAttendance()
                        coordinator.refreshTimetable()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    inputs.update { it.copy(error = ErrorText.forData(failure)) }
                }
            }
            val preferences =
                combine(
                    preferencesStore.getUserBoolean("combined_attendance"),
                    preferencesStore.getUserInt("attendance_threshold", 75),
                    preferencesStore.getUserString("semester_end_date", ""),
                ) { combined, threshold, end ->
                    ForecastPreferences(combined, threshold, end)
                }
            val options =
                combine(
                    preferences,
                    inputs,
                    networkMonitor.isOnline,
                    coordinator.attendanceDemandError,
                    coordinator.timetableDemandError,
                ) { prefs, input, online, attendanceError, timetableError ->
                    ForecastOptions(prefs, input, online, attendanceError ?: timetableError)
                }
            combine(attendance, timetable, markerRepository.observe(key.context.admno), options) { current, week, markers, option ->
                ForecastSource(current, week, markers, option)
            }.collectLatest { source ->
                summary = source.attendance
                _state.value = render(source, LocalDate.now(clock))
            }
        }

        @Suppress("CyclomaticComplexMethod", "LongMethod", "ComplexCondition")
        private suspend fun render(
            source: ForecastSource,
            today: LocalDate,
        ): PlannerUiState {
            val attendance = source.attendance
            val week = source.week
            val markers = source.markers
            val options = source.options
            val preferences = options.preferences
            val input = options.input
            val online = options.online
            val demandError = options.error
            val end = ForecastEndDate.resolve(preferences.semesterEnd, today)
            val weekly = week.data?.weekly.orEmpty()
            val noClassDates = markerNoClassDates(markers)
            val todaySlots =
                if (today in noClassDates) {
                    emptyList()
                } else {
                    weekly[
                        today.dayOfWeek.getDisplayName(
                            TextStyle.SHORT,
                            Locale.ENGLISH,
                        ),
                    ].orEmpty()
                }
            val answer =
                input.answer?.takeIf {
                    it.date == today && it.data == attendance.data && it.updatedAt == attendance.updatedAtMillis
                }?.choice
            val todayAttendance = if (todaySlots.isEmpty()) TodayAttendance.ALREADY_INCLUDED else answer
            val raw =
                attendance.data?.table?.values.orEmpty().map {
                    SubjectAttendance(it.subCode, it.subname, it.lecType, it.present, it.total, it.percent)
                }
            val subjects = if (preferences.combined) attendanceUseCase.combineSubjects(raw) else raw
            val hasSchedule = weekly.values.any { it.isNotEmpty() }
            val error =
                input.error ?: (attendance.error ?: week.error ?: demandError)?.let(ErrorText::forData)
                    ?: if (week.data != null && !hasSchedule) "No weekly classes available. Refresh the timetable to forecast." else null
            val projected =
                if (attendance.data != null && hasSchedule && todayAttendance != null) {
                    withContext(calculationDispatcher) {
                        plannerUseCase.forecast(
                            subjects,
                            weekly,
                            today,
                            end,
                            todayAttendance,
                            input.absences,
                            noClassDates,
                            preferences.threshold,
                        )
                    }
                } else {
                    emptyList()
                }
            return PlannerUiState(
                isLoading = (attendance.data == null || week.data == null) && error == null,
                isRefreshing = attendance.refreshing || week.refreshing,
                isOffline = !online,
                error = error,
                forecastEnd = end,
                absences = input.absences,
                markers = markers.toImmutableList(),
                projected = projected.toImmutableList(),
                threshold = preferences.threshold,
                todayClassCount = todaySlots.size,
                todayAttendance = todayAttendance,
                attendanceUpdatedAt = attendance.updatedAtMillis,
                timetableUpdatedAt = week.updatedAtMillis,
            )
        }

        fun refresh() {
            inputs.update { it.copy(error = null) }
            refreshRequest.update { it + 1 }
        }

        fun setForecastEnd(date: LocalDate) {
            if (date < LocalDate.now(clock)) return
            val context = coordinator.activeContext.value ?: return
            val key = preferencesStore.userScoped("semester_end_date")
            viewModelScope.launch {
                if (coordinator.activeContext.value == context) preferencesStore.putString(key, date.toString())
            }
        }

        fun setTodayAttendance(choice: TodayAttendance) {
            val current = summary ?: return
            inputs.update { it.copy(answer = TodayAnswer(choice, current.data, current.updatedAtMillis, LocalDate.now(clock))) }
        }

        fun addAbsence(
            start: LocalDate,
            end: LocalDate,
        ) {
            if (end < start || start <= LocalDate.now(clock)) return
            val range = AbsenceRange(start, end)
            inputs.update { it.copy(absences = (it.absences + range).distinct().sortedBy { range -> range.start }.toImmutableList()) }
        }

        fun removeAbsence(range: AbsenceRange) {
            inputs.update { it.copy(absences = (it.absences - range).toImmutableList()) }
        }

        fun clearAbsences() {
            inputs.update { it.copy(absences = persistentListOf()) }
        }

        fun addNoClassDays(
            start: LocalDate,
            end: LocalDate,
        ) {
            if (end < start) return
            editMarkers { owner -> markerRepository.add(owner, "No class", StudentMarkerType.HOLIDAY, start, end) }
        }

        fun deleteMarker(id: Long) = editMarkers { owner -> markerRepository.delete(owner, id) }

        private fun editMarkers(edit: suspend (String) -> Unit) {
            val context = coordinator.activeContext.value ?: return
            viewModelScope.launch {
                try {
                    edit(context.admno)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (coordinator.activeContext.value == context) inputs.update { it.copy(error = ErrorText.forData(failure)) }
                }
            }
        }

        private data class TodayAnswer(
            val choice: TodayAttendance,
            val data: AttendanceResponse?,
            val updatedAt: Long?,
            val date: LocalDate,
        )

        private data class ForecastInputs(
            val absences: ImmutableList<AbsenceRange> = persistentListOf(),
            val answer: TodayAnswer? = null,
            val error: String? = null,
        )

        private data class ForecastPreferences(val combined: Boolean, val threshold: Int, val semesterEnd: String)

        private data class ForecastOptions(
            val preferences: ForecastPreferences,
            val input: ForecastInputs,
            val online: Boolean,
            val error: Throwable?,
        )

        private data class ForecastSource(
            val attendance: AcademicSnapshot<AttendanceResponse>,
            val week: AcademicSnapshot<TimetableData>,
            val markers: List<StudentMarker>,
            val options: ForecastOptions,
        )
    }
