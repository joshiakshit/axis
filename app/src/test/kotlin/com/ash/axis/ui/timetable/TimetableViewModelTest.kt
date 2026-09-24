package com.ash.axis.ui.timetable

import androidx.lifecycle.viewModelScope
import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.CalendarRepository
import com.ash.axis.data.repository.TimetableData
import com.ash.axis.data.repository.TimetableKey
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.axis.domain.model.TimetableSlot
import com.ash.axis.domain.usecase.TimetableUseCase
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = StudentRequestContext("student", 1, "ExactCase", "2026")
    private val activeContext = MutableStateFlow<StudentRequestContext?>(context)
    private val demandError = MutableStateFlow<Throwable?>(null)
    private val weeks = mutableMapOf<TimetableKey, MutableStateFlow<AcademicSnapshot<TimetableData>>>()
    private val repository = mockk<TimetableRepository>()
    private val coordinator = mockk<AcademicDataCoordinator>()
    private val auth = mockk<AuthRepository>()
    private val preferences = mockk<PreferencesStore>(relaxed = true)
    private val network = mockk<NetworkMonitor>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { coordinator.activeContext } returns activeContext
        every { coordinator.timetableDemandError } returns demandError
        every { network.isOnline } returns MutableStateFlow(true)
        every { auth.getUserInfo() } returns null
        coEvery { coordinator.timetableVisible(any()) } returns Unit
        coEvery { repository.observeWeek(any()) } answers {
            weeks.getOrPut(firstArg()) { MutableStateFlow(AcademicSnapshot()) }
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `failed next week keeps the current week usable`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val current = LocalDate.now().with(DayOfWeek.MONDAY)
            runCurrent()
            weeks.getValue(key(current)).value =
                AcademicSnapshot(
                    data = TimetableData(mapOf("Mon" to listOf(TimetableSlot(subCode = "KNOWN"))), emptyMap()),
                )
            runCurrent()

            val next = current.plusWeeks(1)
            viewModel.onDateShown(next)
            runCurrent()
            weeks.getValue(key(next)).value = AcademicSnapshot(error = IllegalStateException("Offline"))
            runCurrent()

            assertTrue(current in viewModel.state.value.loadedWeeks)
            assertTrue(next in viewModel.state.value.failedWeeks)
            assertTrue(current in viewModel.state.value.dayCache)
            coVerify { coordinator.timetableVisible(next) }
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `old account completion cannot restore its week`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val current = LocalDate.now().with(DayOfWeek.MONDAY)
            runCurrent()
            val oldFlow = weeks.getValue(key(current))

            activeContext.value = context.copy(admno = "other")
            runCurrent()
            oldFlow.value = AcademicSnapshot(data = TimetableData(mapOf("Mon" to listOf(TimetableSlot())), emptyMap()))
            runCurrent()

            assertFalse(current in viewModel.state.value.loadedWeeks)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `rapid date changes keep the last visible week`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val current = LocalDate.now().with(DayOfWeek.MONDAY)
            runCurrent()
            val first = current.plusWeeks(1)
            val last = current.plusWeeks(2)

            viewModel.onDateShown(first)
            runCurrent()
            viewModel.onDateShown(last)
            runCurrent()
            weeks.getValue(key(first)).value = AcademicSnapshot(data = TimetableData(emptyMap(), emptyMap()))
            weeks.getValue(key(last)).value = AcademicSnapshot(data = TimetableData(emptyMap(), emptyMap()))
            runCurrent()

            assertTrue(last in viewModel.state.value.loadedWeeks)
            assertFalse(first in viewModel.state.value.loadedWeeks)
            coVerify { coordinator.timetableVisible(last) }
            viewModel.viewModelScope.cancel()
        }

    private fun key(monday: LocalDate) = TimetableKey(context, monday.toString(), monday.plusDays(6).toString())

    @Test
    fun `clearing a shared snapshot removes the derived week`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            try {
                val current = LocalDate.now().with(DayOfWeek.MONDAY)
                runCurrent()
                weeks.getValue(key(current)).value = AcademicSnapshot(data = TimetableData(emptyMap(), emptyMap()))
                runCurrent()
                assertTrue(current in viewModel.state.value.loadedWeeks)

                weeks.getValue(key(current)).value = AcademicSnapshot()
                runCurrent()

                assertFalse(current in viewModel.state.value.loadedWeeks)
                assertFalse(current in viewModel.state.value.dayCache)
            } finally {
                viewModel.viewModelScope.cancel()
            }
        }

    private fun viewModel() =
        TimetableViewModel(
            repository,
            coordinator,
            auth,
            mockk<CalendarRepository>(),
            mockk<TimetableUseCase>(relaxed = true),
            preferences,
            network,
        )
}
