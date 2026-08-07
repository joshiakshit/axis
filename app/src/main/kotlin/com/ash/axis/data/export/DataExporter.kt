package com.ash.axis.data.export

import android.content.Context
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.SELECTED_SEMESTER_CLASS_KEY
import com.ash.axis.data.repository.SELECTED_SEMESTER_YEAR_KEY
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.TimetableSlot
import com.ash.axis.domain.usecase.TimetableUseCase
import com.ash.core.storage.PreferencesStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

object ExportKeys {
    // Key the Timetable tab writes its current view date to, so "export timetable" mirrors the viewed week.
    const val TIMETABLE_VIEW_DATE = "timetable_view_date"
}

data class ExportFile(
    val file: File,
    val mimeType: String,
    val subject: String,
)

@Singleton
class DataExporter
    @Inject
    constructor(
        private val attendanceRepo: AttendanceRepository,
        private val timetableRepo: TimetableRepository,
        private val authRepository: AuthRepository,
        private val timetableUseCase: TimetableUseCase,
        private val preferencesStore: PreferencesStore,
        @ApplicationContext private val appContext: Context,
    ) {
        private val dayOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        suspend fun exportAttendanceCsv(): ExportFile {
            val user = authRepository.getUserInfo() ?: error("Not logged in")
            val yearId = preferencesStore.getUserString(SELECTED_SEMESTER_YEAR_KEY).first()
            val classId = preferencesStore.getUserString(SELECTED_SEMESTER_CLASS_KEY).first()
            val semester = attendanceRepo.getPreferredSemester(user.admno, user.brId, yearId, classId, false)
            val attendance = attendanceRepo.getAttendance(user.admno, user.brId, semester.classId, semester.yearId, false)
            val csv = buildAttendanceCsv(attendance)
            val file = writeFile("axis-attendance-${LocalDate.now()}.csv", csv)
            return ExportFile(file, "text/csv", "Attendance — ${semester.label}")
        }

        suspend fun exportTimetableIcs(): ExportFile {
            val user = authRepository.getUserInfo() ?: error("Not logged in")
            val viewDate =
                parseDate(preferencesStore.getUserString(ExportKeys.TIMETABLE_VIEW_DATE, "").first()) ?: LocalDate.now()
            val weekStart = viewDate.with(DayOfWeek.MONDAY)
            val weekEnd = weekStart.plusDays(6)

            val yearId = preferencesStore.getUserString(SELECTED_SEMESTER_YEAR_KEY).first()
            val classId = preferencesStore.getUserString(SELECTED_SEMESTER_CLASS_KEY).first()
            val year =
                runCatching { attendanceRepo.getPreferredSemester(user.admno, user.brId, yearId, classId, false).yearId }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: timetableUseCase.getAcadYear()

            val week =
                timetableRepo.getDateKeyedTimetable(
                    admno = user.admno,
                    brId = user.brId,
                    acadYear = year,
                    startDate = weekStart.toString(),
                    endDate = weekEnd.toString(),
                    forceRefresh = false,
                )
            val ics = buildTimetableIcs(week, weekStart, weekEnd)
            val file = writeFile("axis-timetable-$weekStart.ics", ics)
            val range = "${weekStart.format(rangeFormat)} – ${weekEnd.format(rangeFormat)}"
            return ExportFile(file, "text/calendar", "Timetable — $range")
        }

        private fun buildAttendanceCsv(attendance: AttendanceResponse): String =
            buildString {
                append("Subject,Code,Type,Present,Total,Percentage\r\n")
                attendance.table.values
                    .sortedByDescending { it.total }
                    .forEach { e ->
                        append(csvCell(e.subname))
                        append(',').append(csvCell(e.subCode))
                        append(',').append(csvCell(e.lecType))
                        append(',').append(e.present)
                        append(',').append(e.total)
                        append(',').append(formatPercent(e.percent))
                        append("\r\n")
                    }
                val end = attendance.endrow
                append(csvCell("Overall")).append(",,,")
                append(end.present).append(',').append(end.total).append(',')
                append(formatPercent(end.percentage)).append("\r\n")
            }

        private fun buildTimetableIcs(
            week: Map<LocalDate, List<TimetableSlot>>,
            weekStart: LocalDate,
            weekEnd: LocalDate,
        ): String {
            val stamp = ZonedDateTime.now(ZoneId.of("UTC")).format(stampFormat)
            val builder = StringBuilder()
            builder.append("BEGIN:VCALENDAR\r\n")
            builder.append("VERSION:2.0\r\n")
            builder.append("PRODID:-//Axis//Timetable//EN\r\n")
            builder.append("CALSCALE:GREGORIAN\r\n")
            builder.append("METHOD:PUBLISH\r\n")
            foldInto(builder, "X-WR-CALNAME:Axis Timetable ${weekStart.format(rangeFormat)}-${weekEnd.format(rangeFormat)}")
            for (i in 0..6) {
                val date = weekStart.plusDays(i.toLong())
                val dayName = dayOrder[i]
                timetableUseCase.sortSlotsByTime(week[date] ?: emptyList()).forEach { slot ->
                    appendEvent(builder, date, dayName, slot, stamp)
                }
            }
            builder.append("END:VCALENDAR\r\n")
            return builder.toString()
        }

        private fun appendEvent(
            builder: StringBuilder,
            date: LocalDate,
            dayName: String,
            slot: TimetableSlot,
            stamp: String,
        ) {
            val start = timeStamp(date, slot.fromTime) ?: return
            val end = timeStamp(date, slot.toTime) ?: return
            val name = timetableUseCase.displaySubjectName(slot).ifBlank { slot.subCode }
            val type = lectureLabel(slot.lectType)
            val summary = if (type.isNotBlank()) "$name ($type)" else name

            builder.append("BEGIN:VEVENT\r\n")
            builder.append("UID:").append(date).append('-').append(digits(slot.fromTime))
            builder.append('-').append(digits(slot.subjectId.ifBlank { slot.subCode })).append("@axis\r\n")
            builder.append("DTSTAMP:").append(stamp).append("\r\n")
            builder.append("DTSTART:").append(start).append("\r\n")
            builder.append("DTEND:").append(end).append("\r\n")
            foldInto(builder, "SUMMARY:${icsText(summary)}")
            if (slot.roomno.isNotBlank()) foldInto(builder, "LOCATION:${icsText(slot.roomno)}")
            val descBits = listOfNotNull(slot.subCode.ifBlank { null }, dayName).joinToString(" · ")
            if (descBits.isNotBlank()) foldInto(builder, "DESCRIPTION:${icsText(descBits)}")
            builder.append("END:VEVENT\r\n")
        }

        private fun writeFile(
            name: String,
            content: String,
        ): File {
            val dir = File(appContext.cacheDir, "exports").apply { mkdirs() }
            return File(dir, name).apply { writeText(content) }
        }

        // --- formatting helpers --------------------------------------------------------------

        private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw) }.getOrNull()

        private fun formatPercent(value: Double): String = String.format(java.util.Locale.ENGLISH, "%.2f", value)

        private fun csvCell(value: String): String =
            if (value.any { it in CSV_SPECIALS }) {
                "\"${value.replace("\"", "\"\"")}\""
            } else {
                value
            }

        // Build a floating-local ICS timestamp (YYYYMMDDTHHMMSS) from a date and an "HH:mm" string.
        private fun timeStamp(
            date: LocalDate,
            time: String,
        ): String? {
            val parts = time.split(":")
            val hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
            val minute = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
            return "%04d%02d%02dT%02d%02d00".format(date.year, date.monthValue, date.dayOfMonth, hour, minute)
        }

        private fun digits(value: String): String = value.filter { it.isLetterOrDigit() }.ifBlank { "x" }

        private fun icsText(value: String): String =
            value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n")

        private fun lectureLabel(lectType: String): String =
            when {
                lectType.equals("PP+PR", ignoreCase = true) -> "LEC+LAB"
                lectType.equals("PR", ignoreCase = true) -> "LAB"
                lectType.isBlank() -> ""
                else -> "LEC"
            }

        // Fold a content line to <=75 octets per RFC 5545 (continuation lines start with a space).
        private fun foldInto(
            builder: StringBuilder,
            line: String,
        ) {
            var remaining = line
            var limit = FOLD_LIMIT
            while (remaining.length > limit) {
                builder.append(remaining, 0, limit).append("\r\n ")
                remaining = remaining.substring(limit)
                limit = FOLD_LIMIT - 1 // account for the leading space on continuation lines
            }
            builder.append(remaining).append("\r\n")
        }

        private companion object {
            const val FOLD_LIMIT = 74
            val CSV_SPECIALS = charArrayOf(',', '"', '\n', '\r')
            val rangeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM", java.util.Locale.ENGLISH)
            val stampFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.ENGLISH)
        }
    }
