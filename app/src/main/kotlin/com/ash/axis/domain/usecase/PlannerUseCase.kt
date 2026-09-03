package com.ash.axis.domain.usecase

import com.ash.axis.domain.model.TimetableSlot
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

data class PlannerSubject(
    val code: String,
    val name: String,
    val lecType: String,
    val present: Int,
    val total: Int,
    val percent: Double,
    val bunkable: Int,
    val need: Int,
    val tone: AttendanceTone,
    val weeklyCount: Int,
)

data class DaySafety(
    val day: String,
    val hasClasses: Boolean,
    val slotCount: Int,
    val safe: Boolean,
    val riskySubjects: List<String>,
)

data class ProjectedSubject(
    val code: String,
    val name: String,
    val lecType: String,
    val currentPresent: Int,
    val currentTotal: Int,
    val currentPercent: Double,
    val projectedPresent: Int,
    val projectedTotal: Int,
    val baselinePercent: Double,
    val projectedPercent: Double,
    val absencesPlanned: Int,
    val delta: Double,
    val baselineTone: AttendanceTone,
    val projectedTone: AttendanceTone,
    val maxReachable: Double? = null,
    val maxPresent: Int? = null,
    val maxTotal: Int? = null,
)

enum class TodayAttendance { ATTENDED, MISSED, NO_CLASSES }

class PlannerUseCase
    @Inject
    constructor(
        private val attendanceUseCase: AttendanceUseCase,
    ) {
        fun buildPlannerSubjects(
            subjects: List<SubjectAttendance>,
            timetable: Map<String, List<TimetableSlot>>,
            threshold: Int = 75,
        ): List<PlannerSubject> {
            return subjects.map { s ->
                val weeklyCount = countWeeklySlots(s, timetable)
                PlannerSubject(
                    code = s.subCode,
                    name = s.subName,
                    lecType = s.lecType,
                    present = s.present,
                    total = s.total,
                    percent = s.percent,
                    bunkable = attendanceUseCase.bunkBudget(s.present, s.total, threshold),
                    need = attendanceUseCase.mustAttend(s.present, s.total, threshold),
                    tone = attendanceUseCase.tone(s.percent, threshold),
                    weeklyCount = weeklyCount,
                )
            }
        }

        @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
        fun analyzeDaySafety(
            day: String,
            subjects: List<PlannerSubject>,
            timetable: Map<String, List<TimetableSlot>>,
        ): DaySafety {
            val daySlots = timetable[day] ?: emptyList()
            if (daySlots.isEmpty()) return DaySafety(day, false, 0, true, emptyList())

            val slotMatcher = buildSlotMatcher(subjects)
            val names = buildNameMatcher(subjects)
            val subjectIndex = subjects.associateBy { "${it.code.uppercase()}_${it.lecType.uppercase()}" }

            val perOwner = mutableMapOf<String, Int>()
            for (slot in daySlots) {
                val ownerKey = resolveSlotOwner(slot, slotMatcher, names) ?: continue
                perOwner[ownerKey] = (perOwner[ownerKey] ?: 0) + 1
            }

            val risky = mutableListOf<String>()
            for ((ownerKey, count) in perOwner) {
                val subj = subjectIndex[ownerKey] ?: continue
                if (subj.bunkable < count) risky.add(subj.code)
            }

            return DaySafety(day, true, daySlots.size, risky.isEmpty(), risky)
        }

        @Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "LongMethod", "LongParameterList")
        fun computeProjected(
            subjects: List<PlannerSubject>,
            selectedDates: Set<LocalDate>,
            dateTimetable: Map<LocalDate, List<TimetableSlot>>,
            threshold: Int = 75,
            semesterEnd: LocalDate? = null,
            weeklyTimetable: Map<String, List<TimetableSlot>> = emptyMap(),
            today: LocalDate = LocalDate.now(),
            includeNoAbsence: Boolean = false,
            noClassDates: Set<LocalDate> = emptySet(),
            projectionEnd: LocalDate? = dateTimetable.keys.maxOrNull(),
            todayAttendance: TodayAttendance = TodayAttendance.NO_CLASSES,
        ): List<ProjectedSubject> {
            if (selectedDates.isEmpty() && !includeNoAbsence) return emptyList()

            val futureDates = selectedDates.filter { it > today }.sorted()
            val hasTodayOutcome = todayAttendance != TodayAttendance.NO_CLASSES && dateTimetable[today].orEmpty().isNotEmpty()
            if (futureDates.isEmpty() && !includeNoAbsence && !hasTodayOutcome) return emptyList()

            val subjectKeys = buildSlotMatcher(subjects)
            val names = buildNameMatcher(subjects)

            val todayPresentPerSubject = mutableMapOf<String, Int>()
            val todayTotalPerSubject = mutableMapOf<String, Int>()
            val totalPerSubject = mutableMapOf<String, Int>()
            val absentPerSubject = mutableMapOf<String, Int>()

            if (todayAttendance != TodayAttendance.NO_CLASSES && today !in noClassDates) {
                for (slot in dateTimetable[today].orEmpty()) {
                    val ownerKey = resolveSlotOwner(slot, subjectKeys, names) ?: continue
                    todayTotalPerSubject[ownerKey] = (todayTotalPerSubject[ownerKey] ?: 0) + 1
                    if (todayAttendance == TodayAttendance.ATTENDED) {
                        todayPresentPerSubject[ownerKey] = (todayPresentPerSubject[ownerKey] ?: 0) + 1
                    }
                }
            }

            for ((date, slots) in dateTimetable) {
                val outsideProjection = projectionEnd == null || date > projectionEnd
                if (date <= today || outsideProjection || date in noClassDates) continue
                val isAbsence = date in selectedDates

                for (slot in slots) {
                    val ownerKey = resolveSlotOwner(slot, subjectKeys, names) ?: continue
                    totalPerSubject[ownerKey] = (totalPerSubject[ownerKey] ?: 0) + 1
                    if (isAbsence) absentPerSubject[ownerKey] = (absentPerSubject[ownerKey] ?: 0) + 1
                }
            }

            val semesterSlots =
                if (semesterEnd != null && semesterEnd >= today && weeklyTimetable.isNotEmpty()) {
                    countSemesterSlots(today.plusDays(1), semesterEnd, weeklyTimetable, subjectKeys, names, noClassDates)
                } else {
                    emptyMap()
                }

            return subjects
                .mapNotNull { s ->
                    val ownerKey = "${s.code.uppercase()}_${s.lecType.uppercase()}"
                    val absences = absentPerSubject[ownerKey] ?: 0
                    val rangeTotal = totalPerSubject[ownerKey] ?: 0
                    val todayPresent = todayPresentPerSubject[ownerKey] ?: 0
                    val todayTotal = todayTotalPerSubject[ownerKey] ?: 0
                    val hasMaximum = semesterEnd != null && semesterEnd >= today
                    val includeUnchanged = includeNoAbsence && (rangeTotal > 0 || hasMaximum)
                    if (absences == 0 && todayTotal == 0 && !includeUnchanged) {
                        return@mapNotNull null
                    }
                    val knownPresent = s.present + todayPresent
                    val knownTotal = s.total + todayTotal
                    val sharedTotal = knownTotal + rangeTotal
                    val baselinePresent = knownPresent + rangeTotal
                    val baselinePercent = if (sharedTotal > 0) baselinePresent * 100.0 / sharedTotal else 0.0
                    val projectedPresent = baselinePresent - absences
                    val projPercent = if (sharedTotal > 0) projectedPresent * 100.0 / sharedTotal else 0.0
                    val projTone = attendanceUseCase.tone(projPercent, threshold)
                    val semClasses = (semesterSlots[ownerKey] ?: 0L).toInt()
                    val maxPresent = if (hasMaximum) knownPresent + semClasses else null
                    val maxTotal = if (hasMaximum) knownTotal + semClasses else null
                    val maxReachable =
                        if (maxPresent != null && maxTotal != null && maxTotal > 0) {
                            maxPresent * 100.0 / maxTotal
                        } else {
                            null
                        }
                    ProjectedSubject(
                        code = s.code,
                        name = s.name,
                        lecType = s.lecType,
                        currentPresent = s.present,
                        currentTotal = s.total,
                        projectedPresent = projectedPresent,
                        projectedTotal = sharedTotal,
                        currentPercent = s.percent,
                        baselinePercent = baselinePercent,
                        projectedPercent = projPercent,
                        absencesPlanned = absences,
                        delta = projPercent - baselinePercent,
                        baselineTone = attendanceUseCase.tone(baselinePercent, threshold),
                        projectedTone = projTone,
                        maxReachable = maxReachable,
                        maxPresent = maxPresent,
                        maxTotal = maxTotal,
                    )
                }
                .sortedBy { it.delta }
        }

        private fun countWeeklySlots(
            subject: SubjectAttendance,
            timetable: Map<String, List<TimetableSlot>>,
        ): Int {
            val code = subject.subCode.uppercase().trim()
            val name = subject.subName.uppercase().trim()
            val isCombined = subject.lecType.equals("PP+PR", ignoreCase = true)
            return timetable.values.sumOf { slots ->
                slots.count { slot ->
                    if (!slotMatchesCode(slot, code) && !slotMatchesName(slot, name)) return@count false
                    if (isCombined) {
                        val lt = slot.lectType.uppercase().trim()
                        lt == "PP" || lt == "PR"
                    } else {
                        slot.lectType.equals(subject.lecType, ignoreCase = true)
                    }
                }
            }
        }

        private fun slotMatchesName(
            slot: TimetableSlot,
            name: String,
        ): Boolean {
            if (name.isEmpty()) return false
            return listOfNotNull(slot.subname, slot.subjectName, slot.subject_full)
                .any { it.uppercase().trim() == name }
        }

        private fun countSemesterSlots(
            start: LocalDate,
            end: LocalDate,
            weeklyTimetable: Map<String, List<TimetableSlot>>,
            subjectKeys: Map<String, String>,
            nameKeys: Map<String, String> = emptyMap(),
            noClassDates: Set<LocalDate> = emptySet(),
        ): Map<String, Long> {
            val counts = mutableMapOf<String, Long>()
            var date = start
            while (date <= end) {
                if (date in noClassDates) {
                    date = date.plusDays(1)
                    continue
                }
                val dayName = DAY_NAMES[date.dayOfWeek] ?: ""
                val slots = weeklyTimetable[dayName] ?: emptyList()
                for (slot in slots) {
                    val ownerKey = resolveSlotOwner(slot, subjectKeys, nameKeys) ?: continue
                    counts[ownerKey] = (counts[ownerKey] ?: 0L) + 1
                }
                date = date.plusDays(1)
            }
            return counts
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

        private fun slotMatchesCode(
            slot: TimetableSlot,
            code: String,
        ): Boolean = slotCodeCandidates(slot).any { it == code }

        private fun buildSlotMatcher(subjects: List<PlannerSubject>): Map<String, String> {
            val map = mutableMapOf<String, String>()
            for (s in subjects) {
                val code = s.code.uppercase()
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

        private fun buildNameMatcher(subjects: List<PlannerSubject>): Map<String, String> {
            val map = mutableMapOf<String, String>()
            for (s in subjects) {
                val name = s.name.uppercase().trim()
                if (name.isEmpty()) continue
                val ownerKey = "${s.code.uppercase()}_${s.lecType.uppercase()}"
                if (s.lecType.equals("PP+PR", ignoreCase = true)) {
                    map["${name}_PP"] = ownerKey
                    map["${name}_PR"] = ownerKey
                } else {
                    map["${name}_${s.lecType.uppercase()}"] = ownerKey
                }
            }
            return map
        }

        private companion object {
            val DAY_NAMES =
                mapOf(
                    DayOfWeek.MONDAY to "Mon",
                    DayOfWeek.TUESDAY to "Tue",
                    DayOfWeek.WEDNESDAY to "Wed",
                    DayOfWeek.THURSDAY to "Thu",
                    DayOfWeek.FRIDAY to "Fri",
                    DayOfWeek.SATURDAY to "Sat",
                    DayOfWeek.SUNDAY to "Sun",
                )
        }
    }
