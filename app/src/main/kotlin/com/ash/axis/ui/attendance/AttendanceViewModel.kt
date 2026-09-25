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
import com.ash.axis.domain.usecase.AttendanceTone
import com.ash.axis.domain.usecase.AttendanceUseCase
import com.ash.axis.domain.usecase.ForecastRow
import com.ash.axis.domain.usecase.ForecastUseCase
import com.ash.axis.domain.usecase.SubjectAttendance
import com.ash.axis.domain.usecase.TimetableUseCase
import com.ash.axis.ui.ErrorText
import com.ash.axis.ui.academics.SemesterSelection
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
import kotlinx.coroutines.flow.flow
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
        private val semesterRetry = MutableStateFlow(0L)
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
                var observedKey: AttendanceKey? = null
                val semesterSnapshot =
                    selectedSemester(
                        coordinator,
                        attendanceRepo,
                        preferencesStore,
                        resolveLabel = true,
                        retry = semesterRetry,
                    ).flatMapLatest { selection ->
                        val context = coordinator.activeContext.value
                        val option = selection.option
                        val current = if (selection.context == context) selection else SemesterSelection(context)
                        val key =
                            if (context != null && option != null && selection.context == context) {
                                AttendanceKey(context, option.classId, option.yearId)
                            } else {
                                null
                            }
                        flow {
                            if (key != observedKey) {
                                observedKey = key
                                emit(current to AcademicSnapshot<AttendanceResponse>())
                            }
                            if (key == null) {
                                emit(current to AcademicSnapshot<AttendanceResponse>())
                            } else {
                                attendanceRepo.observeSummary(key).collect { emit(current to it) }
                            }
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
                val base =
                    combine(
                        semesterSnapshot,
                        weekSnapshot,
                        preferencesStore.getUserInt("attendance_threshold", 75),
                        preferencesStore.getUserBoolean("combined_attendance"),
                        preferencesStore.getUserString("semester_end_date", ""),
                    ) { selection, week, threshold, combined, endDate ->
                        Presentation(selection.first, selection.second, week, threshold, combined, endDate)
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
            if (_state.value.semesterError != null) semesterRetry.value++
            viewModelScope.launch {
                launch {
                    manualAttendanceError.value = null
                    try {
                        coordinator.refreshAttendance()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        manualAttendanceError.value = error
                    }
                }
                launch {
                    manualTimetableError.value = null
                    try {
                        coordinator.refreshTimetable()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        manualTimetableError.value = error
                    }
                }
            }
        }

        @Suppress("LongMethod")
        private fun applyPresentation(presentation: Presentation) {
            val selection = presentation.selection
            val semester = selection.option
            val snapshot = presentation.attendance
            val week = presentation.week
            val threshold = presentation.threshold
            val combined = presentation.combined
            val endDateText = presentation.endDate
            val data = snapshot.data
            val error = presentation.error?.let(ErrorText::forData)
            if (data == null) {
                _state.update {
                    it.copy(
                        isLoading = error == null,
                        error = error,
                        semesterError = selection.error?.let(ErrorText::forData),
                        semesterLabel = semester?.label.orEmpty(),
                        overallPercent = 0.0,
                        overallPresent = 0,
                        overallTotal = 0,
                        overallTone = AttendanceTone.OK,
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
                    error = error,
                    semesterError = selection.error?.let(ErrorText::forData),
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
            val selection: SemesterSelection,
            val attendance: AcademicSnapshot<AttendanceResponse>,
            val week: AcademicSnapshot<TimetableData>,
            val threshold: Int,
            val combined: Boolean,
            val endDate: String,
            val error: Throwable? = null,
        )
    }
