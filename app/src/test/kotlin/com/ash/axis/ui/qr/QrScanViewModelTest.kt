package com.ash.axis.ui.qr

import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.session.UsageReporter
import com.ash.axis.domain.model.QrScanResult
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.axis.domain.model.UserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QrScanViewModelTest {
    private val user = UserInfo("A", 1, "Student", "student@example.com", "", clientId = "Client")
    private val origin = StudentRequestContext("A", 1, "Client", "")

    @Test
    fun `successful scan confirms marked attendance without backend text`() {
        runTest {
            Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
            val attendance = mockk<AttendanceRepository>()
            val auth = mockk<AuthRepository>()
            val usage = mockk<UsageReporter>(relaxed = true)
            val academic = mockk<AcademicDataCoordinator>()
            every { auth.getUserInfo() } returns user
            every { academic.activeContext } returns MutableStateFlow(origin)
            coEvery { attendance.sendScanQR(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                QrScanResult(true, "raw server status: 1")
            coEvery { academic.qrSucceeded(origin) } returns Unit
            val viewModel = QrScanViewModel(attendance, auth, usage, academic)
            try {
                viewModel.submitQrScan("qr", "selfie")
                runCurrent()
                assertEquals(true, viewModel.state.value.success)
                assertEquals("Your attendance was marked successfully.", viewModel.state.value.message)
                coVerify(exactly = 1) { academic.qrSucceeded(origin) }
            } finally {
                viewModel.viewModelScope.cancel()
                Dispatchers.resetMain()
            }
        }
    }

    @Test
    fun `refresh failure still confirms marked attendance`() {
        runTest {
            Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
            val attendance = mockk<AttendanceRepository>()
            val auth = mockk<AuthRepository>()
            val usage = mockk<UsageReporter>(relaxed = true)
            val academic = mockk<AcademicDataCoordinator>()
            every { auth.getUserInfo() } returns user
            every { academic.activeContext } returns MutableStateFlow(origin)
            coEvery { attendance.sendScanQR(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                QrScanResult(true, "raw server status: 1")
            coEvery { academic.qrSucceeded(origin) } throws IllegalStateException("offline")
            val viewModel = QrScanViewModel(attendance, auth, usage, academic)
            try {
                viewModel.submitQrScan("qr", "selfie")
                runCurrent()
                assertEquals(true, viewModel.state.value.success)
                assertEquals(
                    "Attendance was marked, but the latest attendance could not refresh. Pull down to retry.",
                    viewModel.state.value.message,
                )
            } finally {
                viewModel.viewModelScope.cancel()
                Dispatchers.resetMain()
            }
        }
    }
}
