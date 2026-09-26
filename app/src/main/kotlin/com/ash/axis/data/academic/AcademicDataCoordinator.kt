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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

enum class AcademicDestination { HOME, ATTENDANCE, TIMETABLE, DAYWISE, PLANNER, OTHER }

data class DaywiseRange(val year: String, val fromDate: String, val toDate: String)

data class AcademicSemesterSelection(
    val context: StudentRequestContext? = null,
    val option: SemesterOption? = null,
    val loading: Boolean = false,
    val error: Throwable? = null,
)

/** Starts only the current visible demand and the two core warmup loads. */
@Singleton
@Suppress("TooGenericExceptionCaught", "TooManyFunctions", "ComplexCondition")
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
        private val mutableSelectedSemester = MutableStateFlow(AcademicSemesterSelection())
        val selectedSemester: StateFlow<AcademicSemesterSelection> = mutableSelectedSemester
        private val mutableAttendanceDemandError = MutableStateFlow<Throwable?>(null)
        val attendanceDemandError: StateFlow<Throwable?> = mutableAttendanceDemandError
        private val mutableTimetableDemandError = MutableStateFlow<Throwable?>(null)
        val timetableDemandError: StateFlow<Throwable?> = mutableTimetableDemandError
        private val mutableDaywiseDemandError = MutableStateFlow<Throwable?>(null)
        val daywiseDemandError: StateFlow<Throwable?> = mutableDaywiseDemandError
        private var semester: SemesterOption? = null
        private var currentWeek: Pair<String, String>? = null
        private var visibleWeek: Pair<String, String>? = null
        private var destination = AcademicDestination.HOME
        private var generation = 0L
        private var selectionVersion = 0L
        private var summaryDemand = 0L
        private var weekDemand = 0L
        private var daywiseDemand = 0L
        private val mutableTransition = MutableStateFlow(0L)
        val transition: StateFlow<Long> = mutableTransition
        private val prefetchQueue = LinkedHashSet<TimetableKey>()
        private var prefetchWorker: Job? = null
        private var pendingQrOrigin: StudentRequestContext? = null

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
                mutableTransition.value = generation
                prefetchWorker?.cancel()
                prefetchWorker = null
                prefetchQueue.clear()
                active = context
                mutableActiveContext.value = context
                semester = selectedSemester
                selectionVersion++
                currentWeek = weekRange(weekStart)
                visibleWeek = currentWeek
                this.destination = destination
                mutableSelectedSemester.value = AcademicSemesterSelection(context, selectedSemester)
                mutableAttendanceDemandError.value = null
                mutableTimetableDemandError.value = null
                mutableDaywiseDemandError.value = null
                summaryDemand++
                weekDemand++
                daywiseDemand++
                if (pendingQrOrigin?.let { sameAccount(it, context) } == true) {
                    attendance.invalidateDaywiseForAccount(context)
                    selectedSemester?.let { applyPendingQr(context, it) }
                }
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
                mutableTransition.value = generation
                prefetchWorker?.cancel()
                prefetchWorker = null
                prefetchQueue.clear()
                active = null
                mutableActiveContext.value = null
                semester = null
                selectionVersion++
                currentWeek = null
                visibleWeek = null
                destination = AcademicDestination.HOME
                mutableSelectedSemester.value = AcademicSemesterSelection()
                mutableAttendanceDemandError.value = null
                mutableTimetableDemandError.value = null
                mutableDaywiseDemandError.value = null
                summaryDemand++
                weekDemand++
                daywiseDemand++
                attendance.deactivateAcademicData()
                timetable.deactivateAcademicData()
            }
        }

        suspend fun discoverSemester() {
            val (context, observed) =
                mutex.withLock {
                    val context = active ?: return
                    selectionVersion++
                    mutableSelectedSemester.value = AcademicSemesterSelection(context, semester, loading = true)
                    context to (generation to selectionVersion)
                }
            try {
                val option = attendance.getLatestSemester(context.admno, context.brId)
                mutex.withLock {
                    val current = active ?: return
                    if (!sameAccount(current, context) || generation != observed.first || selectionVersion != observed.second) return
                    semester = option
                    mutableSelectedSemester.value = AcademicSemesterSelection(current, option)
                    applyPendingQr(current, option)
                }
                safeSummary()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutex.withLock {
                    val current = active
                    if (current != null && sameAccount(current, context) && generation == observed.first &&
                        selectionVersion == observed.second
                    ) {
                        mutableSelectedSemester.value = AcademicSemesterSelection(current, semester, error = error)
                    }
                }
            }
        }

        suspend fun attendanceVisible() {
            mutex.withLock { destination = AcademicDestination.ATTENDANCE }
            startBoth(::safeSummary, { safeWeek(visible = false) })
        }

        suspend fun timetableVisible(weekStart: LocalDate) {
            mutex.withLock {
                destination = AcademicDestination.TIMETABLE
                visibleWeek = weekRange(weekStart)
            }
            startBoth({ safeWeek(visible = true) }, ::safeSummary)
            prefetchAdjacent(weekStart)
        }

        suspend fun prefetchAdjacent(weekStart: LocalDate) {
            mutex.withLock {
                val context = active?.takeIf { it.academicYear.isNotBlank() } ?: return
                prefetchQueue.clear()
                listOf(weekStart.minusWeeks(1), weekStart.plusWeeks(1)).forEach { week ->
                    prefetchQueue += TimetableKey(context, week.toString(), week.plusDays(6).toString())
                }
                startPrefetchWorker()
            }
        }

        suspend fun homeVisible(weekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) {
            mutex.withLock {
                destination = AcademicDestination.HOME
                currentWeek = weekRange(weekStart)
            }
            warmCore()
        }

        suspend fun otherVisible() {
            mutex.withLock { destination = AcademicDestination.OTHER }
            scope.launch { warmCore() }
        }

        suspend fun daywiseVisible(
            year: String,
            fromDate: String,
            toDate: String,
        ) {
            mutex.withLock { destination = AcademicDestination.DAYWISE }
            startBoth(
                { safeDaywise(year, fromDate, toDate) },
                { warmCore() },
            )
        }

        suspend fun plannerVisible(weekStart: LocalDate) {
            mutex.withLock {
                destination = AcademicDestination.PLANNER
                currentWeek = weekRange(weekStart)
            }
            startBoth({ safeWeek(visible = false) }, ::safeSummary)
        }

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
                if (current != refreshed) {
                    generation++
                    mutableTransition.value = generation
                    prefetchWorker?.cancel()
                    prefetchWorker = null
                    prefetchQueue.clear()
                }
                active = refreshed
                mutableSelectedSemester.value = mutableSelectedSemester.value.copy(context = refreshed)
                mutableActiveContext.value = refreshed
                val week = if (destination == AcademicDestination.TIMETABLE) visibleWeek else currentWeek
                if (week == null) return
                timetable.requestWeek(TimetableKey(refreshed, week.first, week.second), force = true)
            }
        }

        suspend fun qrSucceeded(origin: StudentRequestContext) {
            mutex.withLock {
                val context = active
                if (context == null) {
                    val user = auth.getUserInfo() ?: return
                    if (user.admno == origin.admno && user.brId == origin.brId && user.clientId == origin.clientId) {
                        pendingQrOrigin = origin
                    }
                    return
                }
                if (!sameAccount(context, origin)) return
                val selected = semester
                if (selected == null) {
                    pendingQrOrigin = origin
                } else {
                    attendance.invalidateSummary(AttendanceKey(context, selected.classId, selected.yearId))
                }
                attendance.invalidateDaywiseForAccount(context)
            }
        }

        private suspend fun applyPendingQr(
            context: StudentRequestContext,
            option: SemesterOption,
        ) {
            if (pendingQrOrigin?.let { sameAccount(it, context) } == true) {
                pendingQrOrigin = null
                attendance.invalidateSummary(AttendanceKey(context, option.classId, option.yearId))
            }
        }

        suspend fun clearAcademicCache() {
            mutex.withLock {
                generation++
                mutableTransition.value = generation
                summaryDemand++
                weekDemand++
                daywiseDemand++
                mutableAttendanceDemandError.value = null
                mutableTimetableDemandError.value = null
                mutableDaywiseDemandError.value = null
                prefetchWorker?.cancel()
                prefetchWorker = null
                prefetchQueue.clear()
                attendance.clearCache()
                timetable.clearCache()
            }
        }

        @Suppress("CyclomaticComplexMethod")
        private fun startPrefetchWorker() {
            if (prefetchWorker?.isActive == true) return
            val owner = generation
            prefetchWorker =
                scope.launch {
                    while (true) {
                        val next =
                            mutex.withLock {
                                if (generation != owner) return@launch
                                val prefetch = prefetchQueue.firstOrNull() ?: return@launch
                                prefetchQueue.remove(prefetch)
                                prefetch
                            }
                        val key = next
                        val visible = mutex.withLock { visibleWeek == (key.startDate to key.endDate) }
                        if (visible) continue
                        try {
                            val visibleKey = mutex.withLock { visibleWeek?.let { TimetableKey(key.context, it.first, it.second) } }
                            visibleKey?.let { timetable.observeWeek(it).first { snapshot -> !snapshot.refreshing } }
                            val snapshot = timetable.requestPrefetchWeek(key)
                            snapshot.first { !it.refreshing }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            // The keyed repository snapshot owns range errors.
                        }
                    }
                }
        }

        private suspend fun warmCore() {
            startBoth(::safeSummary, { safeWeek(visible = false) })
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
            val observed =
                mutex.withLock {
                    mutableAttendanceDemandError.value = null
                    generation to ++summaryDemand
                }
            try {
                requestSummary()
                mutex.withLock {
                    if (generation == observed.first && summaryDemand == observed.second) mutableAttendanceDemandError.value = null
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutex.withLock {
                    if (generation == observed.first && summaryDemand == observed.second) mutableAttendanceDemandError.value = error
                }
            }
        }

        private suspend fun safeWeek(visible: Boolean) {
            val observed =
                mutex.withLock {
                    mutableTimetableDemandError.value = null
                    generation to ++weekDemand
                }
            try {
                requestWeek(visible)
                mutex.withLock {
                    if (generation == observed.first && weekDemand == observed.second) mutableTimetableDemandError.value = null
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutex.withLock {
                    if (generation == observed.first && weekDemand == observed.second) mutableTimetableDemandError.value = error
                }
            }
        }

        private suspend fun safeDaywise(
            year: String,
            fromDate: String,
            toDate: String,
        ) {
            val observed =
                mutex.withLock {
                    mutableDaywiseDemandError.value = null
                    generation to ++daywiseDemand
                }
            try {
                mutex.withLock {
                    val context = active ?: return
                    attendance.requestDaywise(DaywiseKey(context, year, fromDate, toDate))
                }
                mutex.withLock {
                    if (generation == observed.first && daywiseDemand == observed.second) mutableDaywiseDemandError.value = null
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutex.withLock {
                    if (generation == observed.first && daywiseDemand == observed.second) mutableDaywiseDemandError.value = error
                }
            }
        }

        private suspend fun requestSummary(force: Boolean = false) {
            mutex.withLock {
                val context = active ?: return
                val selected = semester ?: return
                attendance.requestSummary(AttendanceKey(context, selected.classId, selected.yearId), force)
            }
        }

        private suspend fun requestWeek(visible: Boolean) {
            val state = mutex.withLock { Pair(active ?: return, generation) }
            val context =
                if (state.first.academicYear.isNotBlank()) {
                    state.first
                } else {
                    val resolved = auth.requireStudentRequestContext()
                    mutex.withLock {
                        if (generation != state.second || active != state.first || !sameAccount(state.first, resolved)) return
                        active = resolved
                        mutableSelectedSemester.value = mutableSelectedSemester.value.copy(context = resolved)
                        mutableActiveContext.value = resolved
                    }
                    resolved
                }
            mutex.withLock {
                if (generation != state.second || active != context) return
                val week = if (visible) visibleWeek else currentWeek
                if (week == null) return
                timetable.requestWeek(TimetableKey(context, week.first, week.second))
            }
        }

        private fun weekRange(start: LocalDate): Pair<String, String> = start.toString() to start.plusDays(6).toString()

        private fun sameAccount(
            left: StudentRequestContext,
            right: StudentRequestContext,
        ): Boolean = left.admno == right.admno && left.brId == right.brId && left.clientId == right.clientId
    }
