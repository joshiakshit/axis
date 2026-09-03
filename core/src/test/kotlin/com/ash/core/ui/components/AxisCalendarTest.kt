package com.ash.core.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarWeeksTest {
    @Test
    fun `month starting midweek is padded with leading nulls`() {
        val weeks = calendarWeeks(YearMonth.of(2026, 9))

        assertEquals(5, weeks.size)
        assertNull(weeks[0][0])
        assertEquals(LocalDate.of(2026, 9, 1), weeks[0][1])
    }

    @Test
    fun `month starting on monday has no leading nulls`() {
        val weeks = calendarWeeks(YearMonth.of(2026, 6))

        assertEquals(LocalDate.of(2026, 6, 1), weeks[0][0])
    }

    @Test
    fun `every week has seven cells`() {
        val weeks = calendarWeeks(YearMonth.of(2026, 2))

        assertTrue(weeks.all { it.size == 7 })
    }

    @Test
    fun `grid holds every day of the month exactly once`() {
        val weeks = calendarWeeks(YearMonth.of(2026, 9))

        val days = weeks.flatten().filterNotNull()
        assertEquals(30, days.size)
        assertEquals(days.distinct(), days)
    }
}

class DateRangeSelectionTest {
    private val base = LocalDate.of(2026, 10, 10)

    @Test
    fun `first tap sets the start and leaves the end open`() {
        val result = DateRangeSelection().toggle(base)

        assertEquals(base, result.start)
        assertNull(result.end)
    }

    @Test
    fun `second tap on a later date completes the range`() {
        val result = DateRangeSelection(start = base).toggle(base.plusDays(3))

        assertEquals(base, result.start)
        assertEquals(base.plusDays(3), result.end)
    }

    @Test
    fun `second tap on the same date makes a single day range`() {
        val result = DateRangeSelection(start = base).toggle(base)

        assertEquals(base, result.start)
        assertEquals(base, result.end)
    }

    @Test
    fun `tapping before the start restarts the range`() {
        val result = DateRangeSelection(start = base).toggle(base.minusDays(2))

        assertEquals(base.minusDays(2), result.start)
        assertNull(result.end)
    }

    @Test
    fun `tapping a complete range restarts it`() {
        val complete = DateRangeSelection(base, base.plusDays(4))

        val result = complete.toggle(base.plusDays(9))

        assertEquals(base.plusDays(9), result.start)
        assertNull(result.end)
    }

    @Test
    fun `covers reports dates inside a complete range`() {
        val range = DateRangeSelection(base, base.plusDays(3))

        assertTrue(range.covers(base))
        assertTrue(range.covers(base.plusDays(2)))
        assertTrue(range.covers(base.plusDays(3)))
        assertFalse(range.covers(base.plusDays(4)))
    }

    @Test
    fun `covers reports only the start when the range is open`() {
        val open = DateRangeSelection(start = base)

        assertTrue(open.covers(base))
        assertFalse(open.covers(base.plusDays(1)))
    }

    @Test
    fun `covers reports nothing when empty`() {
        assertFalse(DateRangeSelection().covers(base))
    }
}
