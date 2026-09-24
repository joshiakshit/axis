package com.ash.axis.ui.grades

import com.ash.axis.domain.model.ExamSession
import com.ash.axis.domain.model.PerformanceOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GradesUiStateTest {
    @Test
    fun `changing performance year clears dependent selections and results`() {
        val state =
            GradesUiState(
                selectedPerformanceYear = "2025",
                selectedPerformanceSession = "old",
                performanceSessions = listOf(PerformanceOption("old", "Old")),
                selectedPerformanceClass = "class",
                selectedPerformanceDivision = "division",
                selectedPerformanceExams = listOf("exam"),
                performanceError = "old error",
            )

        val changed = state.withPerformanceYear("2026")

        assertEquals("2026", changed.selectedPerformanceYear)
        assertEquals(emptyList<PerformanceOption>(), changed.performanceSessions)
        assertNull(changed.selectedPerformanceSession)
        assertNull(changed.selectedPerformanceClass)
        assertNull(changed.selectedPerformanceDivision)
        assertEquals(emptyList<String>(), changed.selectedPerformanceExams)
        assertNull(changed.performanceError)
    }

    @Test
    fun `changing result semester clears session and pdf parameters`() {
        val state =
            GradesUiState(
                selectedSemesterNum = "2",
                examSessions = listOf(ExamSession("old", "Old")),
                selectedSessionId = "old",
                marksheetType = "old",
                subExamTypeAA = "old",
                classId = "old",
                acadYear = "old",
            )

        val changed = state.withSemester("3")

        assertEquals("3", changed.selectedSemesterNum)
        assertEquals(emptyList<ExamSession>(), changed.examSessions)
        assertNull(changed.selectedSessionId)
        assertEquals("", changed.marksheetType)
        assertEquals("", changed.subExamTypeAA)
        assertEquals("", changed.classId)
        assertEquals("", changed.acadYear)
    }
}
