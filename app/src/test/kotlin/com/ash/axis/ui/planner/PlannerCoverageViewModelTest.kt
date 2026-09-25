package com.ash.axis.ui.planner

import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicSemesterSelection
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.CalendarRepository
import com.ash.axis.data.repository.StudentMarkerRepository
import com.ash.axis.data.repository.TimetableData
import com.ash.axis.data.repository.TimetableKey
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.axis.domain.model.TimetableSlot
import com.ash.axis.domain.model.UserInfo
import com.ash.axis.domain.usecase.AttendanceUseCase
import com.ash.axis.domain.usecase.PlannerUseCase
import com.ash.axis.domain.usecase.TodayAttendance
import com.ash.core.network.NetworkMonitor
import com.ash.core.storage.PreferencesStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerCoverageViewModelTest {
    private val context = StudentRequestContext("A", 1, "Client", "2026")
    private val today = LocalDate.now()
    private val currentWeek = plannerWeekStart(today)
    private val flows = mutableMapOf<TimetableKey, MutableStateFlow<AcademicSnapshot<TimetableData>>>()
    private val transition = MutableStateFlow(0L)
    private val coverageRequests = mutableListOf<Collection<LocalDate>>()

    @Test
    fun `distant horizon remains required without opening every week observer`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val viewModel = viewModel()
            try {
                awaitState(viewModel) { it.coreReady && today in it.coveredDates }
                viewModel.setTodayAttendance(TodayAttendance.ATTENDED)
                awaitState(viewModel) { it.todayAttendance == TodayAttendance.ATTENDED }
                val farWeek = plannerWeekStart(today.plusWeeks(40))
                viewModel.previewDate(today.plusWeeks(40))
                runCurrent()

                assertTrue(coverageRequests.any { farWeek in it })
                assertTrue(flows.size <= 8, "Opened ${flows.size} timetable observers")
            } finally {
                viewModel.viewModelScope.cancel()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `cache transition rejects a suspended projection completion`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val viewModel = viewModel()
            try {
                awaitState(viewModel) { it.coreReady && today in it.coveredDates }
                viewModel.setTodayAttendance(TodayAttendance.ATTENDED)
                awaitState(viewModel) { it.todayAttendance == TodayAttendance.ATTENDED }
                viewModel.clearDates()
                val calculation = StandardTestDispatcher(TestCoroutineScheduler())
                viewModel.calculationDispatcher = calculation

                viewModel.previewDate(today)
                runCurrent()
                transition.value++
                calculation.scheduler.runCurrent()
                runCurrent()

                assertTrue(viewModel.state.value.projected.isEmpty())
                assertEquals(null, viewModel.state.value.anchorDate)
            } finally {
                viewModel.viewModelScope.cancel()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `used week observes refreshed dates reset and recovery`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val viewModel = viewModel()
            try {
                awaitState(viewModel) { it.coreReady }
                viewModel.shiftSimulatorMonth(1)
                val target = today.plusMonths(1).withDayOfMonth(15)
                val monday = plannerWeekStart(target)
                awaitFlow(monday)

                flow(monday).value = AcademicSnapshot(data = TimetableData(emptyMap(), mapOf(target.toString() to listOf(slot("A")))))
                awaitState(viewModel) { it.dateTimetable[target]?.firstOrNull()?.subCode == "A" }

                flow(monday).value = AcademicSnapshot(data = TimetableData(emptyMap(), mapOf(target.toString() to listOf(slot("B")))))
                awaitState(viewModel) { it.dateTimetable[target]?.firstOrNull()?.subCode == "B" }

                flow(monday).value = AcademicSnapshot()
                awaitState(viewModel) { target !in it.coveredDates }
                assertFalse(target in viewModel.state.value.dateTimetable)

                flow(monday).value = AcademicSnapshot(error = IllegalStateException("range offline"))
                awaitState(viewModel) { monday in it.failedWeeks }
                flow(monday).value = AcademicSnapshot(data = TimetableData(emptyMap(), mapOf(target.toString() to listOf(slot("C")))))
                awaitState(viewModel) { it.dateTimetable[target]?.firstOrNull()?.subCode == "C" }
                assertFalse(monday in viewModel.state.value.failedWeeks)

                flow(currentWeek).value = AcademicSnapshot()
                awaitState(viewModel) { !it.coreReady && it.dateTimetable.isEmpty() }
                flow(currentWeek).value = AcademicSnapshot(data = actualWeek(currentWeek))
                awaitState(viewModel) { it.coreReady && it.dateTimetable[target]?.firstOrNull()?.subCode == "C" }
            } finally {
                viewModel.viewModelScope.cancel()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `missing failed and legacy weeks never appear complete`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val viewModel = viewModel()
            try {
                awaitState(viewModel) { it.coreReady && today in it.coveredDates }
                viewModel.setTodayAttendance(TodayAttendance.ATTENDED)
                awaitState(viewModel) { it.todayAttendance == TodayAttendance.ATTENDED }
                val horizon = today.plusWeeks(2)
                viewModel.previewDate(horizon)
                val middle = currentWeek.plusWeeks(1)
                val last = plannerWeekStart(horizon)
                awaitFlow(middle)
                awaitFlow(last)
                assertEquals(ProjectionCoverage.LOADING, viewModel.state.value.projectionCoverage)
                assertTrue(viewModel.state.value.projected.isEmpty())

                flow(last).value = AcademicSnapshot(data = actualWeek(last))
                awaitState(viewModel) { last.plusDays(1) in it.coveredDates }
                assertEquals(ProjectionCoverage.LOADING, viewModel.state.value.projectionCoverage)

                flow(middle).value = AcademicSnapshot(error = IllegalStateException("middle offline"))
                awaitState(viewModel) { it.projectionCoverage == ProjectionCoverage.FAILED }
                assertTrue(viewModel.state.value.projected.isEmpty())

                flow(middle).value = AcademicSnapshot(data = TimetableData(mapOf("Mon" to listOf(slot("WEEKLY"))), null))
                awaitState(viewModel) { it.projectionCoverage == ProjectionCoverage.ESTIMATE }
                assertTrue(middle.plusDays(1) in viewModel.state.value.estimatedDates)
            } finally {
                viewModel.viewModelScope.cancel()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    private fun viewModel(): PlannerViewModel {
        val coordinator = mockk<AcademicDataCoordinator>()
        val attendance = mockk<AttendanceRepository>()
        val timetable = mockk<TimetableRepository>()
        val auth = mockk<AuthRepository>()
        val calendar = mockk<CalendarRepository>()
        val markers = mockk<StudentMarkerRepository>()
        val preferences = mockk<PreferencesStore>(relaxed = true)
        val network = mockk<NetworkMonitor>()
        every { coordinator.activeContext } returns MutableStateFlow(context)
        every { coordinator.selectedSemester } returns MutableStateFlow(AcademicSemesterSelection(context, SemesterOption("Y1", "C1", "")))
        every { coordinator.transition } returns transition
        every { coordinator.attendanceDemandError } returns MutableStateFlow(null)
        every { coordinator.timetableDemandError } returns MutableStateFlow(null)
        coEvery { coordinator.plannerVisible(any()) } returns Unit
        coEvery { coordinator.plannerCoverage(any()) } coAnswers { coverageRequests += firstArg<Collection<LocalDate>>() }
        every { auth.getUserInfo() } returns UserInfo("A", 1, "Alex", "", "", "Client")
        coEvery { calendar.getCalendar(any(), any(), any(), any()) } throws IllegalStateException("calendar offline")
        coEvery { markers.observe(any()) } returns flowOf(emptyList())
        every { preferences.getUserString(any(), any()) } answers {
            flowOf(
                when (firstArg<String>()) {
                    "selected_semester_year_id" -> "Y1"
                    "selected_semester_class_id" -> "C1"
                    else -> ""
                },
            )
        }
        every { preferences.getUserBoolean(any(), any()) } returns flowOf(false)
        every { preferences.getUserInt(any(), any()) } returns flowOf(75)
        every { network.isOnline } returns flowOf(true)
        coEvery { attendance.observeSummary(any()) } returns MutableStateFlow(AcademicSnapshot(data = AttendanceResponse()))
        coEvery { timetable.observeWeek(any()) } answers { flow(LocalDate.parse(firstArg<TimetableKey>().startDate)) }
        coEvery { timetable.requestWeek(any(), any()) } answers { flow(LocalDate.parse(firstArg<TimetableKey>().startDate)) }
        val useCase = AttendanceUseCase()
        return PlannerViewModel(
            attendance, timetable, coordinator, auth, markers, calendar,
            useCase, PlannerUseCase(useCase), preferences, network,
        ).also { it.calculationDispatcher = Dispatchers.Main }
    }

    private fun flow(monday: LocalDate): MutableStateFlow<AcademicSnapshot<TimetableData>> =
        flows.getOrPut(TimetableKey(context, monday.toString(), monday.plusDays(6).toString())) {
            MutableStateFlow(
                if (monday == currentWeek) AcademicSnapshot(data = actualWeek(monday)) else AcademicSnapshot(),
            )
        }

    private fun actualWeek(monday: LocalDate) =
        TimetableData(
            emptyMap(),
            (0L..6L).associate { offset ->
                val date = monday.plusDays(offset)
                date.toString() to if (date == today) listOf(slot("TODAY")) else emptyList()
            },
        )

    private fun slot(code: String) = TimetableSlot(subCode = code)

    private suspend fun kotlinx.coroutines.test.TestScope.awaitFlow(monday: LocalDate) {
        runCurrent()
        assertTrue(flows.keys.any { it.startDate == monday.toString() }, "Week was not observed: $monday")
    }

    private suspend fun kotlinx.coroutines.test.TestScope.awaitState(
        viewModel: PlannerViewModel,
        condition: (PlannerUiState) -> Boolean,
    ) {
        runCurrent()
        assertTrue(condition(viewModel.state.value), "Planner state did not reach the expected condition: ${viewModel.state.value}")
    }
}
