package com.ash.axis.domain.usecase

import com.ash.axis.domain.model.TimetableSlot
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class AbsenceRange(val start: LocalDate, val end: LocalDate = start) {
    init {
        require(!end.isBefore(start))
    }
}

data class ProjectedSubject(
    val code: String,
    val name: String,
    val lecType: String,
    val currentPresent: Int,
    val currentTotal: Int,
    val projectedPresent: Int,
    val projectedTotal: Int,
    val absencesPlanned: Int,
    val weeklyClasses: Int,
) {
    val currentPercent: Double get() = if (currentTotal == 0) 0.0 else currentPresent * 100.0 / currentTotal
    val projectedPercent: Double get() = if (projectedTotal == 0) 0.0 else projectedPresent * 100.0 / projectedTotal
}

enum class TodayAttendance { ATTENDED, MISSED, ALREADY_INCLUDED }

class PlannerUseCase
    @Inject
    constructor() {
        @Suppress("LongParameterList", "CyclomaticComplexMethod", "NestedBlockDepth")
        fun forecast(
            subjects: List<SubjectAttendance>,
            weeklyTimetable: Map<String, List<TimetableSlot>>,
            today: LocalDate,
            end: LocalDate,
            todayAttendance: TodayAttendance,
            absences: List<AbsenceRange> = emptyList(),
            noClassDates: Set<LocalDate> = emptySet(),
            threshold: Int = 75,
        ): List<ProjectedSubject> {
            require(!end.isBefore(today))
            val codes = buildSlotMatcher(subjects)
            val names = buildNameMatcher(subjects)
            val schedule = weeklyTimetable.mapValues { (_, slots) -> slots.mapNotNull { resolveSlotOwner(it, codes, names) } }
            val weeklyCounts = schedule.values.flatten().groupingBy { it }.eachCount()
            val totals = mutableMapOf<String, Int>()
            val missed = mutableMapOf<String, Int>()
            generateSequence(today) { it.plusDays(1) }.takeWhile { it <= end }.forEach { date ->
                if (date in noClassDates || (date == today && todayAttendance == TodayAttendance.ALREADY_INCLUDED)) return@forEach
                val absent = if (date == today) todayAttendance == TodayAttendance.MISSED else absences.any { date in it.start..it.end }
                val day = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                schedule[day].orEmpty().forEach { owner ->
                    totals[owner] = (totals[owner] ?: 0) + 1
                    if (absent) missed[owner] = (missed[owner] ?: 0) + 1
                }
            }
            return subjects.map { subject ->
                val owner = "${subject.subCode.uppercase().trim()}_${subject.lecType.uppercase()}"
                val total = totals[owner] ?: 0
                val absent = missed[owner] ?: 0
                ProjectedSubject(
                    subject.subCode, subject.subName, subject.lecType, subject.present, subject.total,
                    subject.present + total - absent, subject.total + total, absent, weeklyCounts[owner] ?: 0,
                )
            }.sortedWith(compareBy<ProjectedSubject> { it.projectedPercent >= threshold }.thenBy { it.projectedPercent }.thenBy { it.name })
        }

        private fun slotCodeCandidates(slot: TimetableSlot): List<String> =
            listOfNotNull(
                slot.subCode.uppercase().trim().takeIf { it.isNotEmpty() },
                slot.sub_shortname?.uppercase()?.trim()?.takeIf { it.isNotEmpty() },
                slot.sub_short?.uppercase()?.trim()?.takeIf { it.isNotEmpty() },
                slot.subjectId.uppercase().trim().takeIf { it.isNotEmpty() },
            )

        private fun resolveSlotOwner(
            slot: TimetableSlot,
            slotMatcher: Map<String, String>,
            nameMatcher: Map<String, String> = emptyMap(),
        ): String? {
            val slotLecType = slot.lectType.uppercase().trim()
            slotCodeCandidates(slot).firstNotNullOfOrNull { code ->
                slotMatcher["${code}_$slotLecType"]
            }?.let { return it }

            if (nameMatcher.isNotEmpty()) {
                val slotName =
                    (slot.subname ?: slot.subjectName ?: slot.subject_full)
                        ?.uppercase()?.trim()?.takeIf { it.isNotEmpty() }
                if (slotName != null) {
                    nameMatcher["${slotName}_$slotLecType"]?.let { return it }
                }
            }
            return null
        }

        private fun buildSlotMatcher(subjects: List<SubjectAttendance>): Map<String, String> {
            val map = mutableMapOf<String, String>()
            for (s in subjects) {
                val code = s.subCode.uppercase().trim()
                val ownerKey = "${code}_${s.lecType.uppercase()}"
                if (s.lecType.equals("PP+PR", ignoreCase = true)) {
                    map["${code}_PP"] = ownerKey
                    map["${code}_PR"] = ownerKey
                } else {
                    map[ownerKey] = ownerKey
                }
            }
            return map
        }

        private fun buildNameMatcher(subjects: List<SubjectAttendance>): Map<String, String> {
            val map = mutableMapOf<String, String>()
            for (s in subjects) {
                val name = s.subName.uppercase().trim()
                if (name.isEmpty()) continue
                val ownerKey = "${s.subCode.uppercase().trim()}_${s.lecType.uppercase()}"
                if (s.lecType.equals("PP+PR", ignoreCase = true)) {
                    map["${name}_PP"] = ownerKey
                    map["${name}_PR"] = ownerKey
                } else {
                    map["${name}_${s.lecType.uppercase()}"] = ownerKey
                }
            }
            return map
        }
    }
