package com.ash.axis.data.repository

import com.ash.axis.data.api.ICloudEmsApi
import com.ash.axis.data.api.QrAttendanceApi
import com.ash.axis.data.db.CacheDao
import io.mockk.coEvery
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
            coEvery { authRepository.refreshTokenIfNeeded() } returns "token"
            coEvery { qrApi.sendScanQR(capture(body)) } returns Response.success("{}".toResponseBody())
            val repository = AttendanceRepository(api, qrApi, mockk<CacheDao>(), authRepository, Json)

            repository.sendScanQR(
                rawQr = "qr",
                admno = "21001",
                email = "student@example.com",
                brId = 1,
                latitude = null,
                longitude = null,
                clientId = "clientMixedCase",
            )

            val buffer = Buffer()
            body.captured.writeTo(buffer)
            val payload = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
            assertEquals("clientMixedCase", payload.getValue("collegeid").jsonPrimitive.content)
        }
}
