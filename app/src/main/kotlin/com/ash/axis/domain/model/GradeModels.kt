package com.ash.axis.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GradeSubject(
    val subCode: String = "",
    val subName: String = "",
    val grade: String = "",
    val credits: String = "",
    val gradePoints: String = "",
    val marks: String = "",
    val totalMarks: String = "",
    val internalMarks: String = "",
    val externalMarks: String = "",
)

@Serializable
data class ReportCardEntry(
    val srno: Int = 0,
    val id: String = "",
    val examSession: String = "",
    val acadYear: String = "",
    val classes: String = "",
    val semDisplayName: String = "",
    val semesterNumeric: String = "",
    val year: String = "",
    val subExamName: String = "",
)

@Serializable
data class SemesterGrades(
    val semesterLabel: String = "",
    val subjects: List<GradeSubject> = emptyList(),
    val sgpa: String = "",
    val cgpa: String = "",
    val earnedGradePoints: String = "",
)

@Serializable
data class GradesData(
    val semesters: List<SemesterGrades> = emptyList(),
    val reportCards: List<ReportCardEntry> = emptyList(),
    val isOverallPerformance: Boolean = false,
    val noResultMsg: String = "",
    val isResultPublish: Boolean = true,
    val subExamTypeAA: String = "",
    val acadYear: String = "",
    val classId: String = "",
    val marksheetType: String = "",
)

@Serializable
data class ExamSession(
    val id: String = "",
    val label: String = "",
)

@Serializable
data class PerformanceAcadYear(
    val academicYear: String = "",
    val isCurrent: Boolean = false,
)

@Serializable
data class PerformanceOption(
    val id: String = "",
    val label: String = id,
)

@Serializable
data class PerformanceSetup(
    val academicYears: List<PerformanceAcadYear> = emptyList(),
    val semester: String = "",
)

@Serializable
data class CourseMarks(
    val subCode: String = "",
    val subShort: String = "",
    val subName: String = "",
    val lectures: List<LectureMark> = emptyList(),
)

@Serializable
data class LectureMark(
    val lecType: String = "",
    val examType: String = "",
    val maxMarks: String = "",
    val minMarks: String = "",
    val obtainedMarks: String = "",
)

@Serializable
data class PerformanceData(
    val academicYears: List<PerformanceAcadYear> = emptyList(),
    val courses: List<CourseMarks> = emptyList(),
)
