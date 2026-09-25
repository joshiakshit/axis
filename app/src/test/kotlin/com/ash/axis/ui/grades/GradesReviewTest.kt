package com.ash.axis.ui.grades

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.GradesRepository
import com.ash.axis.domain.model.CourseMarks
import com.ash.axis.domain.model.GradesData
import com.ash.axis.domain.model.PerformanceSetup
import com.ash.axis.domain.model.ReportCardEntry
import com.ash.axis.domain.model.UserInfo
import com.ash.axis.domain.usecase.TimetableUseCase
import com.ash.core.storage.PreferencesStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GradesReviewTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<GradesRepository>()
    private val auth = mockk<AuthRepository>()
    private val preferences = mockk<PreferencesStore>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { auth.getUserInfo() } returns UserInfo("student", 11, "Student", "student@example.test", "")
        every { preferences.getUserString(any(), any()) } returns flowOf("")
        coEvery { repository.getPerformanceSetup(any(), any()) } returns PerformanceSetup()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `changing exam chips rejects an older marks response without pressing load again`() =
        runTest(dispatcher) {
            val pending = CompletableDeferred<List<CourseMarks>>()
            coEvery { repository.getPerformanceMarks(any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
                withContext(NonCancellable) { pending.await() }
            }
            val viewModel = viewModel()
            try {
                runCurrent()
                viewModel.togglePerformanceExam("first")
                viewModel.applyPerformanceExams()
                runCurrent()
                assertTrue(viewModel.state.value.performanceLoading)

                viewModel.togglePerformanceExam("first")
                viewModel.togglePerformanceExam("second")
                pending.complete(listOf(CourseMarks(subCode = "OLD")))
                runCurrent()

                assertEquals(listOf("second"), viewModel.state.value.selectedPerformanceExams)
                assertTrue(viewModel.state.value.courses.isEmpty())
                assertFalse(viewModel.state.value.performanceLoading)
            } finally {
                pending.complete(emptyList())
                viewModel.viewModelScope.cancel()
                runCurrent()
            }
        }

    @Test
    fun `changing result while a pdf loads clears obsolete loading state`() =
        runTest(dispatcher) {
            coEvery { repository.getGrades(any(), any(), any(), any(), any()) } returns
                GradesData(
                    reportCards = listOf(ReportCardEntry(id = "card")),
                    marksheetType = "marksheet",
                    subExamTypeAA = "exam",
                )
            val pending = CompletableDeferred<ByteArray>()
            coEvery { repository.getReportCardPdf(any(), any(), any(), any(), any(), any()) } coAnswers { pending.await() }
            val viewModel = viewModel()
            try {
                runCurrent()
                viewModel.selectSession("first")
                runCurrent()
                viewModel.viewReportCard()
                runCurrent()
                assertTrue(viewModel.state.value.isLoadingPdf)

                viewModel.selectSession("second")
                runCurrent()

                assertEquals("second", viewModel.state.value.selectedSessionId)
                assertFalse(viewModel.state.value.isLoadingPdf)
            } finally {
                viewModel.viewModelScope.cancel()
                runCurrent()
            }
        }

    private fun viewModel() =
        GradesViewModel(
            mockk<Application>(),
            repository,
            auth,
            mockk<AttendanceRepository>(),
            preferences,
            mockk<TimetableUseCase>(),
        )
}
