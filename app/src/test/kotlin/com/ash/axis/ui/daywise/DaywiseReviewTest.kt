package com.ash.axis.ui.daywise

import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicSemesterSelection
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.DaywiseKey
import com.ash.axis.domain.model.DaywiseResponse
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.core.network.NetworkMonitor
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DaywiseReviewTest {
    @Test
    @Suppress("LongMethod")
    fun `metadata retry and changed selection keep only current range and error`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val coordinator = mockk<AcademicDataCoordinator>()
            val attendance = mockk<AttendanceRepository>()
            val network = mockk<NetworkMonitor>()
            val demandError = MutableStateFlow<Throwable?>(null)
            val start = LocalDate.now().withDayOfMonth(1)
            val daily =
                MutableStateFlow(
                    AcademicSnapshot(data = DaywiseResponse(dateArray = mapOf("1" to start.toString())), updatedAtMillis = 123L),
                )
            val nextRange = MutableStateFlow(AcademicSnapshot<DaywiseResponse>())
            var metadataAvailable = false
            val context = StudentRequestContext("A", 1, "Client", "2026")
            val selection = MutableStateFlow(AcademicSemesterSelection(context, error = IllegalStateException("semester offline")))
            every { coordinator.activeContext } returns MutableStateFlow<StudentRequestContext?>(context)
            every { coordinator.selectedSemester } returns selection
            every { coordinator.daywiseDemandError } returns demandError
            every { network.isOnline } returns flowOf(true)
            coEvery { attendance.getLatestSemester(any(), any(), any()) } coAnswers {
                if (!metadataAvailable) error("semester offline")
                SemesterOption("Y1", "C1", "Semester 1")
            }
            coEvery { attendance.observeDaywise(any()) } coAnswers {
                if (firstArg<DaywiseKey>().year == "Y1") daily else nextRange
            }
            coEvery { coordinator.daywiseVisible(any(), any(), any()) } returns Unit
            coEvery { coordinator.discoverSemester() } coAnswers {
                if (metadataAvailable) selection.value = AcademicSemesterSelection(context, SemesterOption("Y1", "C1", "Semester 1"))
            }
            val viewModel = DaywiseViewModel(attendance, coordinator, network)
            try {
                runCurrent()
                assertEquals("semester offline", viewModel.state.value.error)

                metadataAvailable = true
                viewModel.onPageVisibilityChanged(true)
                runCurrent()
                assertTrue(viewModel.state.value.hasData)
                assertEquals(1, viewModel.state.value.days.size)
                assertNull(viewModel.state.value.error)

                demandError.value = IllegalStateException("range failed")
                runCurrent()
                selection.value = AcademicSemesterSelection(context, SemesterOption("Y2", "C2", "Semester 2"))
                runCurrent()
                assertFalse(viewModel.state.value.hasData)
                assertEquals("range failed", viewModel.state.value.error)
                demandError.value = null
                runCurrent()
                assertNull(viewModel.state.value.error)
            } finally {
                viewModel.viewModelScope.cancel()
                runCurrent()
                Dispatchers.resetMain()
            }
        }
}
