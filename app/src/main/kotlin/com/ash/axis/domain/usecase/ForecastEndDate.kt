package com.ash.axis.domain.usecase

import java.time.LocalDate

/** An unset or expired saved date uses the same 120-day horizon on both screens. */
object ForecastEndDate {
    fun resolve(
        saved: String,
        today: LocalDate = LocalDate.now(),
    ): LocalDate =
        runCatching { LocalDate.parse(saved) }.getOrNull()
            ?.takeUnless { it < today }
            ?: today.plusDays(120)
}
