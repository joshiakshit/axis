package com.ash.axis.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.repository.AttendanceKey
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.TimetableData
import com.ash.axis.data.repository.TimetableKey
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceEntry
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.TimetableSlot
import com.ash.axis.domain.usecase.AttendanceTone
import com.ash.axis.domain.usecase.AttendanceUseCase
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
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class DashboardSubject(
    val subCode: String,
    val subName: String,
    val lecType: String,
    val present: Int,
    val total: Int,
    val percent: Double,
    val tone: AttendanceTone,
)

data class TodaySlotDisplay(
    val slot: TimetableSlot,
    val cleanName: String,
    val subjectCode: String,
    val room: String,
    val lectType: String,
)

data class NextClassInfo(
    val cleanName: String,
    val subjectCode: String,
    val room: String,
    val lectType: String,
    val startMinutes: Int,
)

data class DashboardUiState(
    val firstName: String = "",
    val overallPercent: Double = 0.0,
    val overallPresent: Int = 0,
    val overallTotal: Int = 0,
    val overallTone: AttendanceTone = AttendanceTone.OK,
    val threshold: Int = 75,
    val atRiskCount: Int = 0,
    val totalBunkable: Int = 0,
    val subjects: ImmutableList<DashboardSubject> = persistentListOf(),
    val todaySlots: ImmutableList<TodaySlotDisplay> = persistentListOf(),
    val nextClass: NextClassInfo? = null,
    val hasAttendance: Boolean = false,
    val hasTimetable: Boolean = false,
    val attendanceError: String? = null,
    val timetableError: String? = null,
    val attendanceRefreshing: Boolean = false,
    val timetableRefreshing: Boolean = false,
    val isOffline: Boolean = false,
) {
    val isRefreshing get() = attendanceRefreshing || timetableRefreshing

    fun needsFullScreenLoading() = !hasAttendance && !hasTimetable && attendanceError == null && timetableError == null

    fun needsFullScreenError() = !hasAttendance && !hasTimetable && (attendanceError != null || timetableError != null)
}

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DashboardViewModel
    @Inject
    constructor(
        private val attendanceRepo: AttendanceRepository,
        private val timetableRepo: TimetableRepository,
        private val coordinator: AcademicDataCoordinator,
        authRepository: AuthRepository,
        private val attendanceUseCase: AttendanceUseCase,
        private val timetableUseCase: TimetableUseCase,
        preferencesStore: PreferencesStore,
        networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        private val _state = MutableStateFlow(DashboardUiState())
        val state: StateFlow<DashboardUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                coordinator.activeContext.collect {
                    _state.value = DashboardUiState(firstName = authRepository.getUserInfo()?.name?.substringBefore(' ').orEmpty())
                }
            }
            viewModelScope.launch {
                selectedSemester(coordinator, attendanceRepo, preferencesStore).flatMapLatest { semester ->
                    val context = coordinator.activeContext.value
                    if (context == null || semester == null) {
                        flowOf(AcademicSnapshot<AttendanceResponse>())
                    } else {
                        attendanceRepo.observeSummary(AttendanceKey(context, semester.classId, semester.yearId))
                    }
                }.combine(preferencesStore.getUserInt("attendance_threshold", 75)) { snapshot, threshold ->
                    snapshot to threshold
                }.collect { (snapshot, threshold) -> applyAttendance(snapshot, threshold) }
            }
            viewModelScope.launch {
                coordinator.activeContext.flatMapLatest { context ->
                    if (context == null || context.academicYear.isBlank()) {
                        flowOf(null)
                    } else {
                        val (start, end) = timetableUseCase.getCurrentWeekRange()
                        timetableRepo.observeWeek(TimetableKey(context, start.toString(), end.toString()))
                    }
                }.collect { applyTimetable(it) }
            }
            viewModelScope.launch {
                combine(coordinator.attendanceDemandError, coordinator.timetableDemandError) { attendance, timetable ->
                    attendance to timetable
                }.collect { (attendance, timetable) ->
                    _state.update {
                        it.copy(
                            attendanceError = attendance?.let(ErrorText::forData) ?: it.attendanceError,
                            timetableError = timetable?.let(ErrorText::forData) ?: it.timetableError,
                        )
                    }
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
                        _state.update { it.copy(attendanceError = ErrorText.forData(error)) }
                    }
                }
                launch {
                    try {
                        coordinator.refreshTimetable()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        _state.update { it.copy(timetableError = ErrorText.forData(error)) }
                    }
                }
            }
        }

        fun onVisible() {
            viewModelScope.launch { coordinator.homeVisible() }
        }

        private fun applyAttendance(
            snapshot: AcademicSnapshot<AttendanceResponse>,
            threshold: Int,
        ) {
            val data = snapshot.data
            if (data == null) {
                _state.update {
                    it.copy(
                        hasAttendance = false,
                        subjects = persistentListOf(),
                        attendanceError = snapshot.error?.let(ErrorText::forData),
                        threshold = threshold,
                        attendanceRefreshing = snapshot.refreshing,
                    )
                }
                return
            }
            val raw = data.table.values.map { it.toSubjectAttendance() }
            val subjects =
                raw.map { subject ->
                    DashboardSubject(
                        subject.subCode,
                        subject.subName,
                        subject.lecType,
                        subject.present,
                        subject.total,
                        subject.percent,
                        attendanceUseCase.tone(subject.percent, threshold),
                    )
                }
            _state.update {
                it.copy(
                    hasAttendance = true,
                    overallPercent = data.endrow.percentage,
                    overallPresent = data.endrow.present,
                    overallTotal = data.endrow.total,
                    overallTone = attendanceUseCase.tone(data.endrow.percentage, threshold),
                    threshold = threshold,
                    atRiskCount = attendanceUseCase.atRiskCount(raw, threshold),
                    totalBunkable = attendanceUseCase.totalBunkable(raw, threshold),
                    subjects = subjects.toImmutableList(),
                    attendanceError = snapshot.error?.let(ErrorText::forData),
                    attendanceRefreshing = snapshot.refreshing,
                )
            }
        }

        private fun applyTimetable(snapshot: AcademicSnapshot<TimetableData>?) {
            val data = snapshot?.data
            if (data == null) {
                _state.update {
                    it.copy(
                        hasTimetable = false,
                        todaySlots = persistentListOf(),
                        nextClass = null,
                        timetableError = snapshot?.error?.let(ErrorText::forData),
                        timetableRefreshing = snapshot?.refreshing == true,
                    )
                }
                return
            }
            val today = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            val slots =
                timetableUseCase.sortSlotsByTime(data.weekly[today].orEmpty()).map { slot ->
                    TodaySlotDisplay(
                        slot,
                        timetableUseCase.displaySubjectName(slot),
                        (slot.subCode.takeIf(String::isNotBlank) ?: slot.sub_shortname ?: slot.sub_short ?: slot.subjectId).uppercase(),
                        slot.roomno,
                        slot.lectType.ifBlank { "Class" },
                    )
                }
            val now = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
            val next =
                slots.firstOrNull { timetableUseCase.timeToMinutes(it.slot.fromTime) > now }?.let {
                    NextClassInfo(it.cleanName, it.subjectCode, it.room, it.lectType, timetableUseCase.timeToMinutes(it.slot.fromTime))
                }
            _state.update {
                it.copy(
                    hasTimetable = true,
                    todaySlots = slots.toImmutableList(),
                    nextClass = next,
                    timetableError = snapshot.error?.let(ErrorText::forData),
                    timetableRefreshing = snapshot.refreshing,
                )
            }
        }

        private fun AttendanceEntry.toSubjectAttendance() = SubjectAttendance(subCode, subname, lecType, present, total, percent)
    }
