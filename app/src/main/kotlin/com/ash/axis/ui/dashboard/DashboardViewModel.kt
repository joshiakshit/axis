package com.ash.axis.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicSemesterSelection
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.academic.observeAttendance
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
    val subjectCount: Int = 0,
    val todaySlots: ImmutableList<TodaySlotDisplay> = persistentListOf(),
    val nextClass: NextClassInfo? = null,
    val hasAttendance: Boolean = false,
    val hasTimetable: Boolean = false,
    val attendanceError: String? = null,
    val timetableError: String? = null,
    val semesterError: String? = null,
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
        private val manualAttendanceError = MutableStateFlow<Throwable?>(null)
        private val manualTimetableError = MutableStateFlow<Throwable?>(null)

        init {
            viewModelScope.launch {
                coordinator.activeContext.collect {
                    _state.value = DashboardUiState(firstName = authRepository.getUserInfo()?.name?.substringBefore(' ').orEmpty())
                    manualAttendanceError.value = null
                    manualTimetableError.value = null
                }
            }
            viewModelScope.launch {
                val summary = coordinator.observeAttendance(attendanceRepo) { manualAttendanceError.value = null }
                combine(
                    summary,
                    preferencesStore.getUserInt("attendance_threshold", 75),
                    coordinator.attendanceDemandError,
                    manualAttendanceError,
                ) { selection, threshold, demandError, manualError ->
                    AttendancePresentation(selection.first, selection.second, threshold, demandError, manualError)
                }.collect { applyAttendance(it) }
            }
            viewModelScope.launch {
                val week =
                    coordinator.activeContext.flatMapLatest { context ->
                        if (context == null || context.academicYear.isBlank()) {
                            flowOf(null)
                        } else {
                            val (start, end) = timetableUseCase.getCurrentWeekRange()
                            timetableRepo.observeWeek(TimetableKey(context, start.toString(), end.toString()))
                        }
                    }
                combine(week, coordinator.timetableDemandError, manualTimetableError) { snapshot, demand, manual ->
                    Triple(snapshot, demand, manual)
                }.collect { (snapshot, demand, manual) -> applyTimetable(snapshot, demand, manual) }
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

        fun onVisible() {
            if (_state.value.semesterError != null) viewModelScope.launch { coordinator.discoverSemester() }
            viewModelScope.launch { coordinator.homeVisible() }
        }

        private fun applyAttendance(presentation: AttendancePresentation) {
            val selection = presentation.selection
            val snapshot = presentation.snapshot
            val threshold = presentation.threshold
            val demandError = presentation.demandError
            val manualError = presentation.manualError
            val error = selection.error ?: snapshot.error ?: demandError ?: manualError
            val data = snapshot.data
            val raw = data?.table?.values.orEmpty().map { it.toSubjectAttendance() }
            _state.update {
                it.copy(
                    hasAttendance = data != null,
                    overallPercent = data?.endrow?.percentage ?: 0.0,
                    overallPresent = data?.endrow?.present ?: 0,
                    overallTotal = data?.endrow?.total ?: 0,
                    overallTone = data?.let { attendanceUseCase.tone(it.endrow.percentage, threshold) } ?: AttendanceTone.OK,
                    threshold = threshold,
                    atRiskCount = attendanceUseCase.atRiskCount(raw, threshold),
                    totalBunkable = attendanceUseCase.totalBunkable(raw, threshold),
                    subjectCount = raw.size,
                    attendanceError = error?.let(ErrorText::forData),
                    semesterError = selection.error?.let(ErrorText::forData),
                    attendanceRefreshing = snapshot.refreshing,
                )
            }
        }

        private fun applyTimetable(
            snapshot: AcademicSnapshot<TimetableData>?,
            demandError: Throwable?,
            manualError: Throwable?,
        ) {
            val error = snapshot?.error ?: demandError ?: manualError
            val data = snapshot?.data
            val today = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            val slots =
                timetableUseCase.sortSlotsByTime(data?.weekly?.get(today).orEmpty()).map { slot ->
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
                    hasTimetable = data != null,
                    todaySlots = slots.toImmutableList(),
                    nextClass = next,
                    timetableError = error?.let(ErrorText::forData),
                    timetableRefreshing = snapshot?.refreshing == true,
                )
            }
        }

        private fun AttendanceEntry.toSubjectAttendance() = SubjectAttendance(subCode, subname, lecType, present, total, percent)

        private data class AttendancePresentation(
            val selection: AcademicSemesterSelection,
            val snapshot: AcademicSnapshot<AttendanceResponse>,
            val threshold: Int,
            val demandError: Throwable?,
            val manualError: Throwable?,
        )
    }
