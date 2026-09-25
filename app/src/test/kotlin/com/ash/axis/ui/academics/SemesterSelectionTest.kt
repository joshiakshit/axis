package com.ash.axis.ui.academics

import com.ash.axis.data.academic.AcademicDataCoordinator
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.StudentRequestContext
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SemesterSelectionTest {
    @Test
    fun `saved selection needs no option lookup and switches with account`() =
        runTest {
            val attendance = mockk<AttendanceRepository>(relaxed = true)
            val timetable = mockk<TimetableRepository>(relaxed = true)
            val coordinator = AcademicDataCoordinator(attendance, timetable, mockk<AuthRepository>())
            val first = StudentRequestContext("A", 1, "Client", "2026")
            val second = first.copy(admno = "B")
            val monday = LocalDate.parse("2026-09-07")

            coordinator.activate(first, SemesterOption("Y1", "C1", ""), monday)
            assertEquals(first, coordinator.selectedSemester.value.context)
            assertEquals(SemesterOption("Y1", "C1", ""), coordinator.selectedSemester.value.option)
            coordinator.activate(second, SemesterOption("Y2", "C2", ""), monday)
            assertEquals(second, coordinator.selectedSemester.value.context)
            assertEquals(SemesterOption("Y2", "C2", ""), coordinator.selectedSemester.value.option)
            coVerify(exactly = 0) { attendance.getPreferredSemester(any(), any(), any(), any(), any()) }
            coordinator.deactivate()
        }
}
