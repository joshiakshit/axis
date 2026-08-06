package com.ash.axis.domain.usecase

import com.ash.axis.domain.model.TimetableSlot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PlannerUseCaseTest {
    private val attendanceUseCase = AttendanceUseCase()
    private val plannerUseCase = PlannerUseCase(attendanceUseCase)

    private val subjects =
        listOf(
            SubjectAttendance("CS101", "Math", "PP", 80, 100, 80.0),
            SubjectAttendance("CS102", "Physics", "PP", 60, 100, 60.0),
        )

    private val timetable =
        mapOf(
            "Mon" to listOf(TimetableSlot(subjectId = "CS101", sub_shortname = "CS101", lectType = "PP")),
            "Wed" to
                listOf(
                    TimetableSlot(subjectId = "CS101", sub_shortname = "CS101", lectType = "PP"),
                    TimetableSlot(subjectId = "CS102", sub_shortname = "CS102", lectType = "PP"),
                ),
        )

    private fun nextDayOfWeek(dow: DayOfWeek): LocalDate {
        val today = LocalDate.now()
        val delta = (dow.value - today.dayOfWeek.value + 7) % 7
        return today.plusDays(if (delta == 0) 7L else delta.toLong())
    }

    private fun buildDateTimetable(date: LocalDate): Map<LocalDate, List<TimetableSlot>> {
        val dayName =
            mapOf(
                DayOfWeek.MONDAY to "Mon",
                DayOfWeek.TUESDAY to "Tue",
                DayOfWeek.WEDNESDAY to "Wed",
            )
        val slots = timetable[dayName[date.dayOfWeek]] ?: emptyList()
        return if (slots.isNotEmpty()) mapOf(date to slots) else emptyMap()
    }

    @Test
    fun `buildPlannerSubjects sets correct weekly count`() {
        val result = plannerUseCase.buildPlannerSubjects(subjects, timetable, 75)
        val math = result.find { it.code == "CS101" }!!
        assertEquals(2, math.weeklyCount)
    }

    @Test
    fun `buildPlannerSubjects computes bunkable and need`() {
        val result = plannerUseCase.buildPlannerSubjects(subjects, timetable, 75)
        val math = result.find { it.code == "CS101" }!!
        val physics = result.find { it.code == "CS102" }!!
        assertTrue(math.bunkable > 0)
        assertTrue(physics.need > 0)
    }

    @Test
    fun `analyzeDaySafety returns no classes for empty day`() {
        val plannerSubjects = plannerUseCase.buildPlannerSubjects(subjects, timetable, 75)
        val safety = plannerUseCase.analyzeDaySafety("Tue", plannerSubjects, timetable)
        assertEquals(false, safety.hasClasses)
        assertTrue(safety.safe)
    }

    @Test
    fun `analyzeDaySafety detects risky subjects`() {
        val lowSubjects =
            listOf(
                SubjectAttendance("CS102", "Physics", "PP", 60, 100, 60.0),
            )
        val plannerSubjects = plannerUseCase.buildPlannerSubjects(lowSubjects, timetable, 75)
        val safety = plannerUseCase.analyzeDaySafety("Wed", plannerSubjects, timetable)
        assertTrue(safety.hasClasses)
    }

    @Test
    fun `computeProjected returns empty for no selected dates`() {
        val plannerSubjects = plannerUseCase.buildPlannerSubjects(subjects, timetable, 75)
        val result = plannerUseCase.computeProjected(plannerSubjects, emptySet(), emptyMap(), 75)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `computeProjected computes impact for future dates`() {
        val plannerSubjects = plannerUseCase.buildPlannerSubjects(subjects, timetable, 75)
        val nextMonday = nextDayOfWeek(DayOfWeek.MONDAY)
        val dateTimetable = buildDateTimetable(nextMonday)
        val result = plannerUseCase.computeProjected(plannerSubjects, setOf(nextMonday), dateTimetable, 75)
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.delta <= 0 })
        val math = result.find { it.code == "CS101" }!!
        assertEquals(80, math.currentPresent)
        assertEquals(100, math.currentTotal)
        assertTrue(math.projectedTotal > math.currentTotal)
    }

    @Test
    fun `combined PP+PR subject counts both slot types`() {
        val combined =
            listOf(SubjectAttendance("CS101", "Math", "PP+PR", 80, 100, 80.0))
        val mixedTimetable =
            mapOf(
                "Mon" to
                    listOf(
                        TimetableSlot(subjectId = "CS101", sub_shortname = "CS101", lectType = "PP"),
                        TimetableSlot(subjectId = "CS101", sub_shortname = "CS101", lectType = "PR"),
                    ),
            )
        val plannerSubjects = plannerUseCase.buildPlannerSubjects(combined, mixedTimetable, 75)
        assertEquals(2, plannerSubjects.first().weeklyCount)
    }

    @Test
    fun `computeProjected populates ratio fields`() {
        val plannerSubjects = plannerUseCase.buildPlannerSubjects(subjects, timetable, 75)
        val nextMon = nextDayOfWeek(DayOfWeek.MONDAY)
        val nextWed = nextDayOfWeek(DayOfWeek.WEDNESDAY)
        val dateTimetable = buildDateTimetable(nextMon) + buildDateTimetable(nextWed)
        val result = plannerUseCase.computeProjected(plannerSubjects, setOf(nextMon), dateTimetable, 75)
        assertTrue(result.isNotEmpty())
        val math = result.find { it.code == "CS101" }!!
        assertEquals(80, math.currentPresent)
        assertEquals(100, math.currentTotal)
        assertEquals(102, math.projectedTotal)
        assertEquals(81, math.projectedPresent)
    }

    @Test
    fun `maxReachable counts exact slots to semester end`() {
        val plannerSubjects = plannerUseCase.buildPlannerSubjects(subjects, timetable, 75)
        val nextMon = nextDayOfWeek(DayOfWeek.MONDAY)
        val dateTimetable = buildDateTimetable(nextMon)
        val semesterEnd = nextMon.plusDays(13)
        val result =
            plannerUseCase.computeProjected(
                plannerSubjects,
                setOf(nextMon),
                dateTimetable,
                75,
                semesterEnd,
                timetable,
                today = nextMon,
            )
        assertTrue(result.isNotEmpty())
        val math = result.find { it.code == "CS101" }!!
        assertTrue(math.maxReachable != null)
        assertTrue(math.maxReachable!! > math.projectedPercent)
        val mondays = countDayOccurrences(nextMon, semesterEnd, DayOfWeek.MONDAY)
        val wednesdays = countDayOccurrences(nextMon, semesterEnd, DayOfWeek.WEDNESDAY)
        val expectedSemClasses = mondays * 1L + wednesdays * 1L
        val expectedMax = (80 + expectedSemClasses - 1) * 100.0 / (100 + expectedSemClasses)
        assertEquals(expectedMax, math.maxReachable!!, 0.01)
    }

    @Test
    fun `maxReachable is null without semester end`() {
        val plannerSubjects = plannerUseCase.buildPlannerSubjects(subjects, timetable, 75)
        val nextMon = nextDayOfWeek(DayOfWeek.MONDAY)
        val dateTimetable = buildDateTimetable(nextMon)
        val result = plannerUseCase.computeProjected(plannerSubjects, setOf(nextMon), dateTimetable, 75, null)
        assertTrue(result.isNotEmpty())
        result.forEach { assertTrue(it.maxReachable == null) }
    }

    private fun countDayOccurrences(
        start: LocalDate,
        end: LocalDate,
        dow: DayOfWeek,
    ): Int {
        var count = 0
        var date = start
        while (date <= end) {
            if (date.dayOfWeek == dow) count++
            date = date.plusDays(1)
        }
        return count
    }

    @Test
    fun `slot matching falls back to subjectId when sub_shortname differs`() {
        val mismatchTimetable =
            mapOf(
                "Mon" to
                    listOf(
                        TimetableSlot(subjectId = "CS101", sub_shortname = "MATH101", lectType = "PP"),
                    ),
            )
        val plannerSubjects = plannerUseCase.buildPlannerSubjects(subjects, mismatchTimetable, 75)
        assertEquals(1, plannerSubjects.find { it.code == "CS101" }!!.weeklyCount)
    }

    @Test
    fun `computeProjected resolves via subjectId fallback`() {
        val plannerSubjects = plannerUseCase.buildPlannerSubjects(subjects, timetable, 75)
        val nextMon = nextDayOfWeek(DayOfWeek.MONDAY)
        val dateTimetable =
            mapOf(
                nextMon to
                    listOf(
                        TimetableSlot(subjectId = "CS101", sub_shortname = "DIFFERENT", lectType = "PP"),
                    ),
            )
        val result = plannerUseCase.computeProjected(plannerSubjects, setOf(nextMon), dateTimetable, 75)
        assertTrue(result.isNotEmpty())
        assertEquals("CS101", result.first().code)
    }

    @Test
    fun `analyzeDaySafety resolves via subjectId fallback`() {
        val mismatchTimetable =
            mapOf(
                "Wed" to
                    listOf(
                        TimetableSlot(subjectId = "CS102", sub_shortname = "PHYS102", lectType = "PP"),
                    ),
            )
        val lowSubjects =
            listOf(SubjectAttendance("CS102", "Physics", "PP", 60, 100, 60.0))
        val plannerSubjects = plannerUseCase.buildPlannerSubjects(lowSubjects, mismatchTimetable, 75)
        val safety = plannerUseCase.analyzeDaySafety("Wed", plannerSubjects, mismatchTimetable)
        assertTrue(safety.hasClasses)
    }

    @Test
    fun `slot matching uses subCode field when subjectId differs`() {
        val ttWithSubCode =
            mapOf(
                "Mon" to
                    listOf(
                        TimetableSlot(
                            subjectId = "99999",
                            subCode = "CS101",
                            sub_shortname = "IRRELEVANT",
                            lectType = "PP",
                        ),
                    ),
            )
        val plannerSubjects = plannerUseCase.buildPlannerSubjects(subjects, ttWithSubCode, 75)
        assertEquals(1, plannerSubjects.find { it.code == "CS101" }!!.weeklyCount)
    }

    @Test
    fun `computeProjected falls back to name matching`() {
        val noCodeMatch =
            listOf(SubjectAttendance("CS101", "Mathematics", "PP", 80, 100, 80.0))
        val nameOnlyTimetable =
            mapOf(
                "Mon" to
                    listOf(
                        TimetableSlot(
                            subjectId = "99999",
                            subCode = "",
                            sub_shortname = "NOMATCH",
                            subname = "Mathematics",
                            lectType = "PP",
                        ),
                    ),
            )
        val plannerSubjects = plannerUseCase.buildPlannerSubjects(noCodeMatch, nameOnlyTimetable, 75)
        assertEquals(1, plannerSubjects.first().weeklyCount)
        val nextMon = nextDayOfWeek(DayOfWeek.MONDAY)
        val dateTimetable =
            mapOf(
                nextMon to
                    listOf(
                        TimetableSlot(
                            subjectId = "99999",
                            subCode = "",
                            sub_shortname = "NOMATCH",
                            subname = "Mathematics",
                            lectType = "PP",
                        ),
                    ),
            )
        val result = plannerUseCase.computeProjected(plannerSubjects, setOf(nextMon), dateTimetable, 75)
        assertTrue(result.isNotEmpty())
        assertEquals("CS101", result.first().code)
    }

    @Test
    fun `computeProjected handles combined PP+PR absences`() {
        val combined =
            listOf(SubjectAttendance("CS101", "Math", "PP+PR", 80, 100, 80.0))
        val nextMonday = nextDayOfWeek(DayOfWeek.MONDAY)
        val dateTimetable =
            mapOf(
                nextMonday to
                    listOf(
                        TimetableSlot(subjectId = "CS101", sub_shortname = "CS101", lectType = "PP"),
                        TimetableSlot(subjectId = "CS101", sub_shortname = "CS101", lectType = "PR"),
                    ),
            )
        val plannerSubjects = plannerUseCase.buildPlannerSubjects(combined, dateTimetable.values.first().let { mapOf("Mon" to it) }, 75)
        val result = plannerUseCase.computeProjected(plannerSubjects, setOf(nextMonday), dateTimetable, 75)
        assertTrue(result.isNotEmpty())
        assertEquals(2, result.first().absencesPlanned)
    }
}
