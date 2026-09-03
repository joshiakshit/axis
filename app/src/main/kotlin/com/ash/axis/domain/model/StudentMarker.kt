package com.ash.axis.domain.model

import java.time.LocalDate

enum class StudentMarkerType(
    val label: String,
) {
    EXAM("Exam"),
    HOLIDAY("Holiday"),
}

data class StudentMarker(
    val id: Long,
    val title: String,
    val type: StudentMarkerType,
    val startDate: LocalDate,
    val endDate: LocalDate = startDate,
)

fun StudentMarker.includes(date: LocalDate): Boolean = date in startDate..endDate

// Exam periods and holidays both stop regular teaching, so their days drop out of the projection.
fun markerNoClassDates(markers: Iterable<StudentMarker>): Set<LocalDate> {
    val dates = mutableSetOf<LocalDate>()
    markers.forEach { marker ->
        var date = marker.startDate
        while (date <= marker.endDate) {
            dates += date
            date = date.plusDays(1)
        }
    }
    return dates
}
