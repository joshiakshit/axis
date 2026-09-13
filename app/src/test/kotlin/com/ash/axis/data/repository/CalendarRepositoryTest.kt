package com.ash.axis.data.repository

import com.ash.axis.data.api.UserApi
import com.ash.axis.data.db.CacheDao
import com.ash.axis.data.db.CacheEntity
import com.ash.axis.domain.model.UserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import retrofit2.Response
import java.time.LocalDate

class CalendarRepositoryTest {
    private val api = mockk<UserApi>()
    private val cache = mockk<CacheDao>()
    private val entries = mutableMapOf<String, CacheEntity>()
    private val repository = CalendarRepository(api, cache, Json { ignoreUnknownKeys = true })
    private val user = UserInfo("student", 11, "Student", "student@example.test", "", "college")
    private val start = LocalDate.of(2026, 9, 1)
    private val end = start.withDayOfMonth(30)

    init {
        coEvery { cache.get(any()) } answers { entries[firstArg()] }
        coEvery { cache.put(any()) } answers { firstArg<CacheEntity>().let { entries[it.key] = it } }
    }

    @Test
    fun `calendar sends date range and separates events from holidays`() =
        runTest {
            coEvery { api.getEvents(any()) } returns
                Response.success(
                    """
{"result":[{"date":"2026-09-14","title":"Talk"}]}
""".toResponseBody(),
                )
            coEvery { api.getHolidays(any()) } returns
                Response.success(
                    """
{"result":[{"date":"2026-09-15","name":"Holiday"}]}
""".toResponseBody(),
                )
            val result = repository.getCalendar(user, start, end, true)
            assertEquals(listOf(false, true), result.data.map { it.isHoliday })
            val body = mapOf("br_id" to "11", "fromdate" to "2026-09-01", "todate" to "2026-09-30", "lastmodifiedby" to user.email)
            coVerify { api.getEvents(body) }
            coVerify { api.getHolidays(body) }
        }

    @Test
    fun `failed calendar refresh preserves cached entries and timestamp`() =
        runTest {
            coEvery { api.getEvents(any()) } returns
                Response.success(
                    """
{"result":[]}
""".toResponseBody(),
                )
            coEvery { api.getHolidays(any()) } returns
                Response.success(
                    """
{"result":[]}
""".toResponseBody(),
                )
            val first = repository.getCalendar(user, start, end, true)
            coEvery { api.getEvents(any()) } returns Response.error(503, "private".toResponseBody())
            val cached = repository.getCalendar(user, start, end, true)
            assertTrue(cached.fromCache)
            assertEquals(first.updatedAt, cached.updatedAt)
            assertNotNull(cached.error)
            assertThrows<Exception> { repository.getCalendar(user.copy(clientId = "other"), start, end, true) }
        }

    @Test
    fun `malformed calendar rows are errors instead of empty results`() =
        runTest {
            coEvery { api.getEvents(any()) } returns
                Response.success(
                    """
{"result":[{"date":"bad date","title":"Talk"}]}
""".toResponseBody(),
                )
            assertThrows<Exception> { repository.getCalendar(user, start, end, true) }
            coVerify(exactly = 0) { cache.put(any()) }
        }
}
