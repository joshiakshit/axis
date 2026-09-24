package com.ash.axis.ui.planner

import com.ash.axis.data.repository.TimetableData
import com.ash.axis.domain.model.TimetableSlot
import java.time.DayOfWeek
import java.time.LocalDate

internal fun plannerWeekDates(
    monday: LocalDate,
    data: TimetableData,
): Map<LocalDate, List<TimetableSlot>> =
    (0L..6L).associate { offset ->
        val date = monday.plusDays(offset)
        val name = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        date to (data.dated?.get(date.toString()) ?: data.weekly[name].orEmpty())
    }

internal fun plannerWeekStart(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)
