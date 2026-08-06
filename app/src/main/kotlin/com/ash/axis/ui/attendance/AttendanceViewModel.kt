package com.ash.axis.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.SELECTED_SEMESTER_CLASS_KEY
import com.ash.axis.data.repository.SELECTED_SEMESTER_YEAR_KEY
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceEntry
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.UserInfo
import com.ash.axis.domain.usecase.AttendanceTone
import com.ash.axis.domain.usecase.AttendanceUseCase
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
import kotlinx.coroutines.Dispatchers
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
import java.time.LocalDate
import javax.inject.Inject

data class DecoratedSubject(
    val subject: SubjectAttendance,
    val bunkable: Int,
    val need: Int,
    val tone: com.ash.axis.domain.usecase.AttendanceTone,
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
)

@HiltViewModel
@Suppress("TooGenericExceptionCaught")
class AttendanceViewModel
    @Inject
    constructor(
        private val attendanceRepo: AttendanceRepository,
        private val timetableRepo: TimetableRepository,
        private val authRepository: AuthRepository,
        private val attendanceUseCase: AttendanceUseCase,
        private val forecastUseCase: ForecastUseCase,
        private val timetableUseCase: TimetableUseCase,
        private val preferencesStore: PreferencesStore,
        private val networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        private val _state = MutableStateFlow(AttendanceUiState())
        val state: StateFlow<AttendanceUiState> = _state.asStateFlow()

        init {
            load(forceRefresh = false)
            observeSemesterSelection()
        }

        fun refresh() = load(forceRefresh = true)

        private fun observeSemesterSelection() {
            viewModelScope.launch {
                combine(
                    preferencesStore.getUserString(SELECTED_SEMESTER_YEAR_KEY),
                    preferencesStore.getUserString(SELECTED_SEMESTER_CLASS_KEY),
                ) { yearId, classId -> yearId to classId }
                    .drop(1)
                    .collect { load(forceRefresh = false) }
            }
        }

        @Suppress("LongMethod")
        private fun load(forceRefresh: Boolean) {
            viewModelScope.launch {
                _state.update { it.copy(isRefreshing = forceRefresh, isLoading = !forceRefresh && it.subjects.isEmpty()) }
                try {
                    val user = authRepository.getUserInfo() ?: error("Not logged in")
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
                                    timetableRepo.getTimetable(
                                        user.admno,
                                        user.brId,
                                        semester.yearId,
                                        weekStart.toString(),
                                        weekEnd.toString(),
                                        forceRefresh,
                                    )
                                }
                            attendanceDeferred.await() to timetableDeferred.await()
                        }

                    val threshold = preferencesStore.getUserInt("attendance_threshold", 75).first()
                    val combinedPref = preferencesStore.getUserBoolean("combined_attendance").first()
                    val endDateStr = preferencesStore.getUserString("semester_end_date", "").first()
                    val endDate = endDateStr.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

                    val (decorated, forecast) =
                        withContext(Dispatchers.Default) {
                            val rawSubjects = attendance.table.values.map { it.toSubjectAttendance() }
                            val subjects = if (combinedPref) attendanceUseCase.combineSubjects(rawSubjects) else rawSubjects
                            val dec =
                                subjects.map { s ->
                                    DecoratedSubject(
                                        subject = s,
                                        bunkable = attendanceUseCase.bunkBudget(s.present, s.total, threshold),
                                        need = attendanceUseCase.mustAttend(s.present, s.total, threshold),
                                        tone = attendanceUseCase.tone(s.percent, threshold),
                                    )
                                }.sortedBy { it.subject.percent }
                            val fc = forecastUseCase.buildForecast(subjects, timetable, threshold, endDate)
                            dec to fc
                        }

                    val overallPct = attendance.endrow.percentage
                    val offline = networkMonitor.isOnline.first().not()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                            semesterLabel = semester.label,
                            overallPercent = overallPct,
                            overallPresent = attendance.endrow.present,
                            overallTotal = attendance.endrow.total,
                            overallTone = attendanceUseCase.tone(overallPct, threshold),
                            threshold = threshold,
                            subjects = decorated.toImmutableList(),
                            forecast = forecast.toImmutableList(),
                            isOffline = offline,
                        )
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(isLoading = false, isRefreshing = false, error = ErrorText.forData(e)) }
                }
            }
        }

        private fun AttendanceEntry.toSubjectAttendance() =
            SubjectAttendance(
                subCode = subCode,
                subName = subname,
                lecType = lecType,
                present = present,
                total = total,
                percent = percent,
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
