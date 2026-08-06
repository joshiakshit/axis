package com.ash.axis.domain.usecase

import com.ash.axis.domain.model.TimetableSlot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class TimetableUseCase
    @Inject
    constructor() {
        fun getCurrentWeekRange(): Pair<LocalDate, LocalDate> {
            val today = LocalDate.now()
            val monday = today.with(DayOfWeek.MONDAY)
            val sunday = monday.plusDays(6)
            return monday to sunday
        }

        fun shiftWeek(
            start: LocalDate,
            weeks: Int,
        ): Pair<LocalDate, LocalDate> {
            val newStart = start.plusWeeks(weeks.toLong())
            return newStart to newStart.plusDays(6)
        }

        fun formatDateRange(
            start: LocalDate,
            end: LocalDate,
        ): String {
            val fmt = DateTimeFormatter.ofPattern("dd MMM")
            return "${start.format(fmt)} - ${end.format(fmt)}"
        }

        fun sortSlotsByTime(slots: List<TimetableSlot>): List<TimetableSlot> {
            return slots.sortedBy { timeToMinutes(it.fromTime) }
        }

        fun timeToMinutes(time: String): Int {
            if (time.isBlank()) return 0
            val parts = time.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return h * 60 + m
        }

        fun currentSlotProgress(
            slot: TimetableSlot,
            isToday: Boolean,
        ): Float? {
            if (!isToday) return null
            val now = LocalTime.now()
            val nowMin = now.hour * 60 + now.minute
            val startMin = timeToMinutes(slot.fromTime)
            val endMin = timeToMinutes(slot.toTime)
            if (nowMin < startMin || nowMin > endMin || endMin <= startMin) return null
            return (nowMin - startMin).toFloat() / (endMin - startMin)
        }

        fun getAcadYear(): String {
            val today = LocalDate.now()
            val year = today.year
            val month = today.monthValue
            return if (month >= 8) "$year-${year + 1}" else "${year - 1}-$year"
        }

        fun cleanSubjectName(
            raw: String,
            roomNo: String? = null,
            vararg codes: String?,
        ): String = cleanSubjectName(raw, roomNo, codes.asList())

        private fun cleanSubjectName(
            raw: String,
            roomNo: String? = null,
            codes: List<String?>,
        ): String {
            var s = raw.replace(Regex("""^.*?Sem\s+\w+\s*[-–]\s*""", RegexOption.IGNORE_CASE), "")
            if (!roomNo.isNullOrBlank()) s = s.replace(roomNo, "")
            s = s.replace(Regex("""\b(PP|PR|TH|LEC|LAB)\b"""), "")
            s = s.replace(Regex("""Section[-\s]*\d+""", RegexOption.IGNORE_CASE), "")
            for (code in codes) {
                if (!code.isNullOrBlank()) s = s.replace(Regex(Regex.escape(code), RegexOption.IGNORE_CASE), "")
            }
            s = s.replace(Regex("""\b[A-Z]\d+[A-Z]+\d*[A-Z]?\b"""), "")
            s = s.replace(Regex("""\(\s*[A-Z0-9_-]*\s*\)"""), "")
            s = s.replace(Regex("""\s*[-–]+\s*"""), " ").replace(Regex("""\s{2,}"""), " ")
            return s.trim()
        }

        fun isSubstitution(slot: TimetableSlot): Boolean {
            val id = slot.altChangeId
            return !id.isNullOrBlank() && id != "0"
        }

        fun displaySubjectName(slot: TimetableSlot): String {
            val codeCandidates = listOf(slot.subjectId, slot.subCode, slot.sub_shortname, slot.sub_short)
            val directName =
                listOfNotNull(slot.subname, slot.subjectName)
                    .firstNotNullOfOrNull { raw ->
                        cleanSubjectName(raw, slot.roomno, codeCandidates).takeIf { it.isNotBlank() }
                    }
            if (!directName.isNullOrBlank()) return directName

            val fallbackName =
                listOfNotNull(slot.subject_full, slot.sub_shortname, slot.sub_short, slot.subjectId)
                    .firstNotNullOfOrNull { raw ->
                        cleanSubjectName(raw, slot.roomno, codeCandidates).takeIf { it.isNotBlank() && !it.looksLikeFacultyBlob() }
                    }
            return fallbackName ?: codeCandidates.firstOrNull { !it.isNullOrBlank() }.orEmpty()
        }

        fun originalTeacher(slot: TimetableSlot): String? = buildTeacherName(slot.empFirstName, slot.empLastName)

        fun substituteTeacher(slot: TimetableSlot): String? = buildTeacherName(slot.empFirstNameAlt, slot.empLastNameAlt)

        private fun buildTeacherName(
            first: String?,
            last: String?,
        ): String? {
            val full = listOfNotNull(first?.trim(), last?.trim()).filter { it.isNotEmpty() }.joinToString(" ")
            return full.ifBlank { null }
        }

        private fun String.looksLikeFacultyBlob(): Boolean {
            val value = lowercase()
            return value.contains("faculty") ||
                value.contains("empfirst") ||
                value.contains("emplast") ||
                value.contains("altchange") ||
                value.contains("alternate")
        }
    }
