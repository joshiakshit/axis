package com.ash.axis.data.academic

import com.ash.axis.data.repository.AttendanceKey
import com.ash.axis.data.repository.AttendanceRepository
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.StudentRequestContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceObservationTest {
    @Test
    fun `account and semester changes clear data before the next subscription completes`() =
        runTest {
            val owner = StudentRequestContext("A", 1, "client", "2026")
            val context = MutableStateFlow<StudentRequestContext?>(owner)
            val selection = MutableStateFlow(AcademicSemesterSelection(owner, SemesterOption("2026", "C1", "First")))
            val coordinator = mockk<AcademicDataCoordinator>()
            val repository = mockk<AttendanceRepository>()
            every { coordinator.activeContext } returns context
            every { coordinator.selectedSemester } returns selection
            val saved = AttendanceResponse()
            val first = MutableStateFlow(AcademicSnapshot(data = saved))
            val pending = CompletableDeferred<MutableStateFlow<AcademicSnapshot<AttendanceResponse>>>()
            coEvery { repository.observeSummary(AttendanceKey(owner, "C1", "2026")) } returns first
            coEvery { repository.observeSummary(AttendanceKey(owner, "C2", "2026")) } coAnswers { pending.await() }
            var latest: Pair<AcademicSemesterSelection, AcademicSnapshot<AttendanceResponse>>? = null
            var resets = 0
            backgroundScope.launch {
                coordinator.observeAttendance(repository) { resets++ }.collect { latest = it }
            }
            runCurrent()
            assertEquals(saved, latest!!.second.data)
            assertEquals(1, resets)

            selection.value = selection.value.copy(error = IllegalStateException("metadata failed"))
            runCurrent()
            assertEquals(saved, latest!!.second.data)
            assertEquals("metadata failed", latest!!.first.error?.message)
            assertEquals(1, resets)

            selection.value = AcademicSemesterSelection(owner, SemesterOption("2026", "C2", "Second"))
            runCurrent()
            assertNull(latest!!.second.data)
            assertEquals(2, resets)
            pending.complete(MutableStateFlow(AcademicSnapshot(data = saved)))
            runCurrent()
            assertEquals(saved, latest!!.second.data)

            context.value = owner.copy(admno = "B")
            runCurrent()
            assertNull(latest!!.first.option)
            assertNull(latest!!.second.data)
            assertEquals(3, resets)
            first.value = AcademicSnapshot(data = saved, updatedAtMillis = 123)
            runCurrent()
            assertNull(latest!!.second.data)
            coVerify(exactly = 0) { repository.requestSummary(any(), any()) }
        }
}
