package com.ash.axis.data.academic

import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.TimetableData
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.core.storage.CacheFreshness
import com.ash.core.storage.CachedResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WaveOneAcceptanceTest {
    @Test
    fun `a previously fresh snapshot revalidates after its cache becomes stale`() =
        runTest {
            var freshness = CacheFreshness.FRESH
            var calls = 0
            val resource =
                AcademicResource<String, String>(
                    backgroundScope,
                    { CachedResult("saved", freshness, 100L) },
                    {
                        calls++
                        "updated"
                    },
                    { _, _ -> 200L },
                )

            resource.request("key")
            runCurrent()
            freshness = CacheFreshness.STALE
            resource.request("key")
            runCurrent()

            assertEquals(1, calls)
        }

    @Test
    fun `existing observers receive a reload after cache clearing`() =
        runTest {
            val resource =
                AcademicResource<String, String>(
                    backgroundScope,
                    { null },
                    { "updated" },
                    { _, _ -> 200L },
                )
            val observed = resource.observe("key")

            resource.clearCache { }
            resource.request("key")
            runCurrent()

            assertEquals("updated", observed.value.data)
        }

    @Test
    fun `a cache read started before clearing cannot hydrate a replacement entry`() =
        runTest {
            val cached = CompletableDeferred<CachedResult<String>?>()
            val resource =
                AcademicResource<String, String>(
                    backgroundScope,
                    { cached.await() },
                    { "updated" },
                    { _, _ -> 200L },
                )
            val oldRequest = async { resource.request("key") }
            runCurrent()
            resource.clearCache { }
            val replacement = resource.observe("key")

            cached.complete(CachedResult("cleared data", CacheFreshness.FRESH, 100L))
            oldRequest.await()
            runCurrent()

            assertNull(replacement.value.data)
        }

    @Test
    fun `failed timetable profile discovery does not prevent attendance loading`() =
        runTest {
            val attendance = mockk<AttendanceRepository>(relaxed = true)
            val timetable = mockk<TimetableRepository>(relaxed = true)
            val auth = mockk<AuthRepository>()
            coEvery { attendance.requestSummary(any(), any()) } returns
                MutableStateFlow<AcademicSnapshot<AttendanceResponse>>(AcademicSnapshot())
            coEvery { auth.requireStudentRequestContext(false) } throws IllegalStateException("profile offline")
            val coordinator = AcademicDataCoordinator(attendance, timetable, auth)

            try {
                runCatching {
                    coordinator.activate(
                        StudentRequestContext("21001", 11, "clientMixedCase", ""),
                        SemesterOption("2025-2026", "C1", "Semester 1"),
                        LocalDate.parse("2026-09-07"),
                        AcademicDestination.TIMETABLE,
                    )
                }
                coVerify(exactly = 1) { attendance.requestSummary(any(), any()) }
            } finally {
                coordinator.deactivate()
            }
        }

    @Test
    fun `manual profile refresh from a prior activation cannot publish into a new activation`() =
        runTest {
            val attendance = mockk<AttendanceRepository>(relaxed = true)
            val timetable = mockk<TimetableRepository>(relaxed = true)
            val auth = mockk<AuthRepository>()
            val pending = CompletableDeferred<StudentRequestContext>()
            val context = StudentRequestContext("21001", 11, "clientMixedCase", "2026-2027")
            val monday = LocalDate.parse("2026-09-07")
            coEvery { auth.requireStudentRequestContext(true) } coAnswers { pending.await() }
            coEvery { timetable.requestWeek(any(), any()) } returns
                MutableStateFlow<AcademicSnapshot<TimetableData>>(AcademicSnapshot())
            val coordinator = AcademicDataCoordinator(attendance, timetable, auth)

            try {
                coordinator.activate(context, null, monday)
                val oldRefresh = async { coordinator.refreshTimetable() }
                runCurrent()
                coordinator.deactivate()
                coordinator.activate(context, null, monday)

                pending.complete(context.copy(academicYear = "old-profile-year"))
                oldRefresh.await()

                assertEquals(context, coordinator.activeContext.value)
                coVerify(exactly = 0) { timetable.requestWeek(any(), true) }
            } finally {
                coordinator.deactivate()
            }
        }
}
