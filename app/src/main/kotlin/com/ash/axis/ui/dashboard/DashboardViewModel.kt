package com.ash.axis.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.SELECTED_SEMESTER_CLASS_KEY
import com.ash.axis.data.repository.SELECTED_SEMESTER_YEAR_KEY
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceEntry
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.TimetableSlot
import com.ash.axis.domain.model.UserInfo
import com.ash.axis.domain.usecase.AttendanceTone
import com.ash.axis.domain.usecase.AttendanceUseCase
import com.ash.axis.domain.usecase.TimetableUseCase
import com.ash.axis.ui.ErrorText
import com.ash.core.network.NetworkMonitor
import com.ash.core.storage.PreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
    val isLoading: Boolean = true,
    val error: String? = null,
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
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
)

@HiltViewModel
@Suppress("TooGenericExceptionCaught")
class DashboardViewModel
    @Inject
    constructor(
        private val attendanceRepo: AttendanceRepository,
        private val timetableRepo: TimetableRepository,
        private val authRepository: AuthRepository,
        private val attendanceUseCase: AttendanceUseCase,
        private val timetableUseCase: TimetableUseCase,
        private val preferencesStore: PreferencesStore,
        private val networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        private val _state = MutableStateFlow(DashboardUiState())
        val state: StateFlow<DashboardUiState> = _state.asStateFlow()

        private var loadJob: Job? = null

        init {
            loadDashboard(forceRefresh = false)
            observeSemesterSelection()
        }

        fun refresh() {
            loadDashboard(forceRefresh = true)
        }

        // Cheap re-read of the shared attendance/timetable cache — called when Home becomes visible again, so a
        // refresh done on the Attendance tab (or a QR mark) is reflected here instead of showing a stale snapshot.
        fun syncFromCache() {
            loadDashboard(forceRefresh = false)
        }

        private fun observeSemesterSelection() {
            viewModelScope.launch {
                combine(
                    preferencesStore.getUserString(SELECTED_SEMESTER_YEAR_KEY),
                    preferencesStore.getUserString(SELECTED_SEMESTER_CLASS_KEY),
                ) { yearId, classId -> yearId to classId }
                    .drop(1)
                    .collect { loadDashboard(forceRefresh = false) }
            }
        }

        @Suppress("LongMethod")
        private fun loadDashboard(forceRefresh: Boolean) {
            // Coalesce overlapping non-forced loads (e.g. init + first ON_RESUME, or rapid tab switches) so we
            // don't fire duplicate fetches. A user-initiated pull-to-refresh always runs.
            if (!forceRefresh && loadJob?.isActive == true) return
            loadJob =
                viewModelScope.launch {
                    _state.update { it.copy(isRefreshing = forceRefresh, isLoading = !forceRefresh && it.subjects.isEmpty()) }

                    try {
                        val threshold = preferencesStore.getUserInt("attendance_threshold", 75).first()
                        val user = authRepository.getUserInfo() ?: error("Not logged in")
                        val firstName = user.name.split(" ").firstOrNull() ?: "there"
                        val semester = selectedSemester(user, forceRefresh)
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
                                        runCatching {
                                            timetableRepo.getTimetable(
                                                user.admno,
                                                user.brId,
                                                semester.yearId,
                                                weekStart.toString(),
                                                weekEnd.toString(),
                                                forceRefresh,
                                            )
                                        }.getOrDefault(emptyMap())
                                    }
                                attendanceDeferred.await() to timetableDeferred.await()
                            }

                        val computed =
                            withContext(Dispatchers.Default) {
                                val rawSubjects = attendance.table.values.map { it.toSubjectAttendance() }
                                val dashSubjects = rawSubjects.map { it.toDashboardSubject(threshold) }

                                val todayKey =
                                    java.time.LocalDate.now().dayOfWeek.getDisplayName(
                                        java.time.format.TextStyle.SHORT,
                                        java.util.Locale.ENGLISH,
                                    )
                                val todaySlots =
                                    timetableUseCase.sortSlotsByTime(timetable[todayKey] ?: emptyList())
                                        .map { slot ->
                                            TodaySlotDisplay(
                                                slot = slot,
                                                cleanName = timetableUseCase.displaySubjectName(slot),
                                                subjectCode =
                                                    (
                                                        slot.subCode.takeIf { it.isNotBlank() }
                                                            ?: slot.sub_shortname ?: slot.sub_short
                                                            ?: slot.subjectId
                                                    ).uppercase(),
                                                room = slot.roomno,
                                                lectType = slot.lectType.ifBlank { "Class" },
                                            )
                                        }

                                val nowMinutes = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
                                val nextClass =
                                    todaySlots
                                        .firstOrNull { timetableUseCase.timeToMinutes(it.slot.fromTime) > nowMinutes }
                                        ?.let {
                                            NextClassInfo(
                                                cleanName = it.cleanName,
                                                subjectCode = it.subjectCode,
                                                room = it.room,
                                                lectType = it.lectType,
                                                startMinutes = timetableUseCase.timeToMinutes(it.slot.fromTime),
                                            )
                                        }

                                val overallPct = attendance.endrow.percentage
                                DashboardUiState(
                                    isLoading = false,
                                    isRefreshing = false,
                                    error = null,
                                    firstName = firstName,
                                    overallPercent = overallPct,
                                    overallPresent = attendance.endrow.present,
                                    overallTotal = attendance.endrow.total,
                                    overallTone = attendanceUseCase.tone(overallPct, threshold),
                                    threshold = threshold,
                                    atRiskCount = attendanceUseCase.atRiskCount(rawSubjects, threshold),
                                    totalBunkable = attendanceUseCase.totalBunkable(rawSubjects, threshold),
                                    subjects = dashSubjects.toImmutableList(),
                                    todaySlots = todaySlots.toImmutableList(),
                                    nextClass = nextClass,
                                )
                            }

                        val offline = networkMonitor.isOnline.first().not()
                        _state.update { computed.copy(isOffline = offline) }
                    } catch (e: Exception) {
                        _state.update { it.copy(isLoading = false, isRefreshing = false, error = ErrorText.forData(e)) }
                    }
                }
        }

        private fun AttendanceEntry.toSubjectAttendance() =
            com.ash.axis.domain.usecase.SubjectAttendance(
                subCode = subCode,
                subName = subname,
                lecType = lecType,
                present = present,
                total = total,
                percent = percent,
            )

        private fun com.ash.axis.domain.usecase.SubjectAttendance.toDashboardSubject(threshold: Int) =
            DashboardSubject(
                subCode = subCode,
                subName = subName,
                lecType = lecType,
                present = present,
                total = total,
                percent = percent,
                tone = attendanceUseCase.tone(percent, threshold),
            )

        private suspend fun selectedSemester(
            user: UserInfo,
            forceRefresh: Boolean,
        ): SemesterOption {
            val yearId = preferencesStore.getUserString(SELECTED_SEMESTER_YEAR_KEY).first()
            val classId = preferencesStore.getUserString(SELECTED_SEMESTER_CLASS_KEY).first()
            return attendanceRepo.getPreferredSemester(user.admno, user.brId, yearId, classId, forceRefresh)
        }
    }
