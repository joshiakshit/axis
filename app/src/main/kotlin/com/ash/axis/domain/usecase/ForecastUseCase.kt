package com.ash.axis.domain.usecase

import com.ash.axis.domain.model.TimetableSlot
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

data class ForecastRow(
    val subCode: String,
    val subName: String,
    val lecType: String,
    val reachDate: LocalDate?,
    val weeklyCount: Int,
    val safeMisses: Int,
    val need: Int,
    val maxReachable: Double? = null,
)

class ForecastUseCase
    @Inject
    constructor(
        private val attendanceUseCase: AttendanceUseCase,
    ) {
        fun buildForecast(
            subjects: List<SubjectAttendance>,
            weeklySlots: Map<String, List<TimetableSlot>>,
            threshold: Int = 75,
            endDate: LocalDate? = null,
        ): List<ForecastRow> {
            val today = LocalDate.now()
            val horizon = endDate ?: today.plusDays(DEFAULT_HORIZON_DAYS)
            return subjects.filter { it.percent < threshold }.map { subject ->
                val matchingSlots = countWeeklySlots(subject.subCode, weeklySlots)
                val reachDate =
                    simulateRecovery(
                        present = subject.present,
                        total = subject.total,
                        threshold = threshold,
                        weeklySlots = matchingSlots,
                        startDate = today,
                        horizon = horizon,
                    )
                val maxReachable =
                    if (reachDate == null) {
                        simulateMaxReachable(subject.present, subject.total, matchingSlots, today, horizon)
                    } else {
                        null
                    }

                ForecastRow(
                    subCode = subject.subCode,
                    subName = subject.subName,
                    lecType = subject.lecType,
                    reachDate = reachDate,
                    weeklyCount = matchingSlots.values.sum(),
                    safeMisses = attendanceUseCase.bunkBudget(subject.present, subject.total, threshold),
                    need = attendanceUseCase.mustAttend(subject.present, subject.total, threshold),
                    maxReachable = maxReachable,
                )
            }
        }

        private fun countWeeklySlots(
            subCode: String,
            weeklySlots: Map<String, List<TimetableSlot>>,
        ): Map<DayOfWeek, Int> {
            val counts = mutableMapOf<DayOfWeek, Int>()
            for ((dayKey, slots) in weeklySlots) {
                val dow = dayKeyToDayOfWeek(dayKey) ?: continue
                val matching = slots.count { slotMatchesSubject(it, subCode) }
                if (matching > 0) counts[dow] = matching
            }
            return counts
        }

        private fun simulateRecovery(
            present: Int,
            total: Int,
            threshold: Int,
            weeklySlots: Map<DayOfWeek, Int>,
            startDate: LocalDate,
            horizon: LocalDate,
        ): LocalDate? {
            if (weeklySlots.isEmpty()) return null
            var p = present
            var t = total
            var date = startDate

            while (date < horizon) {
                date = date.plusDays(1)
                val slotsToday = weeklySlots[date.dayOfWeek] ?: 0
                p += slotsToday
                t += slotsToday
                if (t > 0 && p * 100.0 / t >= threshold) return date
            }
            return null
        }

        private fun simulateMaxReachable(
            present: Int,
            total: Int,
            weeklySlots: Map<DayOfWeek, Int>,
            startDate: LocalDate,
            horizon: LocalDate,
        ): Double {
            if (weeklySlots.isEmpty() && total > 0) return present * 100.0 / total
            var p = present
            var t = total
            var date = startDate
            while (date < horizon) {
                date = date.plusDays(1)
                val slotsToday = weeklySlots[date.dayOfWeek] ?: 0
                p += slotsToday
                t += slotsToday
            }
            return if (t > 0) p * 100.0 / t else 0.0
        }

        companion object {
            private const val DEFAULT_HORIZON_DAYS = 120L
        }

        private fun slotMatchesSubject(
            slot: TimetableSlot,
            subCode: String,
        ): Boolean {
            val code = subCode.lowercase()
            return listOfNotNull(
                slot.subCode.takeIf { it.isNotBlank() },
                slot.sub_shortname,
                slot.sub_short,
                slot.subjectId.takeIf { it.isNotBlank() },
            ).any { it.lowercase().contains(code) }
        }

        private fun dayKeyToDayOfWeek(key: String): DayOfWeek? {
            return when (key.lowercase().take(3)) {
                "mon" -> DayOfWeek.MONDAY
                "tue" -> DayOfWeek.TUESDAY
                "wed" -> DayOfWeek.WEDNESDAY
                "thu" -> DayOfWeek.THURSDAY
                "fri" -> DayOfWeek.FRIDAY
                "sat" -> DayOfWeek.SATURDAY
                "sun" -> DayOfWeek.SUNDAY
                else -> null
            }
        }
    }
