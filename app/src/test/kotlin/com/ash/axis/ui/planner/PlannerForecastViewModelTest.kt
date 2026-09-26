package com.ash.axis.ui.planner

import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicSemesterSelection
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.StudentMarkerRepository
import com.ash.axis.data.repository.TimetableData
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceEntry
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.StudentMarker
import com.ash.axis.domain.model.StudentMarkerType
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.axis.domain.model.TimetableSlot
import com.ash.axis.domain.usecase.AttendanceUseCase
import com.ash.axis.domain.usecase.PlannerUseCase
import com.ash.axis.domain.usecase.TodayAttendance
import com.ash.core.network.NetworkMonitor
import com.ash.core.storage.PreferencesStore
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerForecastViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.now()
    private val context = MutableStateFlow<StudentRequestContext?>(StudentRequestContext("A", 1, "Client", "2026"))
    private val selection = MutableStateFlow(AcademicSemesterSelection(context.value, SemesterOption("Y", "C", "")))
    private val transition = MutableStateFlow(0L)
    private val attendance = mockk<AttendanceRepository>()
    private val timetable = mockk<TimetableRepository>()
    private val markers = MutableStateFlow<List<StudentMarker>>(emptyList())
    private val summary =
        MutableStateFlow(
            AcademicSnapshot(
                data =
                    AttendanceResponse(
                        table =
                            mapOf(
                                "a" to
                                    AttendanceEntry(
                                        subCode = "CS", subname = "Math", lecType = "PP",
                                        present = 80, total = 100, percent = 80.0,
                                    ),
                            ),
                    ),
                updatedAtMillis = 1L,
            ),
        )
    private val week =
        MutableStateFlow(
            AcademicSnapshot(
                data =
                    TimetableData(
                        listOf(
                            "Mon",
                            "Tue",
                            "Wed",
                            "Thu",
                            "Fri",
                            "Sat",
                            "Sun",
                        ).associateWith { listOf(TimetableSlot(subCode = "CS", lectType = "PP")) },
                        null,
                    ),
                updatedAtMillis = 2L,
            ),
        )
    private lateinit var viewModel: PlannerViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val coordinator = mockk<AcademicDataCoordinator>(relaxed = true)
        every { coordinator.activeContext } returns context
        every { coordinator.selectedSemester } returns selection
        every { coordinator.transition } returns transition
        every { coordinator.attendanceDemandError } returns MutableStateFlow(null)
        every { coordinator.timetableDemandError } returns MutableStateFlow(null)
        coEvery { attendance.observeSummary(any()) } returns summary
        coEvery { timetable.observeWeek(any()) } returns week
        val markerRepo = mockk<StudentMarkerRepository>()
        every { markerRepo.observe(any()) } returns markers
        val preferences = mockk<PreferencesStore>()
        every { preferences.getUserInt(any(), any()) } returns flowOf(75)
        every { preferences.getUserBoolean(any(), any()) } returns flowOf(false)
        every { preferences.getUserString("semester_end_date", "") } returns flowOf(today.plusDays(7).toString())
        val network = mockk<NetworkMonitor>()
        every { network.isOnline } returns MutableStateFlow(true)
        viewModel =
            PlannerViewModel(
                attendance, timetable, coordinator, markerRepo,
                AttendanceUseCase(), PlannerUseCase(), preferences, network,
            )
        viewModel.calculationDispatcher = dispatcher
    }

    @AfterEach
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `forecast horizon and absences never request future timetable weeks`() =
        runTest(dispatcher) {
            runCurrent()
            assertTrue(viewModel.state.value.projected.isEmpty())
            viewModel.setTodayAttendance(TodayAttendance.ALREADY_INCLUDED)
            viewModel.setForecastEnd(today.plusWeeks(40))
            viewModel.addAbsence(today.plusDays(1), today.plusDays(2))
            runCurrent()
            assertEquals(2, viewModel.state.value.projected.single().absencesPlanned)
            coVerify(exactly = 1) { timetable.observeWeek(any()) }
            coVerify(exactly = 0) { timetable.requestWeek(any(), any()) }
        }

    @Test
    fun `saved holiday updates forecast and removal restores its classes`() =
        runTest(dispatcher) {
            runCurrent()
            viewModel.setTodayAttendance(TodayAttendance.ALREADY_INCLUDED)
            runCurrent()
            assertEquals(107, viewModel.state.value.projected.single().projectedTotal)
            markers.value = listOf(StudentMarker(1, "Break", StudentMarkerType.HOLIDAY, today.plusDays(1), today.plusDays(3)))
            runCurrent()
            assertEquals(104, viewModel.state.value.projected.single().projectedTotal)
            markers.value = emptyList()
            runCurrent()
            assertEquals(107, viewModel.state.value.projected.single().projectedTotal)
        }

    @Test
    fun `new attendance snapshot requires a fresh answer for today`() =
        runTest(dispatcher) {
            runCurrent()
            viewModel.setTodayAttendance(TodayAttendance.ATTENDED)
            runCurrent()
            assertEquals(108, viewModel.state.value.projected.single().projectedTotal)
            summary.value = summary.value.copy(updatedAtMillis = 3L)
            runCurrent()
            assertNull(viewModel.state.value.todayAttendance)
            assertTrue(viewModel.state.value.projected.isEmpty())
        }

    @Test
    fun `yesterday answer is not reused after midnight`() =
        runTest(dispatcher) {
            runCurrent()
            viewModel.setTodayAttendance(TodayAttendance.ATTENDED)
            runCurrent()
            val zone = ZoneId.systemDefault()
            viewModel.clock = Clock.fixed(today.plusDays(1).atStartOfDay(zone).toInstant(), zone)
            viewModel.setForecastEnd(today.plusDays(8))
            runCurrent()
            assertNull(viewModel.state.value.todayAttendance)
            assertTrue(viewModel.state.value.projected.isEmpty())
        }

    @Test
    fun `missing schedule clears the forecast and recovery uses new data`() =
        runTest(dispatcher) {
            runCurrent()
            viewModel.setTodayAttendance(TodayAttendance.ALREADY_INCLUDED)
            runCurrent()
            val saved = week.value
            week.value = AcademicSnapshot(error = IllegalStateException("offline"))
            runCurrent()
            assertTrue(viewModel.state.value.projected.isEmpty())
            assertTrue(viewModel.state.value.error != null)
            week.value = saved
            runCurrent()
            assertEquals(107, viewModel.state.value.projected.single().projectedTotal)
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `clearing absences wins over a suspended calculation`() =
        runTest(dispatcher) {
            runCurrent()
            viewModel.setTodayAttendance(TodayAttendance.ALREADY_INCLUDED)
            runCurrent()
            val calculation = StandardTestDispatcher(TestCoroutineScheduler())
            viewModel.calculationDispatcher = calculation
            viewModel.addAbsence(today.plusDays(1), today.plusDays(2))
            runCurrent()
            viewModel.clearAbsences()
            runCurrent()
            calculation.scheduler.runCurrent()
            runCurrent()
            calculation.scheduler.runCurrent()
            runCurrent()
            assertTrue(viewModel.state.value.absences.isEmpty())
            assertEquals(0, viewModel.state.value.projected.single().absencesPlanned)
        }

    @Test
    fun `logout rejects a suspended calculation and clears student inputs`() =
        runTest(dispatcher) {
            runCurrent()
            viewModel.setTodayAttendance(TodayAttendance.ALREADY_INCLUDED)
            runCurrent()
            val calculation = StandardTestDispatcher(TestCoroutineScheduler())
            viewModel.calculationDispatcher = calculation
            viewModel.addAbsence(today.plusDays(1), today.plusDays(2))
            runCurrent()
            context.value = null
            runCurrent()
            calculation.scheduler.runCurrent()
            runCurrent()
            assertTrue(viewModel.state.value.projected.isEmpty())
            assertTrue(viewModel.state.value.absences.isEmpty())
            assertTrue(viewModel.state.value.markers.isEmpty())
        }
}
