package com.ash.axis.ui.planner

import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.repository.AttendanceKey
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.CalendarRepository
import com.ash.axis.data.repository.StudentMarkerRepository
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.axis.domain.model.UserInfo
import com.ash.axis.domain.usecase.AttendanceUseCase
import com.ash.axis.domain.usecase.PlannerUseCase
import com.ash.core.network.NetworkMonitor
import com.ash.core.storage.PreferencesStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerSelectionReviewTest {
    @Test
    fun `saved semester binds shared attendance before option discovery completes`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val context = StudentRequestContext("A", 1, "Client", "2026")
            val coordinator = mockk<AcademicDataCoordinator>()
            val attendance = mockk<AttendanceRepository>()
            val timetable = mockk<TimetableRepository>(relaxed = true)
            val auth = mockk<AuthRepository>()
            val calendar = mockk<CalendarRepository>()
            val markers = mockk<StudentMarkerRepository>()
            val preferences = mockk<PreferencesStore>()
            val network = mockk<NetworkMonitor>()
            val pending = CompletableDeferred<SemesterOption>()
            every { coordinator.activeContext } returns MutableStateFlow<StudentRequestContext?>(context)
            every { coordinator.attendanceDemandError } returns MutableStateFlow(null)
            every { coordinator.timetableDemandError } returns MutableStateFlow(null)
            coEvery { coordinator.plannerVisible(any()) } returns Unit
            every { auth.getUserInfo() } returns UserInfo("A", 1, "Alex", "", "", "Client")
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
            coEvery { calendar.getCalendar(any(), any(), any(), any()) } throws IllegalStateException("calendar offline")
            coEvery { markers.observe(any()) } returns flowOf(emptyList())
            coEvery { attendance.getPreferredSemester(any(), any(), any(), any(), any()) } coAnswers { pending.await() }
            coEvery { attendance.observeSummary(any()) } returns
                MutableStateFlow(AcademicSnapshot(data = AttendanceResponse()))
            coEvery { timetable.observeWeek(any()) } returns MutableStateFlow(AcademicSnapshot())
            val useCase = AttendanceUseCase()
            val viewModel =
                PlannerViewModel(
                    attendance, timetable, coordinator, auth, markers,
                    calendar, useCase, PlannerUseCase(useCase), preferences, network,
                )
            try {
                runCurrent()
                coVerify(atLeast = 1) { attendance.observeSummary(AttendanceKey(context, "C1", "Y1")) }
            } finally {
                viewModel.viewModelScope.cancel()
                runCurrent()
                Dispatchers.resetMain()
            }
        }
}
