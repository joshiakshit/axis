package com.ash.axis.ui.dashboard

import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.repository.AttendanceKey
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.TimetableData
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceEndRow
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.axis.domain.model.UserInfo
import com.ash.axis.domain.usecase.AttendanceUseCase
import com.ash.axis.domain.usecase.TimetableUseCase
import com.ash.core.network.NetworkMonitor
import com.ash.core.storage.PreferencesStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    @Test
    @Suppress("LongMethod")
    fun `snapshots remain independent and preference changes do not request data`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val account = StudentRequestContext("A", 1, "Client", "2026")
                val context = MutableStateFlow<StudentRequestContext?>(account)
                val summary = MutableStateFlow(AcademicSnapshot<AttendanceResponse>())
                val nextAccountSummary = MutableStateFlow(AcademicSnapshot<AttendanceResponse>())
                val week = MutableStateFlow(AcademicSnapshot<TimetableData>())
                val threshold = MutableStateFlow(75)
                val attendance = mockk<AttendanceRepository>()
                val timetable = mockk<TimetableRepository>()
                val coordinator = mockk<AcademicDataCoordinator>()
                val auth = mockk<AuthRepository>()
                val preferences = mockk<PreferencesStore>()
                val network = mockk<NetworkMonitor>()
                val pending = CompletableDeferred<com.ash.axis.domain.model.SemesterOption>()
                every { coordinator.activeContext } returns context
                every { coordinator.attendanceDemandError } returns MutableStateFlow(null)
                every { coordinator.timetableDemandError } returns MutableStateFlow(null)
                every { auth.getUserInfo() } returns UserInfo("A", 1, "Alex", "", "", "Client")
                every { preferences.getUserString(any(), any()) } answers {
                    when (firstArg<String>()) {
                        "selected_semester_year_id" -> flowOf("Y1")
                        "selected_semester_class_id" -> flowOf("C1")
                        else -> flowOf("")
                    }
                }
                every { preferences.getUserInt("attendance_threshold", 75) } returns threshold
                every { network.isOnline } returns flowOf(true)
                coEvery { attendance.getPreferredSemester(any(), any(), any(), any(), any()) } coAnswers { pending.await() }
                coEvery { attendance.observeSummary(any()) } coAnswers {
                    if (firstArg<AttendanceKey>().context.admno == "A") summary else nextAccountSummary
                }
                coEvery { timetable.observeWeek(any()) } returns week
                coEvery { coordinator.homeVisible() } returns Unit
                coEvery { coordinator.refreshAttendance() } returns Unit
                coEvery { coordinator.refreshTimetable() } returns Unit
                val viewModel =
                    DashboardViewModel(
                        attendance,
                        timetable,
                        coordinator,
                        auth,
                        AttendanceUseCase(),
                        TimetableUseCase(),
                        preferences,
                        network,
                    )
                runCurrent()
                summary.value = AcademicSnapshot(data = AttendanceResponse(endrow = AttendanceEndRow(8, 10, 80.0)))
                week.value = AcademicSnapshot(error = IllegalStateException("Schedule failed"))
                runCurrent()
                assertTrue(viewModel.state.value.hasAttendance)
                assertFalse(viewModel.state.value.hasTimetable)
                assertEquals(80.0, viewModel.state.value.overallPercent)
                assertEquals("Schedule failed", viewModel.state.value.timetableError)

                threshold.value = 85
                runCurrent()
                assertEquals(85, viewModel.state.value.threshold)
                coVerify(exactly = 0) { attendance.requestSummary(any(), any()) }
                coVerify(exactly = 0) { timetable.requestWeek(any(), any()) }
                viewModel.refresh()
                runCurrent()
                coVerify(exactly = 1) { coordinator.refreshAttendance() }
                coVerify(exactly = 1) { coordinator.refreshTimetable() }

                context.value = StudentRequestContext("B", 1, "Client", "2026")
                runCurrent()
                assertFalse(viewModel.state.value.hasAttendance)
                assertEquals(0.0, viewModel.state.value.overallPercent)
                coVerify { attendance.observeSummary(AttendanceKey(context.value!!, "C1", "Y1")) }
            } finally {
                Dispatchers.resetMain()
            }
        }
}
