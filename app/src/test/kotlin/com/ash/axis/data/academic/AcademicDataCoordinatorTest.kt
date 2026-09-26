package com.ash.axis.data.academic

import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.data.repository.AuthRepository
import com.ash.axis.data.repository.TimetableData
import com.ash.axis.data.repository.TimetableKey
import com.ash.axis.data.repository.TimetableRepository
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.DaywiseResponse
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.axis.domain.model.UserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
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
    fun `home returns to current week after visiting another timetable week`() =
        runTest {
            val attendance = mockk<AttendanceRepository>(relaxed = true)
            val timetable = mockk<TimetableRepository>(relaxed = true)
            val auth = mockk<AuthRepository>()
            val context = StudentRequestContext("21001", 11, "clientMixedCase", "2026-2027")
            val current = LocalDate.parse("2026-09-07")
            val future = current.plusWeeks(8)
            coEvery { auth.requireStudentRequestContext(true) } returns context
            coEvery { timetable.requestWeek(any(), any()) } returns MutableStateFlow(AcademicSnapshot<TimetableData>())
            val coordinator = AcademicDataCoordinator(attendance, timetable, auth)

            coordinator.activate(context, null, current)
            coordinator.timetableVisible(future)
            coordinator.homeVisible(current)
            coordinator.refreshTimetable()

            coVerify(atLeast = 2) {
                timetable.requestWeek(TimetableKey(context, current.toString(), current.plusDays(6).toString()), any())
            }
            coordinator.deactivate()
        }

    @Test
    fun `selected semester changes atomically with account`() =
        runTest {
            val attendance = mockk<AttendanceRepository>(relaxed = true)
            val timetable = mockk<TimetableRepository>(relaxed = true)
            val auth = mockk<AuthRepository>()
            val first = StudentRequestContext("21001", 11, "clientMixedCase", "2026-2027")
            val second = first.copy(admno = "21002")
            val option = SemesterOption("2025-2026", "C1", "Semester 1")
            val coordinator = AcademicDataCoordinator(attendance, timetable, auth)

            coordinator.activate(first, option, LocalDate.parse("2026-09-07"))
            assertEquals(first, coordinator.selectedSemester.value.context)
            assertEquals(option, coordinator.selectedSemester.value.option)
            coordinator.activate(second, null, LocalDate.parse("2026-09-07"))
            assertEquals(second, coordinator.selectedSemester.value.context)
            assertNull(coordinator.selectedSemester.value.option)
            coordinator.deactivate()
        }

    @Test
    fun `confirmed QR invalidates only the originating active account`() =
        runTest {
            val attendance = mockk<AttendanceRepository>(relaxed = true)
            val timetable = mockk<TimetableRepository>(relaxed = true)
            val coordinator = AcademicDataCoordinator(attendance, timetable, mockk<AuthRepository>())
            val first = StudentRequestContext("A", 1, "Client", "2026")
            val second = first.copy(admno = "B")
            val option = SemesterOption("Y1", "C1", "")
            val monday = LocalDate.parse("2026-09-07")

            coordinator.activate(first, option, monday)
            coordinator.activate(second, option, monday)
            coordinator.qrSucceeded(first)
            coVerify(exactly = 0) { attendance.invalidateSummary(any()) }
            coVerify(exactly = 0) { attendance.invalidateDaywiseForAccount(any()) }

            coordinator.qrSucceeded(second)
            coVerify(exactly = 1) { attendance.invalidateSummary(com.ash.axis.data.repository.AttendanceKey(second, "C1", "Y1")) }
            coVerify(exactly = 1) { attendance.invalidateDaywiseForAccount(second) }
            coordinator.deactivate()
        }

    @Test
    fun `late semester discovery cannot replace a newer discovery`() =
        runTest {
            val attendance = mockk<AttendanceRepository>(relaxed = true)
            val timetable = mockk<TimetableRepository>(relaxed = true)
            val coordinator = AcademicDataCoordinator(attendance, timetable, mockk<AuthRepository>())
            val context = StudentRequestContext("A", 1, "Client", "2026")
            val pending = CompletableDeferred<SemesterOption>()
            val started = CompletableDeferred<Unit>()
            coEvery { attendance.getLatestSemester(any(), any(), any()) } coAnswers {
                started.complete(Unit)
                pending.await()
            }
            coordinator.activate(context, null, LocalDate.parse("2026-09-07"))
            val discovery = async { coordinator.discoverSemester() }
            started.await()
            val chosen = SemesterOption("Y2", "C2", "Chosen")
            coEvery { attendance.getLatestSemester(any(), any(), any()) } returns chosen
            coordinator.discoverSemester()
            pending.complete(SemesterOption("Y1", "C1", "Old"))
            discovery.await()

            assertEquals(chosen, coordinator.selectedSemester.value.option)
            coordinator.deactivate()
        }

    @Test
    fun `QR success before session activation invalidates saved selection on activation`() =
        runTest {
            val attendance = mockk<AttendanceRepository>(relaxed = true)
            val timetable = mockk<TimetableRepository>(relaxed = true)
            val auth = mockk<AuthRepository>()
            val context = StudentRequestContext("A", 1, "Client", "2026")
            every { auth.getUserInfo() } returns UserInfo("A", 1, "Alex", "", "", "Client")
            val coordinator = AcademicDataCoordinator(attendance, timetable, auth)

            coordinator.qrSucceeded(context)
            coordinator.activate(context, SemesterOption("Y1", "C1", ""), LocalDate.parse("2026-09-07"))

            coVerify(exactly = 1) {
                attendance.invalidateSummary(com.ash.axis.data.repository.AttendanceKey(context, "C1", "Y1"))
            }
            coVerify(exactly = 1) { attendance.invalidateDaywiseForAccount(context) }
            coordinator.deactivate()
        }

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
