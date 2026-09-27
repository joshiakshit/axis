package com.ash.axis.data.export

import android.content.Context
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.repository.AttendanceKey
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.TimetableData
import com.ash.axis.data.repository.TimetableKey
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceEntry
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.axis.domain.model.TimetableSlot
import com.ash.axis.domain.model.UserInfo
import com.ash.axis.domain.usecase.TimetableUseCase
import com.ash.core.storage.PreferencesStore
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DataExporterTest {
    @TempDir lateinit var directory: File
    private val attendance = mockk<AttendanceRepository>()
    private val timetable = mockk<TimetableRepository>()
    private val auth = mockk<AuthRepository>()
    private val preferences = mockk<PreferencesStore>()
    private val context = mockk<Context>()
    private val rows = slot<List<ImageRow>>()

    @BeforeEach
    fun setUp() {
        every { context.cacheDir } returns directory
        mockkObject(PngDocuments)
        every { PngDocuments.write(any(), any(), any(), capture(rows)) } just Runs
    }

    @AfterEach
    fun tearDown() = unmockkObject(PngDocuments)

    private fun exporter() = DataExporter(attendance, timetable, auth, TimetableUseCase(), preferences, context)

    @Test
    fun `attendance exports latest semester as png with full subject names`() =
        runTest {
            every { auth.getUserInfo() } returns UserInfo("A", 1, "Alex", "", "", "client", academicYear = "2026")
            coEvery { attendance.getLatestSemester("A", 1, false) } returns SemesterOption("2026", "C6", "Semester VI")
            val name = "A very long subject name that must remain complete in the exported image"
            val key = AttendanceKey(StudentRequestContext("A", 1, "client", "2026"), "C6", "2026")
            coEvery { attendance.requestSummary(key, false) } returns
                MutableStateFlow(
                    AcademicSnapshot(
                        data =
                            AttendanceResponse(
                                table = mapOf("one" to AttendanceEntry(subname = name, present = 3, total = 4, percent = 75.0)),
                            ),
                    ),
                )
            val result = exporter().exportAttendancePng()
            assertEquals("image/png", result.mimeType)
            assertEquals("png", result.file.extension)
            assertEquals(name, rows.captured.first().heading)
            assertTrue(rows.captured.first().detail.contains("3/4 attended"))
            assertEquals("Overall", rows.captured.last().heading)
        }

    @Test
    fun `timetable png uses the viewed week and includes sunday`() =
        runTest {
            val owner = StudentRequestContext("A", 1, "client", academicYear = "2026")
            coEvery { auth.requireStudentRequestContext(any()) } returns owner
            every { auth.getUserInfo() } returns UserInfo("A", 1, "Alex", "", "", "client", academicYear = "2026")
            every { preferences.getUserString(ExportKeys.TIMETABLE_VIEW_DATE, "") } returns flowOf("2026-09-23")
            coEvery { timetable.requestWeek(TimetableKey(owner, "2026-09-21", "2026-09-27"), false) } returns
                MutableStateFlow(
                    AcademicSnapshot(
                        data =
                            TimetableData(
                                emptyMap(),
                                mapOf("2026-09-27" to listOf(TimetableSlot(fromTime = "09:00", toTime = "10:00", subCode = "CS1"))),
                            ),
                    ),
                )
            val result = exporter().exportTimetablePng()
            assertEquals("image/png", result.mimeType)
            assertEquals("axis-timetable-2026-09-21.png", result.file.name)
            assertEquals("Sun 27 Sep", rows.captured.single().heading)
            assertTrue(rows.captured.single().detail.contains("CS1"))
            coVerify(exactly = 1) { timetable.requestWeek(TimetableKey(owner, "2026-09-21", "2026-09-27"), false) }
        }

    @Test
    fun `export waits for shared attendance and rejects a changed session`() =
        runTest {
            val snapshot = MutableStateFlow(AcademicSnapshot<AttendanceResponse>(refreshing = true))
            var user: UserInfo? = UserInfo("A", 1, "Alex", "", "", "client", academicYear = "2026")
            every { auth.getUserInfo() } answers { user }
            coEvery { attendance.getLatestSemester("A", 1, false) } returns SemesterOption("2026", "C6", "Semester VI")
            coEvery { attendance.requestSummary(any(), false) } returns snapshot
            val result = async { runCatching { exporter().exportAttendancePng() } }
            runCurrent()
            assertTrue(!result.isCompleted)
            user = null
            snapshot.value = AcademicSnapshot(data = AttendanceResponse())
            runCurrent()
            assertTrue(result.await().exceptionOrNull() is IllegalStateException)
            io.mockk.verify(exactly = 0) { PngDocuments.write(any(), any(), any(), any()) }
        }

    @Test
    fun `reset shared attendance ends an export without writing an image`() =
        runTest {
            val snapshot = MutableStateFlow(AcademicSnapshot<AttendanceResponse>(refreshing = true))
            every { auth.getUserInfo() } returns UserInfo("A", 1, "Alex", "", "", "client", academicYear = "2026")
            coEvery { attendance.getLatestSemester("A", 1, false) } returns SemesterOption("2026", "C6", "Semester VI")
            coEvery { attendance.requestSummary(any(), false) } returns snapshot
            val result = async { runCatching { exporter().exportAttendancePng() } }
            runCurrent()
            snapshot.value = AcademicSnapshot()
            runCurrent()
            assertTrue(result.await().exceptionOrNull() is CancellationException)
            io.mockk.verify(exactly = 0) { PngDocuments.write(any(), any(), any(), any()) }
        }
}
