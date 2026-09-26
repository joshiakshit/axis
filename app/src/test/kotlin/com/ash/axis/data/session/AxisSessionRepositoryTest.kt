package com.ash.axis.data.session

import com.ash.axis.data.api.AxisBackendApi
import com.ash.core.security.TokenManager
import com.ash.core.storage.PreferencesStore
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

private const val KEY = "axis_session"

class AxisSessionRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val tokenManager = mockk<TokenManager>()
    private val prefs = mockk<PreferencesStore>()

    private fun repo(api: AxisBackendApi?) = AxisSessionRepository(api, tokenManager, prefs, json)

    @Test
    fun `disabled build reports not-enabled and never calls the network`() =
        runTest {
            val r = repo(null)
            assertFalse(r.state.value.enabled)

            r.refresh()

            coVerify(exactly = 0) { prefs.putUserString(any(), any()) }
        }

    @Test
    fun `refresh with no active token is a no-op`() =
        runTest {
            every { tokenManager.getAccessToken() } returns null
            val r = repo(mockk())

            r.refresh()

            coVerify(exactly = 0) { prefs.putUserString(any(), any()) }
        }

    @Test
    fun `refresh persists approved session without admin actions`() =
        runTest {
            val api = mockk<AxisBackendApi>()
            every { tokenManager.getAccessToken() } returns "icloud-token"
            coEvery { prefs.putUserString(KEY, any()) } just Runs
            coEvery { api.session(any()) } returns
                AxisSession(status = "approved", role = "admin", admno = "21000", sessionToken = "sess-tok")
            val r = repo(api)

            r.refresh()

            assertEquals("approved", r.state.value.status)
            coVerify { prefs.putUserString(KEY, any()) }
            coVerify {
                api.session(match { !json.encodeToString(it).contains("deviceId") && it.token == "icloud-token" })
            }
        }

    @Test
    fun `refresh keeps the last-known status on a network error`() =
        runTest {
            val api = mockk<AxisBackendApi>()
            every { tokenManager.getAccessToken() } returns "icloud-token"
            coEvery { api.session(any()) } throws RuntimeException("network down")
            val r = repo(api)

            r.refresh()

            assertEquals(AxisSession.STATUS_UNKNOWN, r.state.value.status)
            coVerify(exactly = 0) { prefs.putUserString(any(), any()) }
        }

    @Test
    fun `hydrate applies a cached approved-admin session`() =
        runTest {
            every { prefs.getUserString(KEY, "") } returns
                flowOf(json.encodeToString(AxisSession(status = "approved", role = "admin", sessionToken = "t")))
            val r = repo(mockk())

            r.hydrate()

            assertEquals("approved", r.state.value.status)
        }
}
