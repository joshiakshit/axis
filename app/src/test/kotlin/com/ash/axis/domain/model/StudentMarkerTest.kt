package com.ash.axis.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StudentMarkerTest {
    @Test
    fun `a single day marker covers only its own date`() {
        val exam = marker(StudentMarkerType.EXAM, LocalDate.of(2026, 10, 15))

        assertTrue(exam.includes(LocalDate.of(2026, 10, 15)))
        assertFalse(exam.includes(LocalDate.of(2026, 10, 14)))
        assertFalse(exam.includes(LocalDate.of(2026, 10, 16)))
    }

    @Test
    fun `a marker block covers every day between its bounds`() {
        val block = marker(StudentMarkerType.EXAM, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 18))

        assertTrue(block.includes(LocalDate.of(2026, 10, 15)))
        assertTrue(block.includes(LocalDate.of(2026, 10, 17)))
        assertTrue(block.includes(LocalDate.of(2026, 10, 18)))
        assertFalse(block.includes(LocalDate.of(2026, 10, 19)))
    }

    @Test
    fun `a marked range expands into every day it covers`() {
        val markers =
            listOf(marker(StudentMarkerType.HOLIDAY, LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 11)))

        assertEquals(
            setOf(
                LocalDate.of(2026, 11, 8),
                LocalDate.of(2026, 11, 9),
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 11),
            ),
            markerNoClassDates(markers),
        )
    }

    @Test
    fun `exam periods clear classes just like holidays do`() {
        val markers =
            listOf(
                marker(StudentMarkerType.EXAM, LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 23)),
                marker(StudentMarkerType.HOLIDAY, LocalDate.of(2026, 10, 2)),
            )

        assertEquals(
            setOf(
                LocalDate.of(2026, 10, 2),
                LocalDate.of(2026, 10, 21),
                LocalDate.of(2026, 10, 22),
                LocalDate.of(2026, 10, 23),
            ),
            markerNoClassDates(markers),
        )
    }

    @Test
    fun `overlapping markers do not double count a date`() {
        val markers =
            listOf(
                marker(StudentMarkerType.EXAM, LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 23)),
                marker(StudentMarkerType.HOLIDAY, LocalDate.of(2026, 10, 22)),
            )

        assertEquals(3, markerNoClassDates(markers).size)
    }

    @Test
    fun `no markers yields an empty set`() {
        assertEquals(emptySet<LocalDate>(), markerNoClassDates(emptyList()))
    }

    private fun marker(
        type: StudentMarkerType,
        startDate: LocalDate,
        endDate: LocalDate = startDate,
    ) = StudentMarker(id = 1, title = "Marker", type = type, startDate = startDate, endDate = endDate)
}
