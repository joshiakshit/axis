package com.ash.axis.data.db

import com.ash.core.storage.CacheFreshness
import com.ash.core.storage.CachePolicy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JsonCacheTest {
    private val dao = mockk<CacheDao>()
    private val cache = JsonCache(dao, Json)
    private val serializer = ListSerializer(String.serializer())

    @Test
    fun `empty cached result keeps its successful timestamp`() =
        runTest {
            val timestamp = System.currentTimeMillis()
            coEvery { dao.get("empty") } returns CacheEntity("empty", "[]", timestamp)

            val result = cache.read("empty", serializer, CachePolicy.ATTENDANCE)

            assertNotNull(result)
            assertEquals(emptyList<String>(), result?.data)
            assertEquals(timestamp, result?.cachedAtMillis)
            assertEquals(CacheFreshness.FRESH, result?.freshness)
        }

    @Test
    fun `expired data remains readable for explicit offline fallback`() =
        runTest {
            val timestamp = 1L
            coEvery { dao.get("old") } returns CacheEntity("old", "[\"saved\"]", timestamp)

            assertNull(cache.readAccepted("old", serializer, CachePolicy.ATTENDANCE))
            assertEquals(listOf("saved"), cache.read("old", serializer, CachePolicy.ATTENDANCE)?.data)
        }

    @Test
    fun `malformed entry is unavailable`() =
        runTest {
            coEvery { dao.get("bad") } returns CacheEntity("bad", "not json")

            assertNull(cache.read("bad", serializer, CachePolicy.ATTENDANCE))
            coVerify(exactly = 1) { dao.get("bad") }
        }
}
