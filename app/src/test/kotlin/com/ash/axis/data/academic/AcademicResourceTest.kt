package com.ash.axis.data.academic

import com.ash.core.storage.CacheFreshness
import com.ash.core.storage.CachedResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AcademicResourceTest {
    @Test
    fun `stale cache publishes before shared blocked refresh`() =
        runTest {
            val response = CompletableDeferred<String>()
            var calls = 0
            val resource =
                AcademicResource<String, String>(
                    backgroundScope,
                    { CachedResult("saved", CacheFreshness.STALE, 100L) },
                    {
                        calls++
                        response.await()
                    },
                    { _, _ -> 200L },
                )

            val first = resource.request("account-year-week")
            resource.request("account-year-week")
            runCurrent()

            assertEquals("saved", first.value.data)
            assertEquals(100L, first.value.updatedAtMillis)
            assertTrue(first.value.refreshing)
            assertEquals(1, calls)

            response.complete("new")
            runCurrent()
            assertEquals("new", first.value.data)
            assertEquals(200L, first.value.updatedAtMillis)
        }

    @Test
    fun `fresh cache avoids network while force refresh starts it`() =
        runTest {
            var calls = 0
            val resource =
                AcademicResource<String, List<String>>(
                    backgroundScope,
                    { CachedResult(emptyList<String>(), CacheFreshness.FRESH, 100L) },
                    {
                        calls++
                        listOf("new")
                    },
                    { _, _ -> 200L },
                )

            val state = resource.request("key")
            runCurrent()
            assertEquals(emptyList<String>(), state.value.data)
            assertFalse(state.value.refreshing)
            assertEquals(0, calls)

            resource.request("key", force = true)
            runCurrent()
            assertEquals(1, calls)
            assertEquals(listOf("new"), state.value.data)
        }

    @Test
    fun `concurrent forced refreshes share the forced load`() =
        runTest {
            val pending = CompletableDeferred<String>()
            var calls = 0
            val resource =
                AcademicResource<String, String>(
                    backgroundScope,
                    { CachedResult("saved", CacheFreshness.FRESH, 100L) },
                    {
                        calls++
                        pending.await()
                    },
                    { _, _ -> 200L },
                )

            resource.request("key", force = true)
            runCurrent()
            resource.request("key", force = true)
            pending.complete("new")
            runCurrent()

            assertEquals(1, calls)
        }

    @Test
    fun `failure retains saved data and timestamp`() =
        runTest {
            val resource =
                AcademicResource<String, String>(
                    backgroundScope,
                    { CachedResult("saved", CacheFreshness.STALE, 100L) },
                    { error("offline") },
                    { _, _ -> error("unexpected write") },
                )

            val state = resource.request("key")
            runCurrent()

            assertEquals("saved", state.value.data)
            assertEquals(100L, state.value.updatedAtMillis)
            assertEquals("offline", state.value.error?.message)
        }

    @Test
    fun `failure in one dataset does not block another`() =
        runTest {
            val attendance =
                AcademicResource<String, String>(
                    backgroundScope,
                    { null },
                    { error("attendance offline") },
                    { _, _ -> 200L },
                )
            val timetable =
                AcademicResource<String, String>(
                    backgroundScope,
                    { null },
                    { "schedule" },
                    { _, _ -> 200L },
                )

            val attendanceState = attendance.request("account")
            val timetableState = timetable.request("account-week")
            runCurrent()

            assertEquals("attendance offline", attendanceState.value.error?.message)
            assertEquals("schedule", timetableState.value.data)
        }

    @Test
    fun `invalidation rejects an old completion and runs a newer request`() =
        runTest {
            val old = CompletableDeferred<String>()
            var calls = 0
            val writes = mutableListOf<String>()
            val resource =
                AcademicResource<String, String>(
                    backgroundScope,
                    { null },
                    {
                        calls++
                        if (calls == 1) old.await() else "after scan"
                    },
                    { _, data ->
                        writes += data
                        200L
                    },
                )

            val state = resource.request("key")
            runCurrent()
            resource.invalidate("key")
            runCurrent()
            old.complete("before scan")
            runCurrent()

            assertEquals(listOf("after scan"), writes)
            assertEquals("after scan", state.value.data)
        }

    @Test
    fun `deactivation rejects a late non cancellable result`() =
        runTest {
            val pending = CompletableDeferred<String>()
            var writes = 0
            val resource =
                AcademicResource<String, String>(
                    backgroundScope,
                    { null },
                    { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { pending.await() } },
                    { _, _ ->
                        writes++
                        200L
                    },
                )

            val state = resource.request("old account")
            runCurrent()
            resource.deactivate()
            pending.complete("late")
            runCurrent()

            assertEquals(0, writes)
            assertTrue(state.value.data == null)
        }

    @Test
    fun `cache clearing rejects a late write`() =
        runTest {
            val pending = CompletableDeferred<String>()
            var writes = 0
            var clears = 0
            val resource =
                AcademicResource<String, String>(
                    backgroundScope,
                    { null },
                    { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { pending.await() } },
                    { _, _ ->
                        writes++
                        200L
                    },
                )

            val state = resource.request("key")
            runCurrent()
            resource.clearCache { clears++ }
            pending.complete("late")
            runCurrent()

            assertEquals(1, clears)
            assertEquals(0, writes)
            assertEquals(null, state.value.data)
        }
}
