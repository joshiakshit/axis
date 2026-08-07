package com.ash.axis.ui.timetable

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// The continuous day pager is a bounded window centred on the anchor date (captured at ViewModel
// init). One page == one day, so swiping flows across week boundaries with no wall.
internal object TimetablePaging {
    const val DAY_WINDOW_RADIUS = 365
    const val PAGE_COUNT = DAY_WINDOW_RADIUS * 2 + 1
    const val TODAY_PAGE = DAY_WINDOW_RADIUS

    // A jump farther than this snaps instantly instead of animating page-by-page.
    const val NEARBY_PAGE_THRESHOLD = 7
}

internal fun dateForPage(
    anchor: LocalDate,
    page: Int,
): LocalDate = anchor.plusDays((page - TimetablePaging.TODAY_PAGE).toLong())

internal fun pageForDate(
    anchor: LocalDate,
    date: LocalDate,
): Int =
    (TimetablePaging.TODAY_PAGE + ChronoUnit.DAYS.between(anchor, date))
        .toInt()
        .coerceIn(0, TimetablePaging.PAGE_COUNT - 1)
