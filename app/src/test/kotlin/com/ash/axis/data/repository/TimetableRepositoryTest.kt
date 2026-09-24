package com.ash.axis.data.repository

import com.ash.axis.data.api.ICloudEmsApi
import com.ash.axis.data.db.CacheDao
import com.ash.axis.data.db.CacheEntity
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.axis.domain.model.UserInfo
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import retrofit2.Response

class TimetableRepositoryTest {
    private val api = mockk<ICloudEmsApi>()
    private val cacheDao = mockk<CacheDao>()
    private val authRepository = mockk<AuthRepository>()
    private val json = Json { ignoreUnknownKeys = true }
    private val repository = TimetableRepository(api, cacheDao, authRepository, json)
    private val context =
        StudentRequestContext(
            admno = "21001",
            brId = 11,
            clientId = "clientMixedCase",
            academicYear = "2026-2027",
        )

    @Test
    fun `legacy requests keep the exact student context`() =
        runTest {
            stubCache()
            val featureBody = slot<RequestBody>()
            val scheduleBody = slot<RequestBody>()
            coEvery { api.postTimetableV1(capture(featureBody)) } returns
                success("""{"status":false,"enble_batch_wise_course_flow":"1"}""")
            coEvery { api.postTimetable(capture(scheduleBody)) } returns emptySchedule()

            repository.getTimetable(context, "2026-09-07", "2026-09-13", forceRefresh = true)

            val feature = featureBody.captured.asJsonObject()
            assertEquals("getbatchwisebranchwiseflow", feature.string("method"))
            assertEquals("clientMixedCase", feature.string("client"))
            assertEquals("2026-2027", feature.string("acadyr"))
            assertEquals("21001", feature.string("admno"))
            assertEquals("11", feature.string("br_id"))

            val schedule = scheduleBody.captured.asJsonObject()
            assertEquals("getData", schedule.string("method"))
            assertEquals("clientMixedCase", schedule.string("client"))
            assertEquals("2026-2027", schedule.string("acadyr"))
            assertEquals("2026-09-07", schedule.string("startDate"))
            assertEquals("2026-09-13", schedule.string("endDate"))
        }

    @Test
    fun `confirmed v1 uses only v1 and stores an isolated key`() =
        runTest {
            val stored = slot<CacheEntity>()
            stubCache(stored)
            val bodies = mutableListOf<RequestBody>()
            coEvery { api.postTimetableV1(capture(bodies)) } returnsMany
                listOf(
                    success("""{"status":true,"enble_batch_wise_course_flow":"1"}"""),
                    emptySchedule(),
                )

            repository.getTimetable(context, "2026-09-07", "2026-09-13", forceRefresh = true)

            assertEquals("getbatchwisebranchwiseflow", bodies[0].asJsonObject().string("method"))
            assertEquals("getData", bodies[1].asJsonObject().string("method"))
            coVerify(exactly = 0) { api.postTimetable(any()) }
            assertEquals(
                "v3_timetable_weekly_21001_11_clientMixedCase_2026-2027_v1_2026-09-07_2026-09-13",
                stored.captured.key,
            )
        }

    @Test
    fun `feature check failure uses legacy`() =
        runTest {
            stubCache()
            coEvery { api.postTimetableV1(any()) } returns errorResponse()
            coEvery { api.postTimetable(any()) } returns emptySchedule()

            repository.getTimetable(context, "2026-09-07", "2026-09-13", forceRefresh = true)

            coVerify(exactly = 1) { api.postTimetable(any()) }
        }

    @Test
    fun `confirmed v1 schedule failure does not retry legacy`() =
        runTest {
            stubCache()
            coEvery { api.postTimetableV1(any()) } returnsMany
                listOf(
                    success("""{"status":true,"enble_batch_wise_course_flow":"1"}"""),
                    errorResponse(),
                )

            val result =
                runCatching {
                    repository.getTimetable(context, "2026-09-07", "2026-09-13", forceRefresh = true)
                }

            assertTrue(result.exceptionOrNull() is IcloudServerException)
            coVerify(exactly = 0) { api.postTimetable(any()) }
        }

    @Test
    fun `cancelled schedule does not become cached success`() =
        runTest {
            stubCache()
            coEvery { api.postTimetableV1(any()) } returns success("""{"status":false}""")
            coEvery { api.postTimetable(any()) } throws CancellationException("cancelled")
            coEvery { cacheDao.get(match { it.startsWith("v3_timetable_weekly") }) } returns
                CacheEntity("saved", "{\"Mon\":[]}")

            val failure =
                runCatching {
                    repository.getTimetable(context, "2026-09-07", "2026-09-13", forceRefresh = true)
                }.exceptionOrNull()

            assertTrue(failure is CancellationException)
        }

    @Test
    fun `weekly and dated views share one transport response`() =
        runBlocking {
            stubCache()
            coEvery { api.postTimetableV1(any()) } returns success("""{"status":false}""")
            coEvery { api.postTimetable(any()) } returns
                success(
                    """{"emp_timetable":{"2026-09-07":[{"from_time":"09:00","to_time":"10:00","subject_id":"CS1"}]}}""",
                )
            val key = TimetableKey(context, "2026-09-07", "2026-09-13")

            val state = repository.requestWeek(key)
            val data = withTimeout(5_000) { state.first { it.data != null && !it.refreshing }.data!! }

            assertEquals("CS1", data.weekly.getValue("Mon").single().subjectId)
            assertEquals("CS1", data.dated?.getValue("2026-09-07")?.single()?.subjectId)
            coVerify(exactly = 1) { api.postTimetable(any()) }
        }

    private fun stubCache(stored: io.mockk.CapturingSlot<CacheEntity>? = null) {
        coEvery { authRepository.refreshTokenIfNeeded() } returns "access"
        every { authRepository.getUserInfo() } returns UserInfo("21001", 11, "", "", "", "clientMixedCase")
        coEvery { cacheDao.get(any()) } returns null
        if (stored == null) {
            coEvery { cacheDao.put(any()) } just Runs
        } else {
            coEvery { cacheDao.put(capture(stored)) } just Runs
        }
    }

    private fun emptySchedule(): Response<ResponseBody> = success("""{"emp_timetable":{"Mon":[]}}""")

    private fun success(body: String): Response<ResponseBody> = Response.success(body.toResponseBody("application/json".toMediaType()))

    private fun errorResponse(): Response<ResponseBody> = Response.error(500, "server error".toResponseBody("text/plain".toMediaType()))

    private fun RequestBody.asJsonObject(): JsonObject {
        val buffer = Buffer()
        writeTo(buffer)
        return json.parseToJsonElement(buffer.readUtf8()).jsonObject
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
}
