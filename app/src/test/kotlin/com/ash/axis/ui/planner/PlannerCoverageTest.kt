package com.ash.axis.ui.planner

import com.ash.axis.data.repository.TimetableData
import com.ash.axis.domain.model.TimetableSlot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PlannerCoverageTest {
    @Test
    fun `dated schedule overrides weekly template including a confirmed empty day`() {
        val monday = LocalDate.parse("2026-09-21")
        val template = listOf(slot("WEEKLY"))
        val actual = listOf(slot("ACTUAL"))
        val data =
            TimetableData(
                mapOf("Mon" to template),
                mapOf(monday.toString() to actual, monday.plusWeeks(1).toString() to emptyList()),
            )

        val current = plannerWeekDates(monday, data)

        assertEquals(actual, current[monday])
        assertEquals(emptyList<TimetableSlot>(), plannerWeekDates(monday.plusWeeks(1), data)[monday.plusWeeks(1)])
    }

    @Test
    fun `unloaded dates have no confirmed schedule`() {
        val monday = LocalDate.parse("2026-09-21")
        val dates = plannerWeekDates(monday, TimetableData(emptyMap(), emptyMap()))

        assertFalse(monday.plusWeeks(1) in dates)
        assertEquals(emptyList<TimetableSlot>(), dates[monday])
    }

    @Test
    fun `legacy weekly cache is an estimate for every date`() {
        val monday = LocalDate.parse("2026-09-21")

        val estimates = plannerEstimatedDates(monday, TimetableData(mapOf("Mon" to listOf(slot("WEEKLY"))), null))

        assertEquals((0L..6L).map(monday::plusDays).toSet(), estimates)
    }

    @Test
    fun `missing intermediate week prevents a complete horizon`() {
        val monday = LocalDate.parse("2026-09-21")
        val first = (0L..6L).map(monday::plusDays).toSet()
        val third = (14L..20L).map(monday::plusDays).toSet()

        assertEquals(
            ProjectionCoverage.LOADING,
            plannerProjectionCoverage(monday, monday.plusDays(20), first + third, emptySet(), emptySet()),
        )
        assertEquals(
            ProjectionCoverage.FAILED,
            plannerProjectionCoverage(monday, monday.plusDays(20), first + third, emptySet(), setOf(monday.plusWeeks(1))),
        )
    }

    @Test
    fun `weekly fallback stays labeled as an estimate after all weeks load`() {
        val monday = LocalDate.parse("2026-09-21")
        val dates = (0L..13L).map(monday::plusDays).toSet()

        assertEquals(
            ProjectionCoverage.ESTIMATE,
            plannerProjectionCoverage(monday, monday.plusDays(13), dates, setOf(monday.plusDays(8)), emptySet()),
        )
        assertEquals(
            ProjectionCoverage.COMPLETE,
            plannerProjectionCoverage(monday, monday.plusDays(13), dates, emptySet(), emptySet()),
        )
    }

    private fun slot(code: String) = TimetableSlot(subCode = code)
}
