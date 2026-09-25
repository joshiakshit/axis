package com.ash.axis.data.repository

import com.ash.axis.data.api.ICloudEmsApi
import com.ash.axis.data.api.QrAttendanceApi
import com.ash.axis.data.db.CacheDao
import com.ash.axis.data.db.CacheEntity
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.axis.domain.model.UserInfo
import com.ash.axis.ui.qr.QrDiagnostics
import com.ash.axis.ui.qr.QrScanMode
import com.ash.axis.ui.qr.QrStage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import retrofit2.Response

class AttendanceRepositoryTest {
    @Test
    fun `semester metadata cannot read another active account cache`() =
        runTest {
            val authRepository = mockk<AuthRepository>()
            val cacheDao = mockk<CacheDao>()
            every { authRepository.getUserInfo() } returns UserInfo("other", 1, "Other", "", "", "Client")
            val repository = AttendanceRepository(mockk<ICloudEmsApi>(), mockk<QrAttendanceApi>(), cacheDao, authRepository, Json)

            val failure = runCatching { repository.getAcadYears("21001", 1) }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            coVerify(exactly = 0) { cacheDao.get(any()) }
        }

    @Test
    fun `observable summary does not hydrate unscoped v2 attendance data`() =
        runBlocking {
            val api = mockk<ICloudEmsApi>()
            val cacheDao = mockk<CacheDao>()
            val authRepository = mockk<AuthRepository>()
            val response = CompletableDeferred<Unit>()
            coEvery { cacheDao.get(any()) } answers {
                if (firstArg<String>().startsWith("v2_attendance_")) {
                    CacheEntity("v2_attendance_21001_C1_2025-2026", """{"table":{},"endrow":{}}""")
                } else {
                    null
                }
            }
            coEvery { authRepository.refreshTokenIfNeeded() } returns "token"
            every { authRepository.getUserInfo() } returns UserInfo("21001", 11, "", "", "", "clientMixedCase")
            coEvery { api.postAttendance(any()) } coAnswers {
                response.await()
                Response.success("""{"attendance":{"table":{},"endrow":{}}}""".toResponseBody())
            }
            coEvery { cacheDao.put(any()) } returns Unit
            val repository = AttendanceRepository(api, mockk<QrAttendanceApi>(), cacheDao, authRepository, Json)
            val key = AttendanceKey(StudentRequestContext("21001", 11, "clientMixedCase", "2026-2027"), "C1", "2025-2026")

            val state = repository.requestSummary(key)

            assertNull(state.value.data)
            coVerify(exactly = 1) { cacheDao.get(key.cacheKey) }
            coVerify(exactly = 0) { cacheDao.get(match { it.startsWith("v2_attendance_") }) }
            response.complete(Unit)
            withTimeout(5_000) { state.first { it.data != null && !it.refreshing } }
        }

    @Test
    fun `legacy summary load keeps saved data after network failure`() =
        runTest {
            val api = mockk<ICloudEmsApi>()
            val cacheDao = mockk<CacheDao>()
            val authRepository = mockk<AuthRepository>()
            coEvery { cacheDao.get(any()) } returns CacheEntity("saved", """{"table":{},"endrow":{}}""", 123L)
            coEvery { authRepository.refreshTokenIfNeeded() } returns "token"
            coEvery { api.postAttendance(any()) } throws IllegalStateException("offline")
            val repository = AttendanceRepository(api, mockk<QrAttendanceApi>(), cacheDao, authRepository, Json)

            val result = repository.getAttendance("21001", 11, "C1", "2025-2026", forceRefresh = true)

            assertEquals(emptyMap<String, Any>(), result.table)
            coVerify(exactly = 0) { cacheDao.put(any()) }
        }

    @Test
    fun `attendance key ignores profile year but keeps selected year`() {
        val first = StudentRequestContext("21001", 11, "clientMixedCase", "")
        val resolved = first.copy(academicYear = "2026-2027")

        assertEquals(AttendanceKey(first, "C1", "2025-2026"), AttendanceKey(resolved, "C1", "2025-2026"))
        assertTrue(AttendanceKey(first, "C1", "2025-2026") != AttendanceKey(resolved, "C1", "2024-2025"))
    }

    @Test
    fun `observable summary accepts empty success with exact client id`() =
        runBlocking {
            val api = mockk<ICloudEmsApi>()
            val cacheDao = mockk<CacheDao>()
            val authRepository = mockk<AuthRepository>()
            val body = slot<RequestBody>()
            coEvery { cacheDao.get(any()) } returns null
            coEvery { cacheDao.put(any()) } returns Unit
            coEvery { authRepository.refreshTokenIfNeeded() } returns "token"
            every { authRepository.getUserInfo() } returns UserInfo("21001", 11, "", "", "", "clientMixedCase")
            coEvery { api.postAttendance(capture(body)) } returns
                Response.success(
                    """{"attendance":{"table":{},"endrow":{}}}""".toResponseBody(),
                )
            val repository = AttendanceRepository(api, mockk<QrAttendanceApi>(), cacheDao, authRepository, Json)
            val key = AttendanceKey(StudentRequestContext("21001", 11, "clientMixedCase", "2026-2027"), "C1", "2025-2026")

            val state = repository.requestSummary(key)
            val result = withTimeout(5_000) { state.first { it.data != null && !it.refreshing } }

            assertEquals(emptyMap<String, Any>(), result.data?.table)
            assertTrue(result.updatedAtMillis != null)
            val buffer = Buffer()
            body.captured.writeTo(buffer)
            val payload = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
            assertEquals("clientMixedCase", payload.getValue("client").jsonPrimitive.content)
            assertEquals("2025-2026", payload.getValue("year").jsonPrimitive.content)
        }

    @Test
    fun `account change during summary request cannot write old cache`() =
        runBlocking {
            val api = mockk<ICloudEmsApi>()
            val cacheDao = mockk<CacheDao>()
            val authRepository = mockk<AuthRepository>()
            val started = CompletableDeferred<Unit>()
            val response = CompletableDeferred<Unit>()
            var currentAdmno = "21001"
            coEvery { cacheDao.get(any()) } returns null
            coEvery { authRepository.refreshTokenIfNeeded() } returns "token"
            every { authRepository.getUserInfo() } answers {
                UserInfo(currentAdmno, 11, "", "", "", "clientMixedCase")
            }
            coEvery { api.postAttendance(any()) } coAnswers {
                started.complete(Unit)
                response.await()
                Response.success("""{"attendance":{"table":{},"endrow":{}}}""".toResponseBody())
            }
            val repository = AttendanceRepository(api, mockk<QrAttendanceApi>(), cacheDao, authRepository, Json)
            val key = AttendanceKey(StudentRequestContext("21001", 11, "clientMixedCase", "2026-2027"), "C1", "2025-2026")

            val state = repository.requestSummary(key)
            withTimeout(5_000) { started.await() }
            currentAdmno = "other"
            response.complete(Unit)
            val result = withTimeout(5_000) { state.first { it.error != null } }

            assertEquals(null, result.data)
            coVerify(exactly = 0) { cacheDao.put(any()) }
        }

    @Test
    fun `qr scan preserves the jwt client id`() =
        runTest {
            val api = mockk<ICloudEmsApi>()
            val qrApi = mockk<QrAttendanceApi>()
            val authRepository = mockk<AuthRepository>()
            val body = slot<RequestBody>()
            var submissionStarted = false
            coEvery { authRepository.refreshTokenIfNeeded() } returns "token"
            every { authRepository.getUserInfo() } returns UserInfo("21001", 1, "Student", "", "", "clientMixedCase")
            coEvery { qrApi.sendScanQR(capture(body)) } answers {
                assertTrue(submissionStarted)
                Response.success("{}".toResponseBody())
            }
            val repository = AttendanceRepository(api, qrApi, mockk<CacheDao>(), authRepository, Json)

            val result =
                repository.sendScanQR(
                    rawQr = "qr",
                    admno = "21001",
                    email = "student@example.com",
                    brId = 1,
                    latitude = null,
                    longitude = null,
                    clientId = "clientMixedCase",
                    onSubmissionStart = { submissionStarted = true },
                )

            val buffer = Buffer()
            body.captured.writeTo(buffer)
            val payload = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
            assertEquals("clientMixedCase", payload.getValue("collegeid").jsonPrimitive.content)
            assertEquals(200, result.httpStatus)
            assertTrue(result.httpDurationMs != null && result.httpDurationMs >= 0)
        }

    @Test
    fun `failed token refresh does not start qr submission`() =
        runTest {
            val qrApi = mockk<QrAttendanceApi>()
            val authRepository = mockk<AuthRepository>()
            coEvery { authRepository.refreshTokenIfNeeded() } throws SessionExpiredException()
            val repository = AttendanceRepository(mockk<ICloudEmsApi>(), qrApi, mockk<CacheDao>(), authRepository, Json)
            val lines = mutableListOf<String>()
            val diagnostics = QrDiagnostics(enabled = true, logger = lines::add)
            diagnostics.start(QrScanMode.ATTENDANCE)
            diagnostics.stage(QrStage.AUTHENTICATION)

            val failure =
                runCatching {
                    repository.sendScanQR(
                        rawQr = "qr",
                        admno = "21001",
                        email = "student@example.com",
                        brId = 1,
                        latitude = null,
                        longitude = null,
                        onSubmissionStart = { diagnostics.stage(QrStage.SUBMISSION) },
                    )
                }.exceptionOrNull()

            assertTrue(failure is SessionExpiredException)
            assertEquals(QrStage.AUTHENTICATION, diagnostics.state.value.stage)
            assertTrue(lines.none { it.contains("stage=submission") })
            coVerify(exactly = 0) { qrApi.sendScanQR(any()) }
        }

    @Test
    fun `account change during QR token refresh prevents submission`() =
        runTest {
            val qrApi = mockk<QrAttendanceApi>()
            val authRepository = mockk<AuthRepository>()
            coEvery { authRepository.refreshTokenIfNeeded() } returns "new account token"
            every { authRepository.getUserInfo() } returns UserInfo("other", 1, "Other", "", "", "clientMixedCase")
            val repository = AttendanceRepository(mockk<ICloudEmsApi>(), qrApi, mockk<CacheDao>(), authRepository, Json)

            val failure =
                runCatching {
                    repository.sendScanQR("qr", "21001", "student@example.com", 1, null, null, clientId = "clientMixedCase")
                }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            coVerify(exactly = 0) { qrApi.sendScanQR(any()) }
        }
}
