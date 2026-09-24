package com.ash.axis.data.repository

import com.ash.axis.data.api.ICloudEmsApi
import com.ash.axis.data.db.CacheDao
import com.ash.axis.data.db.CacheEntity
import com.ash.axis.domain.model.GradesData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import retrofit2.Response

class GradesRepositoryTest {
    private val api = mockk<ICloudEmsApi>()
    private val dao = mockk<CacheDao>()
    private val auth = mockk<AuthRepository>()
    private val repository = GradesRepository(api, dao, auth, Json)
    private val key = "v2_grades_student_session_2"

    @Test
    fun `failed forced grades refresh returns expired saved result`() =
        runTest {
            coEvery { auth.refreshTokenIfNeeded() } returns "token"
            coEvery { dao.get(key) } returns
                CacheEntity(key, Json.encodeToString(GradesData.serializer(), GradesData(noResultMsg = "Saved")), 1)
            coEvery { api.postReportCardController(any()) } returns Response.error(503, "unavailable".toResponseBody())

            val result = repository.getGrades("student", 11, "session", listOf("2"), forceRefresh = true)

            assertEquals("Saved", result.noResultMsg)
            coVerify(exactly = 1) { api.postReportCardController(any()) }
        }

    @Test
    fun `cancelled grades request never uses cached fallback`() =
        runTest {
            coEvery { auth.refreshTokenIfNeeded() } throws CancellationException("cancelled")
            coEvery { dao.get(key) } returns CacheEntity(key, Json.encodeToString(GradesData.serializer(), GradesData()), 1)

            assertThrows<CancellationException> {
                kotlinx.coroutines.runBlocking {
                    repository.getGrades("student", 11, "session", listOf("2"), forceRefresh = true)
                }
            }
            coVerify(exactly = 0) { dao.get(key) }
        }
}
