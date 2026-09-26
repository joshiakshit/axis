package com.ash.axis.ui.daywise

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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DaywiseViewModelTest {
    @Test
    @Suppress("LongMethod")
    fun `settled visibility and month changes demand server ranges while saved days remain usable`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val context = MutableStateFlow<StudentRequestContext?>(StudentRequestContext("A", 1, "Client", "2026"))
                val daily = MutableStateFlow(AcademicSnapshot<DaywiseResponse>())
                val nextMonth = MutableStateFlow(AcademicSnapshot<DaywiseResponse>())
                val coordinator = mockk<AcademicDataCoordinator>()
                val attendance = mockk<AttendanceRepository>()
                val network = mockk<NetworkMonitor>()
                every { coordinator.activeContext } returns context
                every { coordinator.selectedSemester } returns
                    MutableStateFlow(
                        AcademicSemesterSelection(context.value, SemesterOption("Y1", "C1", "")),
                    )
                every { coordinator.daywiseDemandError } returns MutableStateFlow(null)
                every { network.isOnline } returns flowOf(true)
                coEvery { attendance.getLatestSemester(any(), any(), any()) } coAnswers {
                    CompletableDeferred<com.ash.axis.domain.model.SemesterOption>().await()
                }
                coEvery { attendance.observeDaywise(any()) } coAnswers {
                    if (firstArg<DaywiseKey>().fromDate == LocalDate.now().withDayOfMonth(1).toString()) daily else nextMonth
                }
                coEvery { coordinator.daywiseVisible(any(), any(), any()) } returns Unit
                coEvery { coordinator.refreshDaywise(any(), any(), any()) } returns Unit
                val viewModel = DaywiseViewModel(attendance, coordinator, network)
                runCurrent()
                coVerify(exactly = 0) { coordinator.daywiseVisible(any(), any(), any()) }

                viewModel.onPageVisibilityChanged(true)
                runCurrent()
                val start = LocalDate.now().withDayOfMonth(1)
                val end = start.withDayOfMonth(start.lengthOfMonth())
                coVerify(exactly = 1) { coordinator.daywiseVisible("Y1", start.toString(), end.toString()) }

                daily.value = AcademicSnapshot(data = DaywiseResponse(dateArray = mapOf("1" to start.toString())), updatedAtMillis = 123L)
                runCurrent()
                assertTrue(viewModel.state.value.hasData)
                assertEquals(1, viewModel.state.value.days.size)
                daily.value = daily.value.copy(error = IllegalStateException("Offline"))
                runCurrent()
                assertEquals(1, viewModel.state.value.days.size)
                assertEquals(123L, viewModel.state.value.lastUpdated)

                viewModel.refresh()
                runCurrent()
                coVerify(exactly = 1) { coordinator.refreshDaywise("Y1", start.toString(), end.toString()) }
                viewModel.onPageVisibilityChanged(false)
                viewModel.shiftMonth(1)
                runCurrent()
                assertFalse(viewModel.state.value.hasData)
                coVerify(exactly = 1) { coordinator.daywiseVisible(any(), any(), any()) }
            } finally {
                Dispatchers.resetMain()
            }
        }
}
