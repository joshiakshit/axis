package com.ash.axis.ui.grades

import com.ash.axis.domain.model.CourseMarks
import com.ash.axis.domain.model.ExamSession
import com.ash.axis.domain.model.PerformanceAcadYear
import com.ash.axis.domain.model.PerformanceOption
import com.ash.axis.domain.model.ReportCardEntry
import java.io.File

enum class GradeTab { PERFORMANCE, RESULT }

data class GradesUiState(
    val selectedTab: GradeTab = GradeTab.PERFORMANCE,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val semesterNumbers: List<String> = emptyList(),
    val selectedSemesterNum: String? = null,
    val examSessions: List<ExamSession> = emptyList(),
    val selectedSessionId: String? = null,
    val reportCards: List<ReportCardEntry> = emptyList(),
    val isResultPublish: Boolean = true,
    val noResultMsg: String = "",
    val isLoadingPdf: Boolean = false,
    val pdfFile: File? = null,
    val marksheetType: String = "",
    val subExamTypeAA: String = "",
    val classId: String = "",
    val acadYear: String = "",
    val resultLastUpdated: Long? = null,
    val performanceLoading: Boolean = false,
    val performanceError: String? = null,
    val performanceYears: List<PerformanceAcadYear> = emptyList(),
    val performanceSemester: String = "",
    val selectedPerformanceYear: String? = null,
    val performanceSessions: List<PerformanceOption> = emptyList(),
    val selectedPerformanceSession: String? = null,
    val performanceClasses: List<PerformanceOption> = emptyList(),
    val selectedPerformanceClass: String? = null,
    val performanceDivisions: List<PerformanceOption> = emptyList(),
    val selectedPerformanceDivision: String? = null,
    val performanceExams: List<PerformanceOption> = emptyList(),
    val selectedPerformanceExams: List<String> = emptyList(),
    val performanceExamSelectorExpanded: Boolean = false,
    val showMarksInsights: Boolean = false,
    val courses: List<CourseMarks> = emptyList(),
    val performanceLastUpdated: Long? = null,
)
