package com.ash.axis.data.academic

import com.ash.axis.data.repository.AttendanceKey
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.DaywiseKey
import com.ash.axis.data.repository.TimetableKey
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.StudentRequestContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

enum class AcademicDestination { HOME, ATTENDANCE, TIMETABLE, DAYWISE, PLANNER, OTHER }

data class DaywiseRange(val year: String, val fromDate: String, val toDate: String)

/** Starts only the current visible demand and the two core warmup loads. */
@Singleton
@Suppress("TooGenericExceptionCaught")
class AcademicDataCoordinator
    @Inject
    constructor(
        private val attendance: AttendanceRepository,
        private val timetable: TimetableRepository,
        private val auth: AuthRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mutex = Mutex()
        private var active: StudentRequestContext? = null
        private val mutableActiveContext = MutableStateFlow<StudentRequestContext?>(null)
        val activeContext: StateFlow<StudentRequestContext?> = mutableActiveContext
        private val mutableAttendanceDemandError = MutableStateFlow<Throwable?>(null)
        val attendanceDemandError: StateFlow<Throwable?> = mutableAttendanceDemandError
        private val mutableTimetableDemandError = MutableStateFlow<Throwable?>(null)
        val timetableDemandError: StateFlow<Throwable?> = mutableTimetableDemandError
        private val mutableDaywiseDemandError = MutableStateFlow<Throwable?>(null)
        val daywiseDemandError: StateFlow<Throwable?> = mutableDaywiseDemandError
        private var semester: SemesterOption? = null
        private var currentWeek: Pair<String, String>? = null
        private var generation = 0L

        suspend fun activate(
            context: StudentRequestContext,
            selectedSemester: SemesterOption?,
            weekStart: LocalDate,
            destination: AcademicDestination = AcademicDestination.HOME,
            visibleDaywise: DaywiseRange? = null,
        ) {
            mutex.withLock {
                attendance.deactivateAcademicData()
                timetable.deactivateAcademicData()
                generation++
                active = context
                mutableActiveContext.value = context
                semester = selectedSemester
                currentWeek = weekRange(weekStart)
                mutableAttendanceDemandError.value = null
                mutableTimetableDemandError.value = null
                mutableDaywiseDemandError.value = null
            }
            when (destination) {
                AcademicDestination.ATTENDANCE -> attendanceVisible()
                AcademicDestination.TIMETABLE -> timetableVisible(weekStart)
                AcademicDestination.PLANNER -> plannerVisible(weekStart)
                AcademicDestination.DAYWISE -> {
                    val range = requireNotNull(visibleDaywise) { "Day-wise activation needs its visible range" }
                    daywiseVisible(range.year, range.fromDate, range.toDate)
                }
                AcademicDestination.OTHER -> otherVisible()
                else -> warmCore()
            }
        }

        suspend fun deactivate() {
            mutex.withLock {
                generation++
                active = null
                mutableActiveContext.value = null
                semester = null
                currentWeek = null
                mutableAttendanceDemandError.value = null
                mutableTimetableDemandError.value = null
                mutableDaywiseDemandError.value = null
                attendance.deactivateAcademicData()
                timetable.deactivateAcademicData()
            }
        }

        suspend fun selectSemester(option: SemesterOption) {
            mutex.withLock { semester = option }
            attendanceVisible()
        }

        suspend fun attendanceVisible() {
            startBoth(::safeSummary, ::safeWeek)
        }

        suspend fun timetableVisible(weekStart: LocalDate) {
            mutex.withLock { currentWeek = weekRange(weekStart) }
            startBoth(::safeWeek, ::safeSummary)
        }

        suspend fun homeVisible() = warmCore()

        suspend fun otherVisible() {
            scope.launch { warmCore() }
        }

        suspend fun daywiseVisible(
            year: String,
            fromDate: String,
            toDate: String,
        ) {
            startBoth(
                { safeDaywise(year, fromDate, toDate) },
                { warmCore() },
            )
        }

        suspend fun plannerVisible(weekStart: LocalDate) = timetableVisible(weekStart)

        suspend fun refreshAttendance() = requestSummary(force = true)

        suspend fun refreshDaywise(
            year: String,
            fromDate: String,
            toDate: String,
        ) {
            mutex.withLock {
                val context = active ?: return
                val key = DaywiseKey(context, year, fromDate, toDate)
                attendance.requestDaywise(key, force = true)
            }
        }

        suspend fun refreshTimetable() {
            val (before, observedGeneration) = mutex.withLock { Pair(active ?: return, generation) }
            val refreshed = auth.requireStudentRequestContext(forceProfileRefresh = true)
            mutex.withLock {
                val current = active ?: return
                if (generation != observedGeneration || current != before || !sameAccount(current, refreshed)) return
                active = refreshed
                mutableActiveContext.value = refreshed
                val week = currentWeek ?: return
                timetable.requestWeek(TimetableKey(refreshed, week.first, week.second), force = true)
            }
        }

        suspend fun qrSucceeded(origin: StudentRequestContext) {
            mutex.withLock {
                val context = active ?: return
                if (!sameAccount(context, origin)) return
                semester?.let { attendance.invalidateSummary(AttendanceKey(context, it.classId, it.yearId)) }
                attendance.invalidateDaywiseForAccount(context)
            }
        }

        suspend fun clearAcademicCache() {
            mutex.withLock {
                generation++
                attendance.clearCache()
                timetable.clearCache()
            }
        }

        private suspend fun warmCore() {
            startBoth(::safeSummary, ::safeWeek)
        }

        private suspend fun startBoth(
            visible: suspend () -> Unit,
            next: suspend () -> Unit,
        ) {
            supervisorScope {
                val visibleLoad = async(start = CoroutineStart.UNDISPATCHED) { visible() }
                val nextLoad = async(start = CoroutineStart.UNDISPATCHED) { next() }
                visibleLoad.await()
                nextLoad.await()
            }
        }

        private suspend fun safeSummary() {
            val observedGeneration = mutex.withLock { generation }
            try {
                requestSummary()
                mutex.withLock { if (generation == observedGeneration) mutableAttendanceDemandError.value = null }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutex.withLock { if (generation == observedGeneration) mutableAttendanceDemandError.value = error }
            }
        }

        private suspend fun safeWeek() {
            val observedGeneration = mutex.withLock { generation }
            try {
                requestCurrentWeek()
                mutex.withLock { if (generation == observedGeneration) mutableTimetableDemandError.value = null }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutex.withLock { if (generation == observedGeneration) mutableTimetableDemandError.value = error }
            }
        }

        private suspend fun safeDaywise(
            year: String,
            fromDate: String,
            toDate: String,
        ) {
            val observedGeneration = mutex.withLock { generation }
            try {
                mutex.withLock {
                    val context = active ?: return
                    attendance.requestDaywise(DaywiseKey(context, year, fromDate, toDate))
                }
                mutex.withLock { if (generation == observedGeneration) mutableDaywiseDemandError.value = null }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutex.withLock { if (generation == observedGeneration) mutableDaywiseDemandError.value = error }
            }
        }

        private suspend fun requestSummary(force: Boolean = false) {
            mutex.withLock {
                val context = active ?: return
                val selected = semester ?: return
                attendance.requestSummary(AttendanceKey(context, selected.classId, selected.yearId), force)
            }
        }

        private suspend fun requestCurrentWeek() {
            val state = mutex.withLock { Pair(active ?: return, generation) }
            val context =
                if (state.first.academicYear.isNotBlank()) {
                    state.first
                } else {
                    val resolved = auth.requireStudentRequestContext()
                    mutex.withLock {
                        if (generation != state.second || active != state.first || !sameAccount(state.first, resolved)) return
                        active = resolved
                        mutableActiveContext.value = resolved
                    }
                    resolved
                }
            mutex.withLock {
                if (generation != state.second || active != context) return
                val week = currentWeek ?: return
                timetable.requestWeek(TimetableKey(context, week.first, week.second))
            }
        }

        private fun weekRange(start: LocalDate): Pair<String, String> = start.toString() to start.plusDays(6).toString()

        private fun sameAccount(
            left: StudentRequestContext,
            right: StudentRequestContext,
        ): Boolean = left.admno == right.admno && left.brId == right.brId && left.clientId == right.clientId
    }
