package com.ash.axis

import android.util.Base64
import com.ash.axis.domain.model.JwtPayload
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthTokenTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun createJwt(exp: Long): String {
        val header = base64Encode("""{"alg":"HS256","typ":"JWT"}""")
        val payload =
            base64Encode(
                """
                {
                    "exp":$exp,
                    "iat":0,
                    "admno":"21001",
                    "br_id":1,
                    "name":"Test",
                    "email":"test@test.com",
                    "phone_number":"9999999999",
                    "user_type":"student",
                    "client_id":"gu"
                }
                """.trimIndent(),
            )
        return "$header.$payload.signature"
    }

    private fun base64Encode(input: String): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(input.toByteArray())

    private fun decodePayload(token: String): JwtPayload {
        val parts = token.split(".")
        val payload = String(java.util.Base64.getUrlDecoder().decode(parts[1]))
        return json.decodeFromString<JwtPayload>(payload)
    }

    private fun isExpired(token: String): Boolean {
        return try {
            val payload = decodePayload(token)
            payload.exp < (System.currentTimeMillis() / 1000) + 60
        } catch (_: Exception) {
            true
        }
    }

    @Test
    fun `decode JWT payload extracts user info`() {
        val token = createJwt(System.currentTimeMillis() / 1000 + 3600)
        val payload = decodePayload(token)
        assertEquals("21001", payload.admno)
        assertEquals(1, payload.brId)
        assertEquals("Test", payload.name)
        assertEquals("test@test.com", payload.email)
        assertEquals("9999999999", payload.phoneNumber)
    }

    @Test
    fun `token with future exp is not expired`() {
        val token = createJwt(System.currentTimeMillis() / 1000 + 3600)
        assertFalse(isExpired(token))
    }

    @Test
    fun `token with past exp is expired`() {
        val token = createJwt(System.currentTimeMillis() / 1000 - 100)
        assertTrue(isExpired(token))
    }

    @Test
    fun `token expiring within 60 seconds is treated as expired`() {
        val token = createJwt(System.currentTimeMillis() / 1000 + 30)
        assertTrue(isExpired(token))
    }

    @Test
    fun `malformed token is treated as expired`() {
        assertTrue(isExpired("not.a.jwt"))
    }

    @Test
    fun `empty token is treated as expired`() {
        assertTrue(isExpired(""))
    }
}
