package com.ash.axis.data.repository

import com.ash.axis.data.api.UserApi
import com.ash.axis.data.db.CacheDao
import com.ash.axis.data.db.CacheEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import retrofit2.Response
import java.io.IOException

class NotificationRepositoryTest {
    private val api = mockk<UserApi>()
    private val cache = mockk<CacheDao>()
    private val entries = mutableMapOf<String, CacheEntity>()
    private val json = Json { ignoreUnknownKeys = true }
    private val repository = NotificationRepository(api, cache, json)

    init {
        coEvery { cache.get(any()) } answers { entries[firstArg()] }
        coEvery { cache.put(any()) } answers { firstArg<CacheEntity>().let { entries[it.key] = it } }
    }

    @Test
    fun `HTTP failure without cache is an error`() =
        runTest {
            coEvery { api.getNotifications(any()) } returns Response.error(503, "private server body".toResponseBody())
            assertThrows<Exception> { repository.getNotifications("college:student", true) }
            coVerify(exactly = 0) { cache.put(any()) }
        }

    @Test
    fun `invalid payload cannot replace cached data with empty data`() =
        runTest {
            coEvery { api.getNotifications(any()) } returns
                Response.success(
                    """
{"result":[{"id":12,"title":"Notice"}]}
""".toResponseBody(),
                )
            val first = repository.getNotifications("college:student", true)
            coEvery { api.getNotifications(any()) } returns
                Response.success(
                    """
{"status":false,"message":"Failed"}
""".toResponseBody(),
                )
            val cached = repository.getNotifications("college:student", true)
            assertEquals(first.data, cached.data)
            assertEquals(first.updatedAt, cached.updatedAt)
            assertTrue(cached.fromCache)
            assertNotNull(cached.error)
        }

    @Test
    fun `confirmed empty cache stays distinct from failed first load`() =
        runTest {
            coEvery { api.getNotifications(any()) } returns
                Response.success(
                    """
{"result":[]}
""".toResponseBody(),
                )
            assertFalse(repository.getNotifications("college:student", true).fromCache)
            coEvery { api.getNotifications(any()) } throws IOException()
            val cached = repository.getNotifications("college:student", true)
            assertTrue(cached.data.isEmpty())
            assertTrue(cached.fromCache)
            assertNotNull(cached.error)
        }

    @Test
    fun `read status survives refresh and stays within its account`() =
        runTest {
            coEvery {
                api.getNotifications(any())
            } returns
                Response.success(
                    """
{"result":[{"title":"Notice","body":"Hello","createdAt":"2026-09-13"}]}
""".toResponseBody(),
                )
            val notification = repository.getNotifications("college:student", true).data.single()
            repository.markAsRead("college:student", notification)
            coEvery {
                api.getNotifications(any())
            } returns
                Response.success(
                    """
{"result":[{"title":"Notice","body":"Hello","createdAt":"2026-09-13"}]}
""".toResponseBody(),
                )
            assertTrue(repository.getNotifications("college:student", true).data.single().read)
            coEvery {
                api.getNotifications(any())
            } returns
                Response.success(
                    """
{"result":[{"title":"Notice","body":"Hello","createdAt":"2026-09-13"}]}
""".toResponseBody(),
                )
            assertFalse(repository.getNotifications("college:other", true).data.single().read)
        }

    @Test
    fun `cancellation propagates`() =
        runTest {
            coEvery { api.getNotifications(any()) } throws CancellationException()
            assertThrows<CancellationException> { repository.getNotifications("college:student", true) }
        }
}
