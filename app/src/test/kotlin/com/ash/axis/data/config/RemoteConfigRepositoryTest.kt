package com.ash.axis.data.config

import com.ash.axis.data.api.RemoteConfigApi
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

private const val KEY = "remote_config"
private const val BAKED = "baked-in-token"

class RemoteConfigRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = mockk<PreferencesStore>(relaxed = false)

    private fun repo(api: RemoteConfigApi?) = RemoteConfigRepository(api, prefs, json)

    @Test
    fun `falls back to the baked-in token and default appVersion before any config is loaded`() {
        val r = repo(mockk())
        assertEquals(BAKED, r.effectiveAuthToken(BAKED))
        assertEquals(RemoteConfig.DEFAULT_APP_VERSION, r.appVersion())
    }

    @Test
    fun `enabled reflects whether an api is present`() {
        assertFalse(repo(null).enabled)
        assertTrue(repo(mockk()).enabled)
    }

    @Test
    fun `hydrate applies a persisted override`() =
        runTest {
            every { prefs.getString(KEY, "") } returns
                flowOf(json.encodeToString(RemoteConfig(authToken = "REMOTE", appVersion = "4.0.0")))
            val r = repo(mockk())

            r.hydrate()

            assertEquals("REMOTE", r.effectiveAuthToken(BAKED))
            assertEquals("4.0.0", r.appVersion())
        }

    @Test
    fun `hydrate keeps defaults when nothing is stored`() =
        runTest {
            every { prefs.getString(KEY, "") } returns flowOf("")
            val r = repo(mockk())

            r.hydrate()

            assertEquals(BAKED, r.effectiveAuthToken(BAKED))
        }

    @Test
    fun `refresh is a no-op and writes nothing when the backend is disabled`() =
        runTest {
            val r = repo(null)

            r.refresh()

            assertEquals(BAKED, r.effectiveAuthToken(BAKED))
            coVerify(exactly = 0) { prefs.putString(any(), any()) }
        }

    @Test
    fun `refresh fetches, persists, and publishes the new config`() =
        runTest {
            val api = mockk<RemoteConfigApi>()
            coEvery { api.getConfig() } returns RemoteConfig(authToken = "NEW", killSwitch = true)
            coEvery { prefs.putString(KEY, any()) } just Runs
            val r = repo(api)

            r.refresh()

            assertEquals("NEW", r.effectiveAuthToken(BAKED))
            assertTrue(r.state.value.killSwitch)
            coVerify { prefs.putString(KEY, any()) }
        }

    @Test
    fun `refresh swallows network failures and keeps the last good config`() =
        runTest {
            val api = mockk<RemoteConfigApi>()
            coEvery { api.getConfig() } throws RuntimeException("network down")
            val r = repo(api)

            r.refresh()

            assertEquals(BAKED, r.effectiveAuthToken(BAKED))
            coVerify(exactly = 0) { prefs.putString(any(), any()) }
        }
}
