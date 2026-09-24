package com.ash.axis.ui.academics

import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.SELECTED_SEMESTER_CLASS_KEY
import com.ash.axis.data.repository.SELECTED_SEMESTER_YEAR_KEY
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.core.storage.PreferencesStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SemesterSelectionTest {
    @Test
    fun `saved selection needs no option lookup and switches with account`() =
        runTest {
            val coordinator = mockk<AcademicDataCoordinator>()
            val attendance = mockk<AttendanceRepository>()
            val preferences = mockk<PreferencesStore>()
            val context = MutableStateFlow<StudentRequestContext?>(StudentRequestContext("A", 1, "Client", "2026"))
            val year = MutableStateFlow("Y1")
            val classId = MutableStateFlow("C1")
            every { coordinator.activeContext } returns context
            every { preferences.getUserString(SELECTED_SEMESTER_YEAR_KEY, any()) } returns year
            every { preferences.getUserString(SELECTED_SEMESTER_CLASS_KEY, any()) } returns classId
            val values = mutableListOf<SemesterOption?>()
            val job = backgroundScope.launch { selectedSemester(coordinator, attendance, preferences).collect(values::add) }

            runCurrent()
            assertEquals(SemesterOption("Y1", "C1", ""), values.last())

            context.value = StudentRequestContext("B", 1, "Client", "2026")
            year.value = "Y2"
            classId.value = "C2"
            runCurrent()
            assertEquals(SemesterOption("Y2", "C2", ""), values.last())
            coVerify(exactly = 0) { attendance.getPreferredSemester(any(), any(), any(), any(), any()) }
            job.cancel()
        }
}
