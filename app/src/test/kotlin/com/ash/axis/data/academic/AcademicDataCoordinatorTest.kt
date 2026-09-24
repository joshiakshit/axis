package com.ash.axis.data.academic

import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.TimetableData
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.DaywiseResponse
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.StudentRequestContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AcademicDataCoordinatorTest {
    @Test
    fun `resolved profile year is published without fabricating an attendance year`() =
        runTest {
            val attendance = mockk<AttendanceRepository>()
            val timetable = mockk<TimetableRepository>()
            val auth = mockk<AuthRepository>()
            val initial = StudentRequestContext("21001", 11, "clientMixedCase", "")
            val resolved = initial.copy(academicYear = "2026-2027")
            coEvery { attendance.deactivateAcademicData() } returns Unit
            coEvery { timetable.deactivateAcademicData() } returns Unit
            coEvery { auth.requireStudentRequestContext(false) } returns resolved
            coEvery { timetable.requestWeek(any(), any()) } returns
                MutableStateFlow<AcademicSnapshot<TimetableData>>(AcademicSnapshot())
            val coordinator = AcademicDataCoordinator(attendance, timetable, auth)

            coordinator.activate(initial, null, LocalDate.parse("2026-09-07"))

            assertEquals(resolved, coordinator.activeContext.value)
            coVerify(exactly = 0) { attendance.requestSummary(any(), any()) }
            coordinator.deactivate()
            assertNull(coordinator.activeContext.value)
        }

    @Test
    fun `daywise activation starts its visible range before core warmup`() =
        runTest {
            val attendance = mockk<AttendanceRepository>(relaxed = true)
            val timetable = mockk<TimetableRepository>(relaxed = true)
            val auth = mockk<AuthRepository>()
            coEvery { attendance.requestDaywise(any(), any()) } returns
                MutableStateFlow<AcademicSnapshot<DaywiseResponse>>(AcademicSnapshot())
            coEvery { attendance.requestSummary(any(), any()) } returns
                MutableStateFlow<AcademicSnapshot<AttendanceResponse>>(AcademicSnapshot())
            coEvery { timetable.requestWeek(any(), any()) } returns
                MutableStateFlow<AcademicSnapshot<TimetableData>>(AcademicSnapshot())
            val coordinator = AcademicDataCoordinator(attendance, timetable, auth)
            val context = StudentRequestContext("21001", 11, "clientMixedCase", "2026-2027")

            coordinator.activate(
                context,
                SemesterOption("2025-2026", "C1", "Semester 1"),
                LocalDate.parse("2026-09-07"),
                AcademicDestination.DAYWISE,
                DaywiseRange("2025-2026", "2026-09-01", "2026-09-07"),
            )

            coVerifyOrder {
                attendance.requestDaywise(any(), false)
                attendance.requestSummary(any(), false)
            }
            coVerify(exactly = 1) { timetable.requestWeek(any(), false) }
            coordinator.deactivate()
        }

    @Test
    fun `blocked profile discovery does not delay attendance demand`() =
        runTest {
            val attendance = mockk<AttendanceRepository>(relaxed = true)
            val timetable = mockk<TimetableRepository>(relaxed = true)
            val auth = mockk<AuthRepository>()
            val pending = CompletableDeferred<StudentRequestContext>()
            val initial = StudentRequestContext("21001", 11, "clientMixedCase", "")
            coEvery { auth.requireStudentRequestContext(false) } coAnswers { pending.await() }
            coEvery { attendance.requestSummary(any(), any()) } returns
                MutableStateFlow<AcademicSnapshot<AttendanceResponse>>(AcademicSnapshot())
            coEvery { timetable.requestWeek(any(), any()) } returns
                MutableStateFlow<AcademicSnapshot<TimetableData>>(AcademicSnapshot())
            val coordinator = AcademicDataCoordinator(attendance, timetable, auth)

            val activation =
                async {
                    coordinator.activate(
                        initial,
                        SemesterOption("2025-2026", "C1", "Semester 1"),
                        LocalDate.parse("2026-09-07"),
                        AcademicDestination.TIMETABLE,
                    )
                }
            runCurrent()
            coVerify(exactly = 1) { attendance.requestSummary(any(), false) }
            pending.complete(initial.copy(academicYear = "2026-2027"))
            activation.await()
            coordinator.deactivate()
        }

    @Test
    fun `failed profile discovery is available as timetable demand error`() =
        runTest {
            val attendance = mockk<AttendanceRepository>(relaxed = true)
            val timetable = mockk<TimetableRepository>(relaxed = true)
            val auth = mockk<AuthRepository>()
            coEvery { attendance.requestSummary(any(), any()) } returns
                MutableStateFlow<AcademicSnapshot<AttendanceResponse>>(AcademicSnapshot())
            coEvery { auth.requireStudentRequestContext(false) } throws IllegalStateException("profile offline")
            val coordinator = AcademicDataCoordinator(attendance, timetable, auth)

            coordinator.activate(
                StudentRequestContext("21001", 11, "clientMixedCase", ""),
                SemesterOption("2025-2026", "C1", "Semester 1"),
                LocalDate.parse("2026-09-07"),
                AcademicDestination.TIMETABLE,
            )

            assertEquals("profile offline", coordinator.timetableDemandError.value?.message)
            coVerify(exactly = 1) { attendance.requestSummary(any(), false) }
            coordinator.deactivate()
        }

    @Test
    fun `cancelled profile discovery propagates without an offline error`() =
        runTest {
            val attendance = mockk<AttendanceRepository>(relaxed = true)
            val timetable = mockk<TimetableRepository>(relaxed = true)
            val auth = mockk<AuthRepository>()
            coEvery { attendance.requestSummary(any(), any()) } returns
                MutableStateFlow<AcademicSnapshot<AttendanceResponse>>(AcademicSnapshot())
            coEvery { auth.requireStudentRequestContext(false) } throws CancellationException("cancelled")
            val coordinator = AcademicDataCoordinator(attendance, timetable, auth)

            val failure =
                runCatching {
                    coordinator.activate(
                        StudentRequestContext("21001", 11, "clientMixedCase", ""),
                        SemesterOption("2025-2026", "C1", "Semester 1"),
                        LocalDate.parse("2026-09-07"),
                        AcademicDestination.TIMETABLE,
                    )
                }.exceptionOrNull()

            assertTrue(failure is CancellationException)
            assertNull(coordinator.timetableDemandError.value)
            coordinator.deactivate()
        }

    @Test
    fun `cache clearing rejects a prior forced profile response`() =
        runTest {
            val attendance = mockk<AttendanceRepository>(relaxed = true)
            val timetable = mockk<TimetableRepository>(relaxed = true)
            val auth = mockk<AuthRepository>()
            val pending = CompletableDeferred<StudentRequestContext>()
            val context = StudentRequestContext("21001", 11, "clientMixedCase", "2026-2027")
            coEvery { auth.requireStudentRequestContext(true) } coAnswers { pending.await() }
            coEvery { timetable.requestWeek(any(), any()) } returns
                MutableStateFlow<AcademicSnapshot<TimetableData>>(AcademicSnapshot())
            val coordinator = AcademicDataCoordinator(attendance, timetable, auth)

            coordinator.activate(context, null, LocalDate.parse("2026-09-07"))
            val oldRefresh = async { coordinator.refreshTimetable() }
            runCurrent()
            coordinator.clearAcademicCache()
            pending.complete(context.copy(academicYear = "old-profile-year"))
            oldRefresh.await()

            assertEquals(context, coordinator.activeContext.value)
            coVerify(exactly = 0) { timetable.requestWeek(any(), true) }
            coordinator.deactivate()
        }
}
