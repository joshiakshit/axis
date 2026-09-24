package com.ash.axis.data.academic

import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.TimetableData
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.StudentRequestContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AcademicDataCoordinatorTest {
    @Test
    fun `resolved profile year is published without fabricating an attendance year`() =
        runTest {
            val attendance = mockk<AttendanceRepository>()
            val timetable = mockk<TimetableRepository>()
            val auth = mockk<AuthRepository>()
            val initial = StudentRequestContext("21001", 11, "clientMixedCase", "")
            val resolved = initial.copy(academicYear = "2026-2027")
            coEvery { attendance.deactivateAcademicData() } returns Unit
            coEvery { timetable.deactivateAcademicData() } returns Unit
            coEvery { auth.requireStudentRequestContext(false) } returns resolved
            coEvery { timetable.requestWeek(any(), any()) } returns
                MutableStateFlow<AcademicSnapshot<TimetableData>>(AcademicSnapshot())
            val coordinator = AcademicDataCoordinator(attendance, timetable, auth)

            coordinator.activate(initial, null, LocalDate.parse("2026-09-07"))

            assertEquals(resolved, coordinator.activeContext.value)
            coVerify(exactly = 0) { attendance.requestSummary(any(), any()) }
            coordinator.deactivate()
            assertNull(coordinator.activeContext.value)
        }
}
