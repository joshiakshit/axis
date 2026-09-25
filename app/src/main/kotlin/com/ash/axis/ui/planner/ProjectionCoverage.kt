package com.ash.axis.ui.planner

import com.ash.axis.data.repository.TimetableData
import com.ash.axis.domain.model.TimetableSlot
import java.time.DayOfWeek
import java.time.LocalDate

enum class ProjectionCoverage { LOADING, FAILED, ESTIMATE, COMPLETE, LIMITED }

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

internal fun plannerEstimatedDates(
    monday: LocalDate,
    data: TimetableData,
): Set<LocalDate> =
    (0L..6L).map(monday::plusDays).filterTo(mutableSetOf()) { date ->
        data.dated?.containsKey(date.toString()) != true
    }

@Suppress("ReturnCount")
internal fun plannerProjectionCoverage(
    today: LocalDate,
    horizon: LocalDate?,
    coveredDates: Set<LocalDate>,
    estimatedDates: Set<LocalDate>,
    failedWeeks: Set<LocalDate>,
    pendingWeeks: Set<LocalDate> = emptySet(),
): ProjectionCoverage {
    val end = maxOf(today, horizon ?: today)
    val weeks =
        generateSequence(plannerWeekStart(today)) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(end) }
            .toList()
    if (weeks.any { it in failedWeeks }) return ProjectionCoverage.FAILED
    if (weeks.any { it in pendingWeeks }) return ProjectionCoverage.LOADING
    if (weeks.any { week -> (0L..6L).any { week.plusDays(it) !in coveredDates } }) return ProjectionCoverage.LOADING
    if (horizon == null || estimatedDates.any { it in today..end }) return ProjectionCoverage.ESTIMATE
    return ProjectionCoverage.COMPLETE
}
