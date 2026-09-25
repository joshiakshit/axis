package com.ash.axis.ui.dashboard

import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.axis.domain.model.UserInfo
import com.ash.axis.domain.usecase.AttendanceUseCase
import com.ash.axis.domain.usecase.TimetableUseCase
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardReviewTest {
    @Test
    fun `cleared demand error disappears without another snapshot emission`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val errors = MutableStateFlow<Throwable?>(null)
            val viewModel = viewModel(errors, savedSelection = true)
            try {
                runCurrent()
                errors.value = IllegalStateException("demand failed")
                runCurrent()
                assertEquals("demand failed", viewModel.state.value.attendanceError)

                errors.value = null
                runCurrent()

                assertNull(viewModel.state.value.attendanceError)
            } finally {
                viewModel.viewModelScope.cancel()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `missing semester metadata failure is presented instead of escaping the collector`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val viewModel = viewModel(MutableStateFlow(null), savedSelection = false)
            try {
                runCurrent()
                assertEquals("semester offline", viewModel.state.value.attendanceError)
            } finally {
                viewModel.viewModelScope.cancel()
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    private fun viewModel(
        errors: MutableStateFlow<Throwable?>,
        savedSelection: Boolean,
    ): DashboardViewModel {
        val coordinator = mockk<AcademicDataCoordinator>()
        val attendance = mockk<AttendanceRepository>()
        val preferences = mockk<PreferencesStore>()
        val auth = mockk<AuthRepository>()
        val network = mockk<NetworkMonitor>()
        every { coordinator.activeContext } returns
            MutableStateFlow<StudentRequestContext?>(StudentRequestContext("A", 1, "Client", ""))
        every { coordinator.attendanceDemandError } returns errors
        every { coordinator.timetableDemandError } returns MutableStateFlow(null)
        every { preferences.getUserString(any(), any()) } answers {
            flowOf(if (savedSelection) "saved" else "")
        }
        every { preferences.getUserInt(any(), any()) } returns flowOf(75)
        every { auth.getUserInfo() } returns UserInfo("A", 1, "Alex", "", "", "Client")
        every { network.isOnline } returns flowOf(true)
        coEvery { attendance.observeSummary(any()) } returns MutableStateFlow(AcademicSnapshot<AttendanceResponse>())
        coEvery { attendance.getPreferredSemester(any(), any(), any(), any(), any()) } throws
            IllegalStateException("semester offline")
        return DashboardViewModel(
            attendance,
            mockk<TimetableRepository>(),
            coordinator,
            auth,
            AttendanceUseCase(),
            TimetableUseCase(),
            preferences,
            network,
        )
    }
}
