package com.ash.axis.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.repository.AttendanceKey
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.TimetableData
import com.ash.axis.data.repository.TimetableKey
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceEntry
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.usecase.AttendanceTone
import com.ash.axis.domain.usecase.AttendanceUseCase
import com.ash.axis.domain.usecase.ForecastRow
import com.ash.axis.domain.usecase.ForecastUseCase
import com.ash.axis.domain.usecase.SubjectAttendance
import com.ash.axis.domain.usecase.TimetableUseCase
import com.ash.axis.ui.ErrorText
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    val semesterLabel: String = "",
    val overallPercent: Double = 0.0,
    val overallPresent: Int = 0,
    val overallTotal: Int = 0,
    val overallTone: AttendanceTone = AttendanceTone.OK,
    val subjects: ImmutableList<DecoratedSubject> = persistentListOf(),
    val forecast: ImmutableList<ForecastRow> = persistentListOf(),
    val isRefreshing: Boolean = false,
    val threshold: Int = 75,
    val isOffline: Boolean = false,
    val hasData: Boolean = false,
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

        init {
            viewModelScope.launch {
                coordinator.activeContext.collect {
                    _state.value = AttendanceUiState()
                }
            }
            viewModelScope.launch {
                val semesterSnapshot =
                    selectedSemester(coordinator, attendanceRepo, preferencesStore, resolveLabel = true).flatMapLatest { semester ->
                        val context = coordinator.activeContext.value
                        if (context == null || semester == null) {
                            flowOf(semester to AcademicSnapshot<AttendanceResponse>())
                        } else {
                            attendanceRepo.observeSummary(AttendanceKey(context, semester.classId, semester.yearId))
                                .combine(flowOf(semester)) { snapshot, option -> option to snapshot }
                        }
                    }
                val weekSnapshot =
                    coordinator.activeContext.flatMapLatest { context ->
                        if (context == null || context.academicYear.isBlank()) {
                            flowOf(AcademicSnapshot<TimetableData>())
                        } else {
                            val (start, end) = timetableUseCase.getCurrentWeekRange()
                            timetableRepo.observeWeek(TimetableKey(context, start.toString(), end.toString()))
                        }
                    }
                combine(
                    semesterSnapshot,
                    weekSnapshot,
                    preferencesStore.getUserInt("attendance_threshold", 75),
                    preferencesStore.getUserBoolean("combined_attendance"),
                    preferencesStore.getUserString("semester_end_date", ""),
                ) { selection, week, threshold, combined, endDate ->
                    Presentation(selection.first, selection.second, week, threshold, combined, endDate)
                }.collect { applyPresentation(it) }
            }
            viewModelScope.launch {
                coordinator.attendanceDemandError.collect { error ->
                    if (error != null) _state.update { it.copy(isLoading = false, error = ErrorText.forData(error)) }
                }
            }
            viewModelScope.launch { networkMonitor.isOnline.collect { online -> _state.update { it.copy(isOffline = !online) } } }
        }

        @Suppress("TooGenericExceptionCaught")
        fun refresh() {
            viewModelScope.launch {
                launch {
                    try {
                        coordinator.refreshAttendance()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        _state.update { it.copy(isLoading = false, error = ErrorText.forData(error)) }
                    }
                }
                launch {
                    try {
                        coordinator.refreshTimetable()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        _state.update { it.copy(isLoading = false, error = ErrorText.forData(error)) }
                    }
                }
            }
        }

        private fun applyPresentation(presentation: Presentation) {
            val semester = presentation.semester
            val snapshot = presentation.attendance
            val week = presentation.week
            val threshold = presentation.threshold
            val combined = presentation.combined
            val endDateText = presentation.endDate
            val data = snapshot.data
            if (data == null) {
                _state.update {
                    it.copy(
                        isLoading = snapshot.error == null,
                        error = snapshot.error?.let(ErrorText::forData),
                        semesterLabel = semester?.label.orEmpty(),
                        subjects = persistentListOf(),
                        forecast = persistentListOf(),
                        isRefreshing = snapshot.refreshing,
                        hasData = false,
                    )
                }
                return
            }
            val raw = data.table.values.map { it.toSubjectAttendance() }
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
            val endDate = endDateText.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val forecast = forecastUseCase.buildForecast(subjects, week.data?.weekly.orEmpty(), threshold, endDate)
            _state.update {
                it.copy(
                    isLoading = false,
                    error = snapshot.error?.let(ErrorText::forData),
                    semesterLabel = semester?.label.orEmpty(),
                    overallPercent = data.endrow.percentage,
                    overallPresent = data.endrow.present,
                    overallTotal = data.endrow.total,
                    overallTone = attendanceUseCase.tone(data.endrow.percentage, threshold),
                    subjects = decorated.toImmutableList(),
                    forecast = forecast.toImmutableList(),
                    isRefreshing = snapshot.refreshing,
                    threshold = threshold,
                    hasData = true,
                )
            }
        }

        private fun AttendanceEntry.toSubjectAttendance() = SubjectAttendance(subCode, subname, lecType, present, total, percent)

        private data class Presentation(
            val semester: SemesterOption?,
            val attendance: AcademicSnapshot<AttendanceResponse>,
            val week: AcademicSnapshot<TimetableData>,
            val threshold: Int,
            val combined: Boolean,
            val endDate: String,
        )
    }
