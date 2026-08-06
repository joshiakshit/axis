package com.ash.axis.ui.grades

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.GradesRepository
import com.ash.axis.data.repository.SELECTED_SEMESTER_CLASS_KEY
import com.ash.axis.data.repository.SELECTED_SEMESTER_YEAR_KEY
import com.ash.axis.domain.usecase.TimetableUseCase
import com.ash.axis.ui.ErrorText
import com.ash.core.storage.PreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@Suppress("TooManyFunctions", "LargeClass")
@HiltViewModel
class GradesViewModel
    @Inject
    constructor(
        private val app: Application,
        private val gradesRepo: GradesRepository,
        private val authRepository: AuthRepository,
        private val attendanceRepo: AttendanceRepository,
        private val preferencesStore: PreferencesStore,
        private val timetableUseCase: TimetableUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow(GradesUiState())
        val state: StateFlow<GradesUiState> = _state.asStateFlow()

        init {
            loadPerformance(forceRefresh = false)
        }

        fun refresh() {
            when (_state.value.selectedTab) {
                GradeTab.PERFORMANCE -> loadPerformance(forceRefresh = true)
                GradeTab.RESULT -> loadSemesters(forceRefresh = true)
            }
        }

        fun selectTab(tab: GradeTab) {
            _state.update { it.copy(selectedTab = tab, error = null) }
            when (tab) {
                GradeTab.PERFORMANCE -> {
                    if (_state.value.performanceYears.isEmpty()) {
                        loadPerformance(forceRefresh = false)
                    }
                }
                GradeTab.RESULT -> {
                    if (_state.value.semesterNumbers.isEmpty()) {
                        loadSemesters(forceRefresh = false)
                    }
                }
            }
        }

        fun selectPerformanceYear(year: String) {
            _state.update {
                it.copy(
                    selectedPerformanceYear = year,
                    performanceSessions = emptyList(),
                    selectedPerformanceSession = null,
                    performanceClasses = emptyList(),
                    selectedPerformanceClass = null,
                    performanceDivisions = emptyList(),
                    selectedPerformanceDivision = null,
                    performanceExams = emptyList(),
                    selectedPerformanceExams = emptyList(),
                    performanceExamSelectorExpanded = false,
                    courses = emptyList(),
                    performanceError = null,
                )
            }
            viewModelScope.launch { preferencesStore.putUserString(PREF_PERF_YEAR, year) }
            loadPerformanceSessions(year)
        }

        fun selectPerformanceSession(session: String) {
            _state.update {
                it.copy(
                    selectedPerformanceSession = session,
                    performanceClasses = emptyList(),
                    selectedPerformanceClass = null,
                    performanceDivisions = emptyList(),
                    selectedPerformanceDivision = null,
                    performanceExams = emptyList(),
                    selectedPerformanceExams = emptyList(),
                    performanceExamSelectorExpanded = false,
                    courses = emptyList(),
                    performanceError = null,
                )
            }
            viewModelScope.launch { preferencesStore.putUserString(PREF_PERF_SESSION, session) }
            loadPerformanceClasses(session)
        }

        fun selectPerformanceClass(classId: String) {
            _state.update {
                it.copy(
                    selectedPerformanceClass = classId,
                    performanceDivisions = emptyList(),
                    selectedPerformanceDivision = null,
                    performanceExams = emptyList(),
                    selectedPerformanceExams = emptyList(),
                    performanceExamSelectorExpanded = false,
                    courses = emptyList(),
                    performanceError = null,
                )
            }
            viewModelScope.launch { preferencesStore.putUserString(PREF_PERF_CLASS, classId) }
            loadPerformanceDivisions(classId)
        }

        fun selectPerformanceDivision(division: String) {
            _state.update {
                it.copy(
                    selectedPerformanceDivision = division,
                    performanceExams = emptyList(),
                    selectedPerformanceExams = emptyList(),
                    performanceExamSelectorExpanded = false,
                    courses = emptyList(),
                    performanceError = null,
                )
            }
            viewModelScope.launch { preferencesStore.putUserString(PREF_PERF_DIVISION, division) }
            loadPerformanceExams(division)
        }

        fun togglePerformanceExam(exam: String) {
            _state.update {
                val exams =
                    if (exam in it.selectedPerformanceExams) {
                        it.selectedPerformanceExams - exam
                    } else {
                        it.selectedPerformanceExams + exam
                    }
                it.copy(
                    selectedPerformanceExams = exams,
                    courses = emptyList(),
                    performanceError = null,
                )
            }
        }

        fun setPerformanceExamSelectorExpanded(expanded: Boolean) {
            _state.update { it.copy(performanceExamSelectorExpanded = expanded) }
        }

        fun applyPerformanceExams() {
            if (_state.value.selectedPerformanceExams.isNotEmpty()) {
                loadPerformanceMarks()
            }
        }

        fun openMarksInsights() {
            _state.update { it.copy(showMarksInsights = it.courses.isNotEmpty()) }
        }

        fun closeMarksInsights() {
            _state.update { it.copy(showMarksInsights = false) }
        }

        fun selectSemesterNum(num: String) {
            _state.update {
                it.copy(
                    selectedSemesterNum = num,
                    examSessions = emptyList(),
                    selectedSessionId = null,
                    reportCards = emptyList(),
                    pdfFile = null,
                    marksheetType = "",
                    subExamTypeAA = "",
                    classId = "",
                    acadYear = "",
                )
            }
            loadSessions(num, forceRefresh = true)
        }

        fun selectSession(sessionId: String) {
            _state.update {
                it.copy(
                    selectedSessionId = sessionId,
                    reportCards = emptyList(),
                    pdfFile = null,
                    marksheetType = "",
                    subExamTypeAA = "",
                    classId = "",
                    acadYear = "",
                )
            }
            loadGrades(sessionId, forceRefresh = true)
        }

        fun viewReportCard() {
            val s = _state.value
            if (s.marksheetType.isBlank() || s.subExamTypeAA.isBlank()) return
            loadReportCardPdf()
        }

        fun dismissPdf() {
            _state.update { it.copy(pdfFile = null) }
        }

        fun sharePdf() {
            val file = _state.value.pdfFile ?: return
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Report Card")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            app.startActivity(
                Intent.createChooser(intent, "Share Report Card")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        @Suppress("TooGenericExceptionCaught", "LongMethod", "CyclomaticComplexMethod")
        private fun loadSemesters(forceRefresh: Boolean) {
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        isRefreshing = forceRefresh && it.semesterNumbers.isNotEmpty(),
                        isLoading = it.semesterNumbers.isEmpty(),
                    )
                }
                try {
                    val user = authRepository.getUserInfo() ?: error("Not logged in")
                    val yearId =
                        preferencesStore.getUserString(SELECTED_SEMESTER_YEAR_KEY).first()
                    val classId =
                        preferencesStore.getUserString(SELECTED_SEMESTER_CLASS_KEY).first()
                    val semester =
                        attendanceRepo.getPreferredSemester(
                            user.admno,
                            user.brId,
                            yearId,
                            classId,
                            forceRefresh,
                        )
                    val acadYear = semester.yearId.ifBlank { timetableUseCase.getAcadYear() }

                    val semResult =
                        gradesRepo.getSemesters(
                            admno = user.admno,
                            brId = user.brId,
                            classId = semester.classId,
                            academicYear = acadYear,
                            forceRefresh = forceRefresh,
                        )

                    val currentSem =
                        semResult.maxSemFromClasses ?: extractSemesterNumber(semester.label)
                    val maxAllowed = currentSem ?: MAX_REASONABLE_SEMESTER
                    val semNums =
                        semResult.semesters.filter { num ->
                            val n = num.toIntOrNull() ?: return@filter false
                            n in 1..maxAllowed
                        }.sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }

                    _state.update {
                        val selected =
                            it.selectedSemesterNum
                                ?.takeIf { selected -> selected in semNums }
                        it.copy(
                            semesterNumbers = semNums,
                            selectedSemesterNum = selected,
                            examSessions = if (selected == null) emptyList() else it.examSessions,
                            selectedSessionId = if (selected == null) null else it.selectedSessionId,
                            reportCards = if (selected == null) emptyList() else it.reportCards,
                            pdfFile = if (selected == null) null else it.pdfFile,
                            marksheetType = if (selected == null) "" else it.marksheetType,
                            subExamTypeAA = if (selected == null) "" else it.subExamTypeAA,
                            classId = if (selected == null) "" else it.classId,
                            acadYear = if (selected == null) "" else it.acadYear,
                            isLoading = false,
                            isRefreshing = false,
                            resultLastUpdated = System.currentTimeMillis(),
                            error = if (semNums.isEmpty()) "No semester data found" else null,
                        )
                    }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = ErrorText.forData(e),
                        )
                    }
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun loadSessions(
            semesterNumeric: String,
            forceRefresh: Boolean,
        ) {
            viewModelScope.launch {
                try {
                    val user = authRepository.getUserInfo() ?: error("Not logged in")
                    val sessions =
                        gradesRepo.getSessions(
                            admno = user.admno,
                            brId = user.brId,
                            semesterNumeric = semesterNumeric,
                            forceRefresh = forceRefresh,
                        )

                    _state.update {
                        val selected =
                            it.selectedSessionId
                                ?.takeIf { selected -> sessions.any { session -> session.id == selected } }
                        it.copy(
                            examSessions = sessions,
                            selectedSessionId = selected,
                            reportCards = if (selected == null) emptyList() else it.reportCards,
                            pdfFile = if (selected == null) null else it.pdfFile,
                            marksheetType = if (selected == null) "" else it.marksheetType,
                            subExamTypeAA = if (selected == null) "" else it.subExamTypeAA,
                            classId = if (selected == null) "" else it.classId,
                            acadYear = if (selected == null) "" else it.acadYear,
                            isLoading = false,
                            isRefreshing = false,
                            error =
                                if (sessions.isEmpty()) "No exam sessions found" else null,
                        )
                    }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = ErrorText.forData(e),
                        )
                    }
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun loadGrades(
            sessionId: String,
            forceRefresh: Boolean,
        ) {
            viewModelScope.launch {
                try {
                    val user = authRepository.getUserInfo() ?: error("Not logged in")
                    val selectedSems =
                        _state.value.selectedSemesterNum?.let { listOf(it) } ?: emptyList()

                    val data =
                        gradesRepo.getGrades(
                            admno = user.admno,
                            brId = user.brId,
                            sessionId = sessionId,
                            semesters = selectedSems,
                            forceRefresh = forceRefresh,
                        )

                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error =
                                if (!data.isResultPublish) {
                                    data.noResultMsg.ifBlank { "Results not published yet" }
                                } else if (data.reportCards.isEmpty()) {
                                    "No records found"
                                } else {
                                    null
                                },
                            reportCards = data.reportCards,
                            isResultPublish = data.isResultPublish,
                            noResultMsg = data.noResultMsg,
                            marksheetType = data.marksheetType,
                            subExamTypeAA = data.subExamTypeAA,
                            classId = data.classId,
                            acadYear = data.acadYear,
                            resultLastUpdated = System.currentTimeMillis(),
                        )
                    }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = ErrorText.forData(e),
                        )
                    }
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun loadReportCardPdf() {
            viewModelScope.launch {
                _state.update { it.copy(isLoadingPdf = true) }
                try {
                    val user = authRepository.getUserInfo() ?: error("Not logged in")
                    val s = _state.value
                    val pdfBytes =
                        gradesRepo.getReportCardPdf(
                            admno = user.admno,
                            brId = user.brId,
                            marksheetType = s.marksheetType,
                            subExamTypeAA = s.subExamTypeAA,
                            classId = s.classId,
                            acadYear = s.acadYear,
                        )

                    val cacheDir = File(app.cacheDir, "report_cards")
                    cacheDir.mkdirs()
                    val file = File(cacheDir, "ReportCard.pdf")
                    file.writeBytes(pdfBytes)

                    _state.update { it.copy(isLoadingPdf = false, pdfFile = file) }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            isLoadingPdf = false,
                            error = ErrorText.forData(e),
                        )
                    }
                }
            }
        }

        @Suppress("TooGenericExceptionCaught", "LongMethod")
        private fun loadPerformance(forceRefresh: Boolean) {
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        performanceLoading = true,
                        performanceError = null,
                        isLoading = it.performanceYears.isEmpty(),
                        isRefreshing = forceRefresh && it.performanceYears.isNotEmpty(),
                    )
                }
                try {
                    val user = authRepository.getUserInfo() ?: error("Not logged in")
                    val setup = gradesRepo.getPerformanceSetup(admno = user.admno, brId = user.brId)
                    val savedYear = preferencesStore.getUserString(PREF_PERF_YEAR).first()
                    val savedSession = preferencesStore.getUserString(PREF_PERF_SESSION).first()
                    val savedClass = preferencesStore.getUserString(PREF_PERF_CLASS).first()
                    val savedDivision = preferencesStore.getUserString(PREF_PERF_DIVISION).first()
                    val selectedYear =
                        savedYear
                            .takeIf { year -> setup.academicYears.any { it.academicYear == year } }
                            ?: setup.academicYears.firstOrNull { it.isCurrent }?.academicYear
                    val sessions =
                        selectedYear?.let {
                            gradesRepo.getPerformanceSessions(
                                admno = user.admno,
                                brId = user.brId,
                                academicYear = it,
                            )
                        }.orEmpty()
                    val selectedSession = savedSession.takeIf { sessions.any { it.id == savedSession } }
                    val classes =
                        selectedSession?.let {
                            gradesRepo.getPerformanceClasses(
                                admno = user.admno,
                                brId = user.brId,
                                academicYear = selectedYear.orEmpty(),
                                examSession = it,
                            )
                        }.orEmpty()
                    val selectedClass = savedClass.takeIf { classes.any { it.id == savedClass } }
                    val divisions =
                        selectedClass?.let {
                            gradesRepo.getPerformanceDivisions(
                                admno = user.admno,
                                brId = user.brId,
                                classId = it,
                            )
                        }.orEmpty()
                    val selectedDivision = savedDivision.takeIf { divisions.any { it.id == savedDivision } }
                    val exams =
                        selectedDivision?.let {
                            gradesRepo.getPerformanceExams(
                                admno = user.admno,
                                brId = user.brId,
                                academicYear = selectedYear.orEmpty(),
                                classId = selectedClass.orEmpty(),
                                division = it,
                            )
                        }.orEmpty()

                    _state.update {
                        it.copy(
                            performanceLoading = false,
                            isLoading = false,
                            isRefreshing = false,
                            performanceYears = setup.academicYears,
                            performanceSemester = setup.semester,
                            selectedPerformanceYear = selectedYear,
                            performanceSessions = sessions,
                            selectedPerformanceSession = selectedSession,
                            performanceClasses = classes,
                            selectedPerformanceClass = selectedClass,
                            performanceDivisions = divisions,
                            selectedPerformanceDivision = selectedDivision,
                            performanceExams = exams,
                            selectedPerformanceExams = emptyList(),
                            performanceExamSelectorExpanded = false,
                            courses = emptyList(),
                            performanceLastUpdated = System.currentTimeMillis(),
                        )
                    }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            performanceLoading = false,
                            isLoading = false,
                            isRefreshing = false,
                            performanceError = ErrorText.forData(e),
                        )
                    }
                }
            }
        }

        private fun extractSemesterNumber(label: String): Int? {
            val romanMap =
                mapOf(
                    "I" to 1, "II" to 2, "III" to 3, "IV" to 4,
                    "V" to 5, "VI" to 6, "VII" to 7, "VIII" to 8,
                    "IX" to 9, "X" to 10, "XI" to 11, "XII" to 12,
                )
            val romanMatch = Regex("(?i)sem(?:ester)?[\\s\\-]*([IVXL]+)\\b").find(label)
            if (romanMatch != null) {
                val roman = romanMatch.groupValues[1].uppercase()
                romanMap[roman]?.let { return it }
            }
            val numMatch = Regex("(?i)sem(?:ester)?[\\s\\-]*(\\d+)").find(label)
            return numMatch?.groupValues?.get(1)?.toIntOrNull()
        }

        @Suppress("TooGenericExceptionCaught")
        private fun loadPerformanceSessions(year: String) {
            viewModelScope.launch {
                _state.update { it.copy(performanceLoading = true, performanceError = null) }
                try {
                    val user = authRepository.getUserInfo() ?: error("Not logged in")
                    val sessions =
                        gradesRepo.getPerformanceSessions(
                            admno = user.admno,
                            brId = user.brId,
                            academicYear = year,
                        )
                    _state.update {
                        it.copy(
                            performanceLoading = false,
                            performanceSessions = sessions,
                            performanceError = if (sessions.isEmpty()) "No exam sessions found" else null,
                        )
                    }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            performanceLoading = false,
                            performanceError = ErrorText.forData(e),
                        )
                    }
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun loadPerformanceClasses(session: String) {
            viewModelScope.launch {
                _state.update { it.copy(performanceLoading = true, performanceError = null) }
                try {
                    val user = authRepository.getUserInfo() ?: error("Not logged in")
                    val year = _state.value.selectedPerformanceYear.orEmpty()
                    val classes =
                        gradesRepo.getPerformanceClasses(
                            admno = user.admno,
                            brId = user.brId,
                            academicYear = year,
                            examSession = session,
                        )
                    _state.update {
                        it.copy(
                            performanceLoading = false,
                            performanceClasses = classes,
                            performanceError = if (classes.isEmpty()) "No classes found for this session" else null,
                        )
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(performanceLoading = false, performanceError = ErrorText.forData(e)) }
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun loadPerformanceDivisions(classId: String) {
            viewModelScope.launch {
                _state.update { it.copy(performanceLoading = true, performanceError = null) }
                try {
                    val user = authRepository.getUserInfo() ?: error("Not logged in")
                    val divisions =
                        gradesRepo.getPerformanceDivisions(
                            admno = user.admno,
                            brId = user.brId,
                            classId = classId,
                        )
                    _state.update {
                        it.copy(
                            performanceLoading = false,
                            performanceDivisions = divisions,
                            performanceError = if (divisions.isEmpty()) "No divisions found for this class" else null,
                        )
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(performanceLoading = false, performanceError = ErrorText.forData(e)) }
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun loadPerformanceExams(division: String) {
            viewModelScope.launch {
                _state.update { it.copy(performanceLoading = true, performanceError = null) }
                try {
                    val user = authRepository.getUserInfo() ?: error("Not logged in")
                    val s = _state.value
                    val exams =
                        gradesRepo.getPerformanceExams(
                            admno = user.admno,
                            brId = user.brId,
                            academicYear = s.selectedPerformanceYear.orEmpty(),
                            classId = s.selectedPerformanceClass.orEmpty(),
                            division = division,
                        )
                    _state.update {
                        it.copy(
                            performanceLoading = false,
                            performanceExams = exams,
                            performanceExamSelectorExpanded = false,
                            performanceError = if (exams.isEmpty()) "No exams found for this division" else null,
                        )
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(performanceLoading = false, performanceError = ErrorText.forData(e)) }
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun loadPerformanceMarks() {
            viewModelScope.launch {
                _state.update { it.copy(performanceLoading = true, performanceError = null) }
                try {
                    val user = authRepository.getUserInfo() ?: error("Not logged in")
                    val s = _state.value
                    val courses =
                        gradesRepo.getPerformanceMarks(
                            admno = user.admno,
                            brId = user.brId,
                            academicYear = s.selectedPerformanceYear.orEmpty(),
                            semester = s.performanceSemester,
                            examSession = s.selectedPerformanceSession.orEmpty(),
                            classId = s.selectedPerformanceClass.orEmpty(),
                            division = s.selectedPerformanceDivision.orEmpty(),
                            examIds = s.selectedPerformanceExams,
                            forceRefresh = true,
                        )
                    _state.update {
                        it.copy(
                            performanceLoading = false,
                            courses = courses,
                            showMarksInsights = false,
                            performanceLastUpdated = System.currentTimeMillis(),
                            performanceError = if (courses.isEmpty()) "No marks found for this exam" else null,
                        )
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(performanceLoading = false, performanceError = ErrorText.forData(e)) }
                }
            }
        }

        private companion object {
            const val MAX_REASONABLE_SEMESTER = 12
            const val PREF_PERF_YEAR = "grades_performance_year"
            const val PREF_PERF_SESSION = "grades_performance_session"
            const val PREF_PERF_CLASS = "grades_performance_class"
            const val PREF_PERF_DIVISION = "grades_performance_division"
        }
    }
