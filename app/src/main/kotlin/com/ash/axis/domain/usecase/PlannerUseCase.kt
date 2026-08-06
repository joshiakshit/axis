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

data class TomorrowClass(
    val subjectName: String,
    val subCode: String,
    val lecType: String,
    val time: String,
    val skipsLeft: Int,
    val canSkip: Boolean,
    val percentAfterSkip: Double,
    val toneAfterSkip: AttendanceTone,
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
)

class PlannerUseCase
    @Inject
    constructor(
        private val attendanceUseCase: AttendanceUseCase,
    ) {
        fun buildTomorrowClasses(
            slots: List<TimetableSlot>,
            subjects: List<PlannerSubject>,
            threshold: Int,
        ): List<TomorrowClass> {
            val slotMatcher = buildSlotMatcher(subjects)
            val nameMatcher = buildNameMatcher(subjects)
            val subjectIndex = subjects.associateBy { "${it.code.uppercase()}_${it.lecType.uppercase()}" }
            return slots.mapNotNull { slot ->
                val ownerKey = resolveSlotOwner(slot, slotMatcher, nameMatcher) ?: return@mapNotNull null
                val subject = subjectIndex[ownerKey] ?: return@mapNotNull null
                val newTotal = subject.total + 1
                val pctAfterSkip = if (newTotal > 0) subject.present * 100.0 / newTotal else 0.0
                TomorrowClass(
                    subjectName = subject.name,
                    subCode = subject.code,
                    lecType = subject.lecType,
                    time = "${slot.fromTime} – ${slot.toTime}",
                    skipsLeft = subject.bunkable,
                    canSkip = subject.bunkable > 0,
                    percentAfterSkip = pctAfterSkip,
                    toneAfterSkip = attendanceUseCase.tone(pctAfterSkip, threshold),
                )
            }
        }

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
        ): List<ProjectedSubject> {
            if (selectedDates.isEmpty() && !includeNoAbsence) return emptyList()

            val futureDates = selectedDates.filter { it >= today }.sorted()
            if (futureDates.isEmpty() && !includeNoAbsence) return emptyList()

            val subjectKeys = buildSlotMatcher(subjects)
            val names = buildNameMatcher(subjects)

            val totalPerSubject = mutableMapOf<String, Int>()
            val absentPerSubject = mutableMapOf<String, Int>()

            for ((date, slots) in dateTimetable) {
                if (date < today) continue
                val isAbsence = date in selectedDates

                for (slot in slots) {
                    val ownerKey = resolveSlotOwner(slot, subjectKeys, names) ?: continue
                    totalPerSubject[ownerKey] = (totalPerSubject[ownerKey] ?: 0) + 1
                    if (isAbsence) absentPerSubject[ownerKey] = (absentPerSubject[ownerKey] ?: 0) + 1
                }
            }

            val semesterSlots =
                if (semesterEnd != null && semesterEnd >= today && weeklyTimetable.isNotEmpty()) {
                    countSemesterSlots(today, semesterEnd, weeklyTimetable, subjectKeys, names)
                } else {
                    emptyMap()
                }

            return subjects
                .mapNotNull { s ->
                    val ownerKey = "${s.code.uppercase()}_${s.lecType.uppercase()}"
                    val absences = absentPerSubject[ownerKey] ?: 0
                    val rangeTotal = totalPerSubject[ownerKey] ?: 0
                    if (absences == 0 && !(includeNoAbsence && rangeTotal > 0)) return@mapNotNull null
                    val sharedTotal = s.total + rangeTotal
                    val baselinePercent = if (sharedTotal > 0) (s.present + rangeTotal) * 100.0 / sharedTotal else 0.0
                    val projPercent = if (sharedTotal > 0) (s.present + rangeTotal - absences) * 100.0 / sharedTotal else 0.0
                    val projTone = attendanceUseCase.tone(projPercent, threshold)
                    val semClasses = semesterSlots[ownerKey] ?: 0L
                    val maxReachable =
                        if (semClasses > 0) {
                            val semTotal = s.total + semClasses
                            if (semTotal > 0) (s.present + semClasses - absences) * 100.0 / semTotal else null
                        } else {
                            null
                        }
                    ProjectedSubject(
                        code = s.code,
                        name = s.name,
                        lecType = s.lecType,
                        currentPresent = s.present,
                        currentTotal = s.total,
                        projectedPresent = s.present + rangeTotal - absences,
                        projectedTotal = sharedTotal,
                        currentPercent = s.percent,
                        baselinePercent = baselinePercent,
                        projectedPercent = projPercent,
                        absencesPlanned = absences,
                        delta = projPercent - baselinePercent,
                        baselineTone = attendanceUseCase.tone(baselinePercent, threshold),
                        projectedTone = projTone,
                        maxReachable = maxReachable,
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
        ): Map<String, Long> {
            val counts = mutableMapOf<String, Long>()
            var date = start
            while (date <= end) {
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
