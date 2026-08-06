package com.ash.axis.domain.usecase

import com.ash.axis.domain.model.TimetableSlot
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

class TimetableUseCaseTest {
    private val useCase = TimetableUseCase()

    @Test
    fun `getCurrentWeekRange starts on Monday`() {
        val (start, _) = useCase.getCurrentWeekRange()
        assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
    }

    @Test
    fun `getCurrentWeekRange ends on Sunday`() {
        val (_, end) = useCase.getCurrentWeekRange()
        assertEquals(DayOfWeek.SUNDAY, end.dayOfWeek)
    }

    @Test
    fun `shiftWeek moves forward by one week`() {
        val start = LocalDate.of(2025, 5, 19)
        val (newStart, newEnd) = useCase.shiftWeek(start, 1)
        assertEquals(LocalDate.of(2025, 5, 26), newStart)
        assertEquals(LocalDate.of(2025, 6, 1), newEnd)
    }

    @Test
    fun `shiftWeek moves backward by one week`() {
        val start = LocalDate.of(2025, 5, 19)
        val (newStart, _) = useCase.shiftWeek(start, -1)
        assertEquals(LocalDate.of(2025, 5, 12), newStart)
    }

    @Test
    fun `formatDateRange produces correct format`() {
        val start = LocalDate.of(2025, 5, 19)
        val end = LocalDate.of(2025, 5, 25)
        assertEquals("19 May - 25 May", useCase.formatDateRange(start, end))
    }

    @Test
    fun `timeToMinutes parses correctly`() {
        assertEquals(570, useCase.timeToMinutes("09:30"))
        assertEquals(0, useCase.timeToMinutes(""))
        assertEquals(720, useCase.timeToMinutes("12:00"))
    }

    @Test
    fun `sortSlotsByTime orders by fromTime`() {
        val slots =
            listOf(
                TimetableSlot(fromTime = "14:00", toTime = "15:00"),
                TimetableSlot(fromTime = "09:00", toTime = "10:00"),
                TimetableSlot(fromTime = "11:00", toTime = "12:00"),
            )
        val sorted = useCase.sortSlotsByTime(slots)
        assertEquals("09:00", sorted[0].fromTime)
        assertEquals("11:00", sorted[1].fromTime)
        assertEquals("14:00", sorted[2].fromTime)
    }

    @Test
    fun `currentSlotProgress returns null for future slot`() {
        val slot = TimetableSlot(fromTime = "23:59", toTime = "23:59")
        assertNull(useCase.currentSlotProgress(slot, isToday = true))
    }

    @Test
    fun `getAcadYear returns correct year range`() {
        val result = useCase.getAcadYear()
        assert(result.matches(Regex("""\d{4}-\d{4}""")))
    }

    @Test
    fun `cleanSubjectName strips semester prefix and type codes`() {
        val raw = "3rd Sem A - Data Structures LEC Section-1"
        val result = useCase.cleanSubjectName(raw)
        assertEquals("Data Structures", result)
    }

    @Test
    fun `cleanSubjectName strips room number`() {
        val raw = "Algorithms R201"
        val result = useCase.cleanSubjectName(raw, roomNo = "R201")
        assertEquals("Algorithms", result)
    }

    @Test
    fun `displaySubjectName prefers real subject name over subject full metadata`() {
        val slot =
            TimetableSlot(
                subjectId = "BCSE301",
                subCode = "BCSE301",
                subname = "Operating Systems",
                subject_full = "Alternate Faculty: Dr Example for EmpFirstName Payload",
            )

        assertEquals("Operating Systems", useCase.displaySubjectName(slot))
    }

    @Test
    fun `displaySubjectName rejects faculty blob fallback`() {
        val slot =
            TimetableSlot(
                subjectId = "BCSE301",
                subCode = "BCSE301",
                subject_full = "AltChangeId 42 Alternate Faculty Long Payload",
            )

        assertEquals("BCSE301", useCase.displaySubjectName(slot))
    }

    @Test
    fun `alternate teacher aliases deserialize`() {
        val json =
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                isLenient = true
            }
        val slot =
            json.decodeFromString<TimetableSlot>(
                """
                {
                  "AltChangeId": "42",
                  "EmpFirstNameAlt": "Priya",
                  "EmpLastNameAlt": "Sharma"
                }
                """.trimIndent(),
            )

        assertEquals("Priya Sharma", useCase.substituteTeacher(slot))
    }

    @Test
    fun `server class metadata does not replace subject name`() {
        val json =
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                isLenient = true
            }
        val slot =
            json.decodeFromString<TimetableSlot>(
                """
                {
                  "AltChangeId": "285438",
                  "EmpFirstName": "Amit",
                  "EmpLastName": "Sharma",
                  "EmpFirstName_alt": "Himani",
                  "EmpLastName_alt": "Piplani",
                  "SubId": "469824",
                  "LectType": "PR",
                  "sub_shortname": "R1UC425L",
                  "SubName": "Aptitude Proficiency",
                  "fromtime": "09:20",
                  "totime": "10:10",
                  "day": "Tue",
                  "subject_id": "469824",
                  "ClassName": {
                    "classes": "Btech_CSE II 2024-2025",
                    "prog_name": "Bachelor of Technology in Computer Science and Engineering",
                    "dept_name": "School of Computing Science & Engineering"
                  }
                }
                """.trimIndent(),
            )

        assertEquals("Aptitude Proficiency", useCase.displaySubjectName(slot))
        assertEquals("Himani Piplani", useCase.substituteTeacher(slot))
    }
}
