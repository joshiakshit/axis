package com.ash.axis.domain.usecase

import com.ash.axis.domain.model.TimetableSlot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ForecastUseCaseTest {
    private val attendanceUseCase = AttendanceUseCase()
    private val forecastUseCase = ForecastUseCase(attendanceUseCase)

    @Test
    fun `buildForecast returns empty for subjects above threshold`() {
        val subjects =
            listOf(
                SubjectAttendance("CS101", "Math", "LEC", 80, 100, 80.0),
            )
        val result = forecastUseCase.buildForecast(subjects, emptyMap(), 75)
        assertEquals(0, result.size)
    }

    @Test
    fun `buildForecast includes subjects below threshold`() {
        val subjects =
            listOf(
                SubjectAttendance("CS101", "Math", "LEC", 60, 100, 60.0),
            )
        val timetable =
            mapOf(
                "Mon" to listOf(TimetableSlot(subjectId = "CS101", sub_shortname = "CS101")),
                "Wed" to listOf(TimetableSlot(subjectId = "CS101", sub_shortname = "CS101")),
            )
        val result = forecastUseCase.buildForecast(subjects, timetable, 75)
        assertEquals(1, result.size)
        assertEquals("CS101", result[0].subCode)
        assertEquals(2, result[0].weeklyCount)
    }

    @Test
    fun `buildForecast returns null reachDate when no weekly slots`() {
        val subjects =
            listOf(
                SubjectAttendance("CS101", "Math", "LEC", 60, 100, 60.0),
            )
        val result = forecastUseCase.buildForecast(subjects, emptyMap(), 75)
        assertEquals(1, result.size)
        assertNull(result[0].reachDate)
    }

    @Test
    fun `buildForecast computes reachDate when recovery possible`() {
        val subjects =
            listOf(
                SubjectAttendance("CS101", "Math", "LEC", 74, 100, 74.0),
            )
        val timetable =
            mapOf(
                "Mon" to listOf(TimetableSlot(subjectId = "CS101", sub_shortname = "CS101")),
                "Tue" to listOf(TimetableSlot(subjectId = "CS101", sub_shortname = "CS101")),
                "Wed" to listOf(TimetableSlot(subjectId = "CS101", sub_shortname = "CS101")),
                "Thu" to listOf(TimetableSlot(subjectId = "CS101", sub_shortname = "CS101")),
                "Fri" to listOf(TimetableSlot(subjectId = "CS101", sub_shortname = "CS101")),
            )
        val result = forecastUseCase.buildForecast(subjects, timetable, 75)
        assertNotNull(result[0].reachDate)
    }

    @Test
    fun `buildForecast sets correct need count`() {
        val subjects =
            listOf(
                SubjectAttendance("CS101", "Math", "LEC", 60, 100, 60.0),
            )
        val result = forecastUseCase.buildForecast(subjects, emptyMap(), 75)
        assertEquals(attendanceUseCase.mustAttend(60, 100, 75), result[0].need)
    }
}
