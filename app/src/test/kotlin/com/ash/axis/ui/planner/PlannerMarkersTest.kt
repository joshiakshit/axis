package com.ash.axis.ui.planner

import com.ash.axis.domain.model.StudentMarker
import com.ash.axis.domain.model.StudentMarkerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PlannerMarkersTest {
    private val today = LocalDate.of(2026, 10, 10)

    @Test
    fun `collapsed preview shows the next three plans`() {
        val markers =
            listOf(
                marker(1, today.minusDays(2), today.minusDays(1)),
                marker(2, today.plusDays(3)),
                marker(3, today),
                marker(4, today.plusDays(1)),
                marker(5, today.plusDays(2)),
            )

        val preview = plannerMarkerPreview(markers, today, expanded = false)

        assertEquals(listOf(3L, 4L, 5L), preview.visible.map { it.id })
        assertEquals(2, preview.hiddenCount)
    }

    @Test
    fun `expanded preview keeps upcoming plans before recent past plans`() {
        val markers =
            listOf(
                marker(1, today.minusDays(4), today.minusDays(2)),
                marker(2, today.plusDays(2)),
                marker(3, today.minusDays(1)),
                marker(4, today),
            )

        val preview = plannerMarkerPreview(markers, today, expanded = true)

        assertEquals(listOf(4L, 2L, 3L, 1L), preview.visible.map { it.id })
        assertEquals(0, preview.hiddenCount)
    }

    private fun marker(
        id: Long,
        startDate: LocalDate,
        endDate: LocalDate = startDate,
    ) = StudentMarker(id, "Marker $id", StudentMarkerType.EXAM, startDate, endDate)
}
