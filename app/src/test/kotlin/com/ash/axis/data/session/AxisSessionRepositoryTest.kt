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
import org.junit.jupiter.api.Assertions.assertTrue
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
            assertFalse(r.isAdmin())

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
    fun `refresh publishes approved admin, persists, and enables admin calls`() =
        runTest {
            val api = mockk<AxisBackendApi>()
            every { tokenManager.getAccessToken() } returns "icloud-token"
            coEvery { prefs.putUserString(KEY, any()) } just Runs
            coEvery { api.session(SessionRequest("icloud-token")) } returns
                AxisSession(status = "approved", role = "admin", admno = "21000", sessionToken = "sess-tok")
            coEvery { api.listUsers("Bearer sess-tok") } returns UsersResponse(listOf(AdminUser("21001")))
            val r = repo(api)

            r.refresh()

            assertEquals("approved", r.state.value.status)
            assertTrue(r.isAdmin())
            coVerify { prefs.putUserString(KEY, any()) }
            // The admin session token was captured, so authorized calls succeed.
            assertEquals("21001", r.listUsers().single().admno)
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

            assertTrue(r.isAdmin())
            assertEquals("approved", r.state.value.status)
        }

    @Test
    fun `setUserStatus routes to allow or kick with the admin bearer`() =
        runTest {
            val api = mockk<AxisBackendApi>()
            every { tokenManager.getAccessToken() } returns "icloud-token"
            coEvery { prefs.putUserString(KEY, any()) } just Runs
            coEvery { api.session(any()) } returns AxisSession(status = "approved", role = "admin", sessionToken = "sess-tok")
            coEvery { api.allow("21001", "Bearer sess-tok") } returns AdminUser("21001", status = "approved")
            coEvery { api.kick("21001", "Bearer sess-tok") } returns AdminUser("21001", status = "pending")
            val r = repo(api)
            r.refresh()

            assertEquals("approved", r.setUserStatus("21001", allow = true)?.status)
            assertEquals("pending", r.setUserStatus("21001", allow = false)?.status)
        }
}
