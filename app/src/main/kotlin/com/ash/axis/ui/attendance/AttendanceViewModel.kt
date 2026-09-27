package com.ash.axis.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicSemesterSelection
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.academic.observeAttendance
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.TimetableData
import com.ash.axis.data.repository.TimetableKey
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceEntry
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.usecase.AttendanceTone
import com.ash.axis.domain.usecase.AttendanceUseCase
import com.ash.axis.domain.usecase.ForecastEndDate
import com.ash.axis.domain.usecase.ForecastRow
import com.ash.axis.domain.usecase.ForecastUseCase
import com.ash.axis.domain.usecase.SubjectAttendance
import com.ash.axis.domain.usecase.TimetableUseCase
import com.ash.axis.ui.ErrorText
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DecoratedSubject(
    val subject: SubjectAttendance,
    val bunkable: Int,
    val need: Int,
    val tone: AttendanceTone,
)

data class AttendanceUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val overallPercent: Double = 0.0,
    val overallPresent: Int = 0,
    val overallTotal: Int = 0,
    val overallTone: AttendanceTone = AttendanceTone.OK,
    val subjects: ImmutableList<DecoratedSubject> = persistentListOf(),
    val forecast: ImmutableList<ForecastRow> = persistentListOf(),
    val isRefreshing: Boolean = false,
    val threshold: Int = 75,
    val combinedAttendance: Boolean = false,
    val isOffline: Boolean = false,
    val hasData: Boolean = false,
    val semesterError: String? = null,
)

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AttendanceViewModel
    @Inject
    constructor(
        private val attendanceRepo: AttendanceRepository,
        private val timetableRepo: TimetableRepository,
        private val coordinator: AcademicDataCoordinator,
        private val attendanceUseCase: AttendanceUseCase,
        private val forecastUseCase: ForecastUseCase,
        private val timetableUseCase: TimetableUseCase,
        private val preferencesStore: PreferencesStore,
        networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        private val _state = MutableStateFlow(AttendanceUiState())
        val state: StateFlow<AttendanceUiState> = _state.asStateFlow()
        private val manualAttendanceError = MutableStateFlow<Throwable?>(null)
        private val manualTimetableError = MutableStateFlow<Throwable?>(null)

        init {
            viewModelScope.launch {
                coordinator.activeContext.collect {
                    _state.value = AttendanceUiState()
                    manualAttendanceError.value = null
                    manualTimetableError.value = null
                }
            }
            viewModelScope.launch {
                val semesterSnapshot = coordinator.observeAttendance(attendanceRepo) { manualAttendanceError.value = null }
                val weekSnapshot =
                    coordinator.activeContext.flatMapLatest { context ->
                        if (context == null || context.academicYear.isBlank()) {
                            flowOf(AcademicSnapshot<TimetableData>())
                        } else {
                            val (start, end) = timetableUseCase.getCurrentWeekRange()
                            timetableRepo.observeWeek(TimetableKey(context, start.toString(), end.toString()))
                        }
                    }
                val preferences =
                    coordinator.activeContext.flatMapLatest { context ->
                        if (context == null) {
                            flowOf(AttendancePreferences(null, 75, false, ""))
                        } else {
                            combine(
                                preferencesStore.getUserInt("attendance_threshold", 75),
                                preferencesStore.getUserBoolean("combined_attendance"),
                                preferencesStore.getUserString("semester_end_date", ""),
                            ) { threshold, combined, endDate ->
                                AttendancePreferences(context.admno, threshold, combined, endDate)
                            }
                        }
                    }
                val base =
                    combine(
                        semesterSnapshot,
                        weekSnapshot,
                        preferences,
                    ) { selection, week, prefs ->
                        val current = prefs.takeIf { it.owner == selection.first.context?.admno }
                        Presentation(
                            selection.first,
                            selection.second,
                            week,
                            current?.threshold ?: 75,
                            current?.combined ?: false,
                            current?.endDate.orEmpty(),
                        )
                    }
                combine(
                    base,
                    coordinator.attendanceDemandError,
                    coordinator.timetableDemandError,
                    manualAttendanceError,
                    manualTimetableError,
                ) { presentation, attendanceDemand, timetableDemand, attendanceManual, timetableManual ->
                    presentation.copy(
                        error =
                            presentation.selection.error ?: presentation.attendance.error ?: attendanceDemand
                                ?: attendanceManual ?: presentation.week.error ?: timetableDemand ?: timetableManual,
                    )
                }.collect { applyPresentation(it) }
            }
            viewModelScope.launch { networkMonitor.isOnline.collect { online -> _state.update { it.copy(isOffline = !online) } } }
        }

        @Suppress("TooGenericExceptionCaught")
        fun refresh() {
            if (_state.value.semesterError != null) viewModelScope.launch { coordinator.discoverSemester() }
            viewModelScope.launch {
                launch {
                    manualAttendanceError.value = null
                    val selection = coordinator.selectedSemester.value
                    try {
                        coordinator.refreshAttendance()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        if (coordinator.selectedSemester.value == selection) manualAttendanceError.value = error
                    }
                }
                launch {
                    manualTimetableError.value = null
                    val context = coordinator.activeContext.value
                    try {
                        coordinator.refreshTimetable()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        if (coordinator.activeContext.value == context) manualTimetableError.value = error
                    }
                }
            }
        }

        fun setThreshold(value: Int) {
            val context = coordinator.activeContext.value ?: return
            val key = preferencesStore.userScoped("attendance_threshold")
            viewModelScope.launch {
                if (coordinator.activeContext.value == context) preferencesStore.putInt(key, value.coerceIn(50, 95))
            }
        }

        fun setCombinedAttendance(enabled: Boolean) {
            val context = coordinator.activeContext.value ?: return
            val key = preferencesStore.userScoped("combined_attendance")
            viewModelScope.launch {
                if (coordinator.activeContext.value == context) preferencesStore.putBoolean(key, enabled)
            }
        }

        @Suppress("LongMethod")
        private fun applyPresentation(presentation: Presentation) {
            val selection = presentation.selection
            val snapshot = presentation.attendance
            val week = presentation.week
            val threshold = presentation.threshold
            val combined = presentation.combined
            val endDateText = presentation.endDate
            val data = snapshot.data
            val error = presentation.error?.let(ErrorText::forData)
            val raw = data?.table?.values.orEmpty().map { it.toSubjectAttendance() }
            val subjects = if (combined) attendanceUseCase.combineSubjects(raw) else raw
            val decorated =
                subjects.map { subject ->
                    DecoratedSubject(
                        subject,
                        attendanceUseCase.bunkBudget(subject.present, subject.total, threshold),
                        attendanceUseCase.mustAttend(subject.present, subject.total, threshold),
                        attendanceUseCase.tone(subject.percent, threshold),
                    )
                }.sortedBy { it.subject.percent }
            val endDate = ForecastEndDate.resolve(endDateText)
            val forecast = forecastUseCase.buildForecast(subjects, week.data?.weekly.orEmpty(), threshold, endDate)
            _state.update {
                it.copy(
                    isLoading = data == null && error == null,
                    error = error,
                    semesterError = selection.error?.let(ErrorText::forData),
                    overallPercent = data?.endrow?.percentage ?: 0.0,
                    overallPresent = data?.endrow?.present ?: 0,
                    overallTotal = data?.endrow?.total ?: 0,
                    overallTone = data?.let { attendanceUseCase.tone(it.endrow.percentage, threshold) } ?: AttendanceTone.OK,
                    subjects = decorated.toImmutableList(),
                    forecast = forecast.toImmutableList(),
                    isRefreshing = snapshot.refreshing,
                    threshold = threshold,
                    combinedAttendance = combined,
                    hasData = data != null,
                )
            }
        }

        private fun AttendanceEntry.toSubjectAttendance() = SubjectAttendance(subCode, subname, lecType, present, total, percent)

        private data class Presentation(
            val selection: AcademicSemesterSelection,
            val attendance: AcademicSnapshot<AttendanceResponse>,
            val week: AcademicSnapshot<TimetableData>,
            val threshold: Int,
            val combined: Boolean,
            val endDate: String,
            val error: Throwable? = null,
        )

        private data class AttendancePreferences(
            val owner: String?,
            val threshold: Int,
            val combined: Boolean,
            val endDate: String,
        )
    }
