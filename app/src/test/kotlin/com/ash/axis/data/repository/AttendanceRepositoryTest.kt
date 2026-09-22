package com.ash.axis.data.repository

import com.ash.axis.data.api.ICloudEmsApi
import com.ash.axis.data.api.QrAttendanceApi
import com.ash.axis.data.db.CacheDao
import com.ash.axis.ui.qr.QrDiagnostics
import com.ash.axis.ui.qr.QrScanMode
import com.ash.axis.ui.qr.QrStage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import retrofit2.Response

class AttendanceRepositoryTest {
    @Test
    fun `qr scan preserves the jwt client id`() =
        runTest {
            val api = mockk<ICloudEmsApi>()
            val qrApi = mockk<QrAttendanceApi>()
            val authRepository = mockk<AuthRepository>()
            val body = slot<RequestBody>()
            var submissionStarted = false
            coEvery { authRepository.refreshTokenIfNeeded() } returns "token"
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
}
