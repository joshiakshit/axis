package com.ash.axis.domain.usecase

import com.ash.axis.domain.model.TimetableSlot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PlannerUseCaseTest {
    private val planner = PlannerUseCase()
    private val monday = LocalDate.of(2026, 9, 7)
    private val subject = SubjectAttendance("CS101", "Math", "PP", 80, 100, 80.0)
    private val slot = TimetableSlot(subCode = "CS101", lectType = "PP")
    private val weekly = mapOf("Mon" to listOf(slot), "Wed" to listOf(slot))

    @Test
    fun `repeats the week through the inclusive forecast end`() {
        val row = forecast().single()
        assertEquals(82, row.projectedPresent)
        assertEquals(102, row.projectedTotal)
        assertEquals(82.0 * 100 / 102, row.projectedPercent, 0.001)
    }

    @Test
    fun `absence ranges overlap without counting classes twice`() {
        val row =
            forecast(
                absences =
                    listOf(
                        AbsenceRange(monday.plusDays(2), monday.plusWeeks(1)),
                        AbsenceRange(monday.plusDays(2), monday.plusDays(3)),
                    ),
            ).single()
        assertEquals(80, row.projectedPresent)
        assertEquals(102, row.projectedTotal)
        assertEquals(2, row.absencesPlanned)
    }

    @Test
    fun `saved no class blocks override absences and scheduled classes`() {
        val row =
            forecast(
                absences = listOf(AbsenceRange(monday.plusDays(2), monday.plusWeeks(1))),
                noClassDates = setOf(monday.plusDays(2)),
            ).single()
        assertEquals(80, row.projectedPresent)
        assertEquals(101, row.projectedTotal)
        assertEquals(1, row.absencesPlanned)
    }

    @Test
    fun `today attended missed and already counted are distinct`() {
        assertEquals(83, forecast(todayAttendance = TodayAttendance.ATTENDED).single().projectedPresent)
        assertEquals(103, forecast(todayAttendance = TodayAttendance.ATTENDED).single().projectedTotal)
        assertEquals(82, forecast(todayAttendance = TodayAttendance.MISSED).single().projectedPresent)
        assertEquals(103, forecast(todayAttendance = TodayAttendance.MISSED).single().projectedTotal)
        assertEquals(102, forecast(todayAttendance = TodayAttendance.ALREADY_INCLUDED).single().projectedTotal)
    }

    @Test
    fun `holiday today overrides the daily answer`() {
        assertEquals(102, forecast(todayAttendance = TodayAttendance.ATTENDED, noClassDates = setOf(monday)).single().projectedTotal)
    }

    @Test
    fun `absence dates outside the forecast do not affect it`() {
        val row =
            forecast(
                absences =
                    listOf(
                        AbsenceRange(monday.minusWeeks(1), monday),
                        AbsenceRange(monday.plusWeeks(2), monday.plusWeeks(3)),
                    ),
            ).single()
        assertEquals(82, row.projectedPresent)
        assertEquals(0, row.absencesPlanned)
    }

    @Test
    fun `sunday classes repeat and zero attendance stays finite`() {
        val row =
            planner.forecast(
                listOf(subject.copy(present = 0, total = 0)),
                mapOf("Sun" to listOf(slot)),
                monday,
                monday.plusDays(6),
                TodayAttendance.ALREADY_INCLUDED,
            ).single()
        assertEquals(1, row.projectedTotal)
        assertEquals(100.0, row.projectedPercent)
        assertEquals(0.0, row.currentPercent)
    }

    @Test
    fun `combined subject matches both lecture types and code aliases`() {
        val slots =
            listOf(
                TimetableSlot(subjectId = "CS101", sub_shortname = "DIFFERENT", lectType = "PP"),
                TimetableSlot(subCode = "CS101", lectType = "PR"),
            )
        val row =
            planner.forecast(
                listOf(subject.copy(lecType = "PP+PR")),
                mapOf("Wed" to slots),
                monday,
                monday.plusDays(2),
                TodayAttendance.ALREADY_INCLUDED,
            ).single()
        assertEquals(102, row.projectedTotal)
        assertEquals(2, row.weeklyClasses)
    }

    @Test
    fun `name fallback does not mix lecture types`() {
        val slots =
            listOf(
                TimetableSlot(subjectId = "999", subname = "Math", lectType = "PP"),
                TimetableSlot(subjectId = "999", subname = "Math", lectType = "PR"),
            )
        val row =
            planner.forecast(
                listOf(subject),
                mapOf("Wed" to slots),
                monday,
                monday.plusDays(2),
                TodayAttendance.ALREADY_INCLUDED,
            ).single()
        assertEquals(101, row.projectedTotal)
    }

    @Test
    fun `unmatched subject retains current totals and reports no weekly classes`() {
        val row =
            planner.forecast(
                listOf(subject),
                emptyMap(),
                monday,
                monday.plusWeeks(1),
                TodayAttendance.ALREADY_INCLUDED,
            ).single()
        assertEquals(80, row.projectedPresent)
        assertEquals(100, row.projectedTotal)
        assertEquals(0, row.weeklyClasses)
    }

    @Test
    fun `below target subjects appear first`() {
        val low = subject.copy(subCode = "LOW", present = 60, percent = 60.0)
        val rows = planner.forecast(listOf(subject, low), weekly, monday, monday.plusWeeks(1), TodayAttendance.ALREADY_INCLUDED)
        assertEquals("LOW", rows.first().code)
    }

    private fun forecast(
        absences: List<AbsenceRange> = emptyList(),
        noClassDates: Set<LocalDate> = emptySet(),
        todayAttendance: TodayAttendance = TodayAttendance.ALREADY_INCLUDED,
    ) = planner.forecast(listOf(subject), weekly, monday, monday.plusWeeks(1), todayAttendance, absences, noClassDates)
}
