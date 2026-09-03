package com.ash.core.ui.components

import java.time.LocalDate
import java.time.YearMonth

data class DateRangeSelection(
    val start: LocalDate? = null,
    val end: LocalDate? = null,
)

// Tap once to open a range, again to close it. A tap before the start, or on a closed range, starts over.
fun DateRangeSelection.toggle(date: LocalDate): DateRangeSelection =
    when {
        start == null || end != null -> DateRangeSelection(date)
        date < start -> DateRangeSelection(date)
        else -> DateRangeSelection(start, date)
    }

fun DateRangeSelection.covers(date: LocalDate): Boolean {
    val from = start ?: return false
    return date in from..(end ?: from)
}

// Monday-first month grid, padded with nulls to whole weeks.
fun calendarWeeks(month: YearMonth): List<List<LocalDate?>> {
    val cells =
        buildList<LocalDate?> {
            repeat(month.atDay(1).dayOfWeek.value - 1) { add(null) }
            for (day in 1..month.lengthOfMonth()) add(month.atDay(day))
            while (size % 7 != 0) add(null)
        }
    return cells.chunked(7)
}
