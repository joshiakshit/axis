package com.ash.axis.domain.usecase

import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

class AttendanceUseCase
    @Inject
    constructor() {
        fun bunkBudget(
            attended: Int,
            total: Int,
            threshold: Int = 75,
        ): Int {
            if (total == 0 || threshold <= 0 || threshold >= 100) return 0
            return max(0, floor(attended * 100.0 / threshold - total).toInt())
        }

        fun mustAttend(
            attended: Int,
            total: Int,
            threshold: Int = 75,
        ): Int {
            if (total == 0 || threshold <= 0 || threshold >= 100) return 0
            return max(0, ceil((threshold * total - 100.0 * attended) / (100 - threshold)).toInt())
        }

        fun tone(
            percent: Double,
            threshold: Int = 75,
        ): AttendanceTone {
            return when {
                percent >= threshold -> AttendanceTone.OK
                percent >= threshold * 0.93 -> AttendanceTone.WARN
                else -> AttendanceTone.BAD
            }
        }

        fun atRiskCount(
            subjects: List<SubjectAttendance>,
            threshold: Int = 75,
        ): Int {
            return subjects.count { it.percent < threshold }
        }

        fun totalBunkable(
            subjects: List<SubjectAttendance>,
            threshold: Int = 75,
        ): Int {
            return subjects.filter { it.percent >= threshold }
                .sumOf { bunkBudget(it.present, it.total, threshold) }
        }

        fun combineSubjects(subjects: List<SubjectAttendance>): List<SubjectAttendance> {
            return subjects.groupBy { it.subCode.uppercase() }.flatMap { (_, group) ->
                if (group.size <= 1) return@flatMap group
                val hasPP = group.any { it.lecType.equals("PP", ignoreCase = true) }
                val hasPR = group.any { it.lecType.equals("PR", ignoreCase = true) }
                if (hasPP && hasPR) {
                    val totalPresent = group.sumOf { it.present }
                    val totalTotal = group.sumOf { it.total }
                    val pct = if (totalTotal > 0) totalPresent * 100.0 / totalTotal else 0.0
                    listOf(
                        SubjectAttendance(
                            subCode = group.first().subCode,
                            subName = group.first().subName,
                            lecType = "PP+PR",
                            present = totalPresent,
                            total = totalTotal,
                            percent = pct,
                        ),
                    )
                } else {
                    group
                }
            }
        }
    }

enum class AttendanceTone { OK, WARN, BAD }

data class SubjectAttendance(
    val subCode: String,
    val subName: String,
    val lecType: String,
    val present: Int,
    val total: Int,
    val percent: Double,
)
