package com.ash.axis.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.repository.AttendanceKey
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.TimetableKey
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.axis.domain.model.TimetableSlot
import com.ash.axis.domain.usecase.TimetableUseCase
import com.ash.core.storage.PreferencesStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

object ExportKeys {
    // Export the week currently shown in the Timetable tab.
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
        private data class Attendance(val semester: SemesterOption, val data: AttendanceResponse)

        private data class ViewWeek(
            val week: Map<LocalDate, List<TimetableSlot>>,
            val start: LocalDate,
            val end: LocalDate,
        )

        private suspend fun loadAttendance(): Attendance {
            val user = authRepository.getUserInfo() ?: error("Not logged in")
            val semester = attendanceRepo.getLatestSemester(user.admno, user.brId)
            val context = StudentRequestContext(user.admno, user.brId, user.clientId, user.academicYear)
            val data = attendanceRepo.requestSummary(AttendanceKey(context, semester.classId, semester.yearId)).awaitData(context)
            return Attendance(semester, data)
        }

        private suspend fun loadViewWeek(): ViewWeek {
            val context = authRepository.requireStudentRequestContext()
            val viewDate =
                parseDate(preferencesStore.getUserString(ExportKeys.TIMETABLE_VIEW_DATE, "").first()) ?: LocalDate.now()
            val weekStart = viewDate.with(DayOfWeek.MONDAY)
            val weekEnd = weekStart.plusDays(6)
            val data = timetableRepo.requestWeek(TimetableKey(context, weekStart.toString(), weekEnd.toString())).awaitData(context)
            val week =
                data.dated?.mapKeys { LocalDate.parse(it.key) } ?: (0..6).associate { offset ->
                    val date = weekStart.plusDays(offset.toLong())
                    date to data.weekly[date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)].orEmpty()
                }
            return ViewWeek(week, weekStart, weekEnd)
        }

        private suspend fun <T> StateFlow<AcademicSnapshot<T>>.awaitData(context: StudentRequestContext): T {
            val snapshot = first { !it.refreshing }
            val user = authRepository.getUserInfo()
            check(user != null && user.admno == context.admno && user.brId == context.brId && user.clientId == context.clientId) {
                "Export session changed"
            }
            return snapshot.data ?: throw (snapshot.error ?: CancellationException("Academic data was reset"))
        }

        private fun weekLabel(
            start: LocalDate,
            end: LocalDate,
        ): String = "${start.format(rangeFormat)} – ${end.format(rangeFormat)}"

        suspend fun exportAttendancePng(): ExportFile {
            val (semester, data) = loadAttendance()
            val rows =
                data.table.values.sortedByDescending { it.total }.map { entry ->
                    ImageRow(
                        entry.subname,
                        "${entry.subCode} · ${entry.lecType} · ${entry.present}/${entry.total} attended · ${formatPercent(entry.percent)}%",
                    )
                } + ImageRow("Overall", "${data.endrow.present}/${data.endrow.total} attended · ${formatPercent(data.endrow.percentage)}%")
            val file = File(exportsDir(), "axis-attendance-${LocalDate.now()}.png")
            PngDocuments.write(file, "Attendance", "${semester.label} · ${LocalDate.now()}", rows)
            return ExportFile(file, "image/png", "Attendance - ${semester.label}")
        }

        suspend fun exportTimetablePng(): ExportFile {
            val (week, start, end) = loadViewWeek()
            val rows =
                (0..6).flatMap { day ->
                    val date = start.plusDays(day.toLong())
                    val slots = timetableUseCase.sortSlotsByTime(week[date].orEmpty())
                    if (slots.isEmpty()) {
                        emptyList()
                    } else {
                        listOf(ImageRow(date.format(dayHeadingFormat), slots.joinToString("\n", transform = ::slotLine)))
                    }
                }.ifEmpty { listOf(ImageRow("No classes scheduled this week.", "")) }
            val file = File(exportsDir(), "axis-timetable-$start.png")
            PngDocuments.write(file, "Timetable", weekLabel(start, end), rows)
            return ExportFile(file, "image/png", "Timetable - ${weekLabel(start, end)}")
        }

        private fun slotLine(slot: TimetableSlot): String {
            val name = timetableUseCase.displaySubjectName(slot).ifBlank { slot.subCode }
            val type = lectureLabel(slot.lectType)
            val label = if (type.isNotBlank()) "$name ($type)" else name
            val room = if (slot.roomno.isNotBlank()) " · Room ${slot.roomno}" else ""
            return "${slot.fromTime}–${slot.toTime}   $label$room"
        }

        // Return false before Android 10 so the caller can use the share sheet.
        @Suppress("TooGenericExceptionCaught")
        fun saveToDownloads(export: ExportFile): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
            val resolver = appContext.contentResolver
            val values =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, export.file.name)
                    put(MediaStore.Downloads.MIME_TYPE, export.mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            try {
                val output = resolver.openOutputStream(uri) ?: error("Cannot save image to Downloads")
                output.use { out -> export.file.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return true
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                throw error
            }
        }

        private fun exportsDir(): File = File(appContext.cacheDir, "exports").apply { mkdirs() }

        private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw) }.getOrNull()

        private fun formatPercent(value: Double): String = String.format(Locale.ENGLISH, "%.2f", value)

        private fun lectureLabel(lectType: String): String =
            when {
                lectType.equals("PP+PR", ignoreCase = true) -> "LEC+LAB"
                lectType.equals("PR", ignoreCase = true) -> "LAB"
                lectType.isBlank() -> ""
                else -> "LEC"
            }

        private companion object {
            val rangeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)
            val dayHeadingFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.ENGLISH)
        }
    }
