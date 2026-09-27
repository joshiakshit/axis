package com.ash.axis.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ForecastEndDateTest {
    private val today = LocalDate.of(2026, 9, 27)

    @Test
    fun `saved future date remains the forecast end`() {
        assertEquals(today.plusDays(30), ForecastEndDate.resolve(today.plusDays(30).toString(), today))
    }

    @Test
    fun `unset invalid and expired dates use the same horizon`() {
        listOf("", "invalid", today.minusDays(1).toString()).forEach { saved ->
            assertEquals(today.plusDays(120), ForecastEndDate.resolve(saved, today))
        }
    }
}
