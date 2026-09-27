package com.ash.axis.ui.attendance

import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicSemesterSelection
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.TimetableData
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.axis.domain.usecase.AttendanceUseCase
import com.ash.axis.domain.usecase.ForecastUseCase
import com.ash.axis.domain.usecase.TimetableUseCase
import com.ash.core.network.NetworkMonitor
import com.ash.core.storage.PreferencesStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceReviewTest {
    @Test
    @Suppress("LongMethod")
    fun `metadata retry and demand recovery retain current presentation`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val coordinator = mockk<AcademicDataCoordinator>()
            val attendance = mockk<AttendanceRepository>()
            val timetable = mockk<TimetableRepository>()
            val preferences = mockk<PreferencesStore>()
            val network = mockk<NetworkMonitor>()
            val demandError = MutableStateFlow<Throwable?>(null)
            val threshold = MutableStateFlow(75)
            val combined = MutableStateFlow(false)
            val savedEnd = MutableStateFlow("")
            val summary = MutableStateFlow(AcademicSnapshot(data = AttendanceResponse()))
            var metadataAvailable = false
            val context = StudentRequestContext("A", 1, "Client", "2026")
            val activeContext = MutableStateFlow<StudentRequestContext?>(context)
            val selection = MutableStateFlow(AcademicSemesterSelection(context, error = IllegalStateException("semester offline")))
            every { coordinator.activeContext } returns activeContext
            every { coordinator.selectedSemester } returns selection
            every { coordinator.attendanceDemandError } returns demandError
            every { coordinator.timetableDemandError } returns MutableStateFlow(null)
            every { preferences.getUserString("semester_end_date", "") } returns savedEnd
            every { preferences.getUserInt("attendance_threshold", 75) } answers {
                if (activeContext.value?.admno == "B") flowOf(60) else threshold
            }
            every { preferences.getUserBoolean("combined_attendance", false) } answers {
                if (activeContext.value?.admno == "B") flowOf(false) else combined
            }
            every { preferences.userScoped("attendance_threshold") } returns "A_attendance_threshold"
            every { preferences.userScoped("combined_attendance") } returns "A_combined_attendance"
            coEvery { preferences.putInt("A_attendance_threshold", any()) } coAnswers { threshold.value = secondArg() }
            coEvery { preferences.putBoolean("A_combined_attendance", any()) } coAnswers { combined.value = secondArg() }
            every { network.isOnline } returns flowOf(true)
            coEvery { attendance.getLatestSemester(any(), any(), any()) } coAnswers {
                if (!metadataAvailable) error("semester offline")
                SemesterOption("Y1", "C1", "Semester 1")
            }
            coEvery { attendance.observeSummary(any()) } returns summary
            coEvery { timetable.observeWeek(any()) } returns MutableStateFlow(AcademicSnapshot<TimetableData>())
            coEvery { coordinator.refreshAttendance() } returns Unit
            coEvery { coordinator.refreshTimetable() } returns Unit
            coEvery { coordinator.discoverSemester() } coAnswers {
                if (metadataAvailable) selection.value = AcademicSemesterSelection(context, SemesterOption("Y1", "C1", "Semester 1"))
            }
            val useCase = AttendanceUseCase()
            val forecast = spyk(ForecastUseCase(useCase))
            val viewModel =
                AttendanceViewModel(
                    attendance,
                    timetable,
                    coordinator,
                    useCase,
                    forecast,
                    TimetableUseCase(),
                    preferences,
                    network,
                )
            try {
                runCurrent()
                assertEquals("semester offline", viewModel.state.value.error)

                metadataAvailable = true
                viewModel.refresh()
                runCurrent()
                assertTrue(viewModel.state.value.hasData)
                assertNull(viewModel.state.value.error)

                viewModel.setThreshold(95)
                viewModel.setCombinedAttendance(true)
                runCurrent()
                assertEquals(95, viewModel.state.value.threshold)
                assertTrue(viewModel.state.value.combinedAttendance)
                coVerify(exactly = 1) { preferences.putInt("A_attendance_threshold", 95) }
                coVerify(exactly = 1) { preferences.putBoolean("A_combined_attendance", true) }
                coVerify(exactly = 1) { coordinator.refreshAttendance() }
                coVerify(exactly = 1) { coordinator.refreshTimetable() }

                val end = LocalDate.now().plusDays(20)
                savedEnd.value = end.toString()
                runCurrent()
                verify { forecast.buildForecast(any(), any(), 95, end) }

                demandError.value = IllegalStateException("demand failed")
                runCurrent()
                threshold.value = 80
                runCurrent()
                assertEquals(80, viewModel.state.value.threshold)
                assertEquals("demand failed", viewModel.state.value.error)

                demandError.value = null
                runCurrent()
                assertNull(viewModel.state.value.error)
                summary.value = summary.value.copy(error = IllegalStateException("snapshot failed"))
                runCurrent()
                assertEquals("snapshot failed", viewModel.state.value.error)

                val nextContext = StudentRequestContext("B", 2, "Other", "2026")
                activeContext.value = nextContext
                selection.value = AcademicSemesterSelection(nextContext, SemesterOption("Y1", "C1", "Semester 1"))
                runCurrent()
                assertEquals(60, viewModel.state.value.threshold)
                assertEquals(false, viewModel.state.value.combinedAttendance)
            } finally {
                viewModel.viewModelScope.cancel()
                runCurrent()
                Dispatchers.resetMain()
            }
        }
}
