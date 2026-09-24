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

    private fun slot(code: String) = TimetableSlot(subCode = code)
}
