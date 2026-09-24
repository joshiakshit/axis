package com.ash.axis.ui.grades

import android.app.Application
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.GradesRepository
import com.ash.axis.domain.model.PerformanceOption
import com.ash.axis.domain.model.PerformanceSetup
import com.ash.axis.domain.model.UserInfo
import com.ash.axis.domain.usecase.TimetableUseCase
import com.ash.core.storage.PreferencesStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GradesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<GradesRepository>()
    private val auth = mockk<AuthRepository>()
    private val preferences = mockk<PreferencesStore>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { auth.getUserInfo() } returns UserInfo("student", 11, "Student", "student@example.test", "")
        every { preferences.getUserString(any(), any()) } returns flowOf("")
        coEvery { preferences.putUserString(any(), any()) } returns Unit
        coEvery { repository.getPerformanceSetup(any(), any()) } returns PerformanceSetup()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `slow old filter response cannot replace newer sessions`() =
        runTest(dispatcher) {
            val old = CompletableDeferred<List<PerformanceOption>>()
            coEvery { repository.getPerformanceSessions(any(), any(), "old") } coAnswers {
                withContext(NonCancellable) { old.await() }
            }
            coEvery { repository.getPerformanceSessions(any(), any(), "new") } returns
                listOf(PerformanceOption("new-session", "New session"))
            val viewModel =
                GradesViewModel(
                    mockk<Application>(),
                    repository,
                    auth,
                    mockk<AttendanceRepository>(),
                    preferences,
                    mockk<TimetableUseCase>(),
                )
            runCurrent()

            viewModel.selectPerformanceYear("old")
            runCurrent()
            viewModel.selectPerformanceYear("new")
            runCurrent()
            old.complete(listOf(PerformanceOption("old-session", "Old session")))
            runCurrent()

            assertEquals("new", viewModel.state.value.selectedPerformanceYear)
            assertEquals(listOf("new-session"), viewModel.state.value.performanceSessions.map { it.id })
        }
}
