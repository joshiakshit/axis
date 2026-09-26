package com.ash.axis.data.repository

import com.ash.axis.data.api.AuthApi
import com.ash.axis.data.api.UserApi
import com.ash.axis.data.config.RemoteConfigRepository
import com.ash.axis.data.device.DeviceIdProvider
import com.ash.axis.domain.model.LoginResponse
import com.ash.axis.domain.model.LoginResponseData
import com.ash.axis.domain.model.PersonalDetailsData
import com.ash.axis.domain.model.PersonalDetailsResponse
import com.ash.axis.domain.model.StudentPersonalDetails
import com.ash.axis.domain.model.TokenData
import com.ash.core.security.TokenManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Duration
import java.util.Base64

class AuthRepositoryTest {
    private val authApi = mockk<AuthApi>()
    private val userApi = mockk<UserApi>()
    private val tokenManager = mockk<TokenManager>()
    private val deviceIdProvider = mockk<DeviceIdProvider>()
    private val remoteConfig = mockk<RemoteConfigRepository>()
    private val json = Json { ignoreUnknownKeys = true }
    private val repository = AuthRepository(authApi, userApi, tokenManager, deviceIdProvider, remoteConfig, json)

    @Test
    fun `otp requests use phone method and the configured app version`() =
        runTest {
            every { deviceIdProvider.get() } returns "device"
            every { remoteConfig.appVersion() } returns "3.0.9"
            coEvery { authApi.requestOtp(any()) } returns LoginResponse(LoginResponseData(username = "student"))
            val body = slot<Map<String, String>>()

            repository.requestOtp("9999999999")

            coVerify { authApi.requestOtp(capture(body)) }
            assertEquals("phone", body.captured["method"])
            assertEquals("9999999999", body.captured["contact"])
            assertEquals("3.0.9", body.captured["appversion"])
        }

    @Test
    fun `otp validation uses the configured app version`() =
        runTest {
            val token = jwt()
            every { deviceIdProvider.get() } returns "device"
            every { remoteConfig.appVersion() } returns "3.0.9"
            every { tokenManager.setActiveAdmno(any()) } just Runs
            every { tokenManager.saveTokens(any(), any()) } just Runs
            every { tokenManager.saveUserMeta(any(), any()) } just Runs
            coEvery { authApi.validateOtp(any()) } returns
                LoginResponse(LoginResponseData(token = TokenData(token, "refresh")))
            val body = slot<Map<String, String>>()

            repository.validateOtp("9999999999", "1234")

            coVerify { authApi.validateOtp(capture(body)) }
            assertEquals("3.0.9", body.captured["appversion"])
        }

    @Test
    fun `personal details use jwt identity and store academic year`() =
        runTest {
            stubProfileCache()
            coEvery { userApi.getPersonalDetails(any()) } returns personalDetails("2026-2027")
            val body = slot<Map<String, String>>()

            val context = repository.requireStudentRequestContext()

            coVerify { userApi.getPersonalDetails(capture(body)) }
            assertEquals("21001", body.captured["code"])
            assertEquals("student", body.captured["type"])
            assertEquals("2026-2027", context.academicYear)
            assertEquals("clientMixedCase", context.clientId)
        }

    @Test
    fun `fresh profile cache avoids a network request`() =
        runTest {
            stubProfileCache("2026-2027", System.currentTimeMillis())

            val context = repository.requireStudentRequestContext()

            assertEquals("2026-2027", context.academicYear)
            coVerify(exactly = 0) { userApi.getPersonalDetails(any()) }
        }

    @Test
    fun `normal load uses a stale cached year after a profile failure`() =
        runTest {
            stubProfileCache("2025-2026", System.currentTimeMillis() - Duration.ofHours(25).toMillis())
            coEvery { userApi.getPersonalDetails(any()) } throws IOException("offline")

            val context = repository.requireStudentRequestContext()

            assertEquals("2025-2026", context.academicYear)
        }

    @Test
    fun `forced refresh replaces a fresh cached year`() =
        runTest {
            stubProfileCache("2025-2026", System.currentTimeMillis())
            coEvery { userApi.getPersonalDetails(any()) } returns personalDetails("2026-2027")

            val context = repository.requireStudentRequestContext(forceProfileRefresh = true)

            assertEquals("2026-2027", context.academicYear)
            coVerify(exactly = 1) { userApi.getPersonalDetails(any()) }
        }

    @Test
    fun `missing academic year returns an error`() =
        runTest {
            stubProfileCache()
            coEvery { userApi.getPersonalDetails(any()) } returns personalDetails("")

            val result = runCatching { repository.requireStudentRequestContext() }

            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }

    @Test
    fun `concurrent profile requests share one fetch`() =
        runTest {
            stubProfileCache()
            coEvery { userApi.getPersonalDetails(any()) } coAnswers {
                delay(10)
                personalDetails("2026-2027")
            }

            val contexts =
                listOf(
                    async { repository.requireStudentRequestContext() },
                    async { repository.requireStudentRequestContext() },
                ).awaitAll()

            assertTrue(contexts.all { it.academicYear == "2026-2027" })
            coVerify(exactly = 1) { userApi.getPersonalDetails(any()) }
        }

    private fun stubProfileCache(
        initialYear: String = "",
        initialFetchedAt: Long = 0L,
    ) {
        var academicYear = initialYear
        var fetchedAt = initialFetchedAt
        every { tokenManager.getAccessToken() } returns jwt()
        every { tokenManager.getAcademicYear() } answers { academicYear }
        every { tokenManager.getAcademicYearFetchedAt() } answers { fetchedAt }
        every { tokenManager.saveAcademicYear(any(), any()) } answers {
            academicYear = firstArg()
            fetchedAt = secondArg()
        }
    }

    private fun personalDetails(academicYear: String) =
        PersonalDetailsResponse(
            data = PersonalDetailsData(StudentPersonalDetails(academicYear = academicYear)),
        )

    private fun jwt(): String {
        val header = encode("""{"alg":"HS256","typ":"JWT"}""")
        val payload =
            encode(
                """
                {
                    "exp": 4102444800,
                    "admno": "21001",
                    "br_id": 11,
                    "name": "Test",
                    "email": "test@example.com",
                    "user_type": "student",
                    "client_id": "clientMixedCase"
                }
                """.trimIndent(),
            )
        return "$header.$payload.signature"
    }

    private fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
}
