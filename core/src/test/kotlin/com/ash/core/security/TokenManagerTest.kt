package com.ash.core.security

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TokenManagerTest {
    private val values =
        mutableMapOf<String, Any>(
            "active_admno" to "A",
            "A_access_token" to "active-token",
            "A_refresh_token" to "active-refresh",
            "B_access_token" to "other-token",
            "B_refresh_token" to "other-refresh",
            "account_list" to "old-list",
            "device_id" to "university-device",
        )
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val prefs = mockk<SharedPreferences>()

    private fun manager(): TokenManager {
        every { prefs.all } answers { values.toMap() }
        every { prefs.getString(any(), any()) } answers { values[firstArg<String>()] as? String ?: secondArg() }
        every { prefs.edit() } returns editor
        every { editor.remove(any()) } answers {
            values.remove(firstArg<String>())
            editor
        }
        every { editor.putString(any(), any()) } answers {
            values[firstArg<String>()] = secondArg<String>()
            editor
        }
        return TokenManager(prefs)
    }

    @Test
    fun `upgrade keeps active credentials and removes alternate accounts`() {
        val manager = manager()
        assertEquals("active-token", manager.getAccessToken())
        assertEquals("active-refresh", manager.getRefreshToken())
        assertFalse(values.containsKey("B_access_token"))
        assertFalse(values.containsKey("B_refresh_token"))
        assertFalse(values.containsKey("account_list"))
    }

    @Test
    fun `logout cannot select a previously saved account`() {
        val manager = manager()
        manager.clearCurrentAccount()
        assertNull(manager.getActiveAdmno())
        assertNull(manager.getAccessToken())
        assertEquals(mapOf("device_id" to "university-device"), values)
    }

    @Test
    fun `new login removes previous credentials and keeps university device identity`() {
        val manager = manager()
        manager.setActiveAdmno("C")
        manager.saveTokens("new-token", "new-refresh")
        assertEquals("C", manager.getActiveAdmno())
        assertEquals("new-token", manager.getAccessToken())
        assertFalse(values.containsKey("A_access_token"))
        assertEquals("university-device", manager.getDeviceId())
    }
}
