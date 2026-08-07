package com.ash.axis.data.repository

import android.util.Base64
import com.ash.axis.data.api.AuthApi
import com.ash.axis.domain.model.JwtPayload
import com.ash.axis.domain.model.UserInfo
import com.ash.core.security.TokenManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class LoginMethod(val apiValue: String) {
    PHONE("phone"),
    EMAIL("email"),
}

@Singleton
class AuthRepository
    @Inject
    constructor(
        private val authApi: AuthApi,
        private val tokenManager: TokenManager,
        private val json: Json,
    ) {
        private companion object {
            const val APP_VERSION = "3.0.3"
        }

        private val refreshMutex = Mutex()

        suspend fun requestOtp(
            contact: String,
            method: LoginMethod = LoginMethod.PHONE,
        ): String {
            val deviceId = getOrCreateDeviceId()
            val response =
                authApi.requestOtp(
                    mapOf(
                        "method" to method.apiValue,
                        "contact" to contact,
                        "lastmodifiedby" to contact,
                        "deviceid" to deviceId,
                        "appversion" to APP_VERSION,
                    ),
                )
            return response.data?.username ?: contact
        }

        suspend fun requestOtp(phone: String): String = requestOtp(phone, LoginMethod.PHONE)

        suspend fun validateOtp(
            contact: String,
            otp: String,
            username: String = contact,
        ): UserInfo {
            val deviceId = getOrCreateDeviceId()
            val response =
                authApi.validateOtp(
                    mapOf(
                        "otp" to otp,
                        "contact" to contact,
                        "username" to username,
                        "lastmodifiedby" to contact,
                        "deviceid" to deviceId,
                        "appversion" to APP_VERSION,
                    ),
                )

            val data = response.data
            val token =
                data?.token
                    // Surface the server's own message (e.g. the "another device" lock) instead of a generic error.
                    ?: error(data?.message?.takeIf { it.isNotBlank() } ?: "Login response did not include tokens")

            val userInfo = decodeUserInfo(token.accessToken)
            tokenManager.setActiveAdmno(userInfo.admno)
            tokenManager.saveTokens(token.accessToken, token.refreshToken)
            tokenManager.saveUserMeta(userInfo.email, userInfo.phoneNumber.ifBlank { contact })
            tokenManager.addAccount(admno = userInfo.admno, name = userInfo.name, email = userInfo.email)
            return userInfo
        }

        @Suppress("ThrowsCount")
        suspend fun refreshTokenIfNeeded(): String {
            val access = tokenManager.getAccessToken() ?: throw SessionExpiredException()
            if (!isExpired(access)) return access

            return refreshMutex.withLock {
                val current = tokenManager.getAccessToken() ?: throw SessionExpiredException()
                if (!isExpired(current)) {
                    current
                } else {
                    val refresh = tokenManager.getRefreshToken() ?: throw SessionExpiredException()
                    if (isExpired(refresh)) throw SessionExpiredException()

                    val response =
                        authApi.refreshToken(
                            mapOf(
                                "refreshtoken" to refresh,
                                "accesstoken" to current,
                                "lastmodifiedby" to (tokenManager.getEmail() ?: tokenManager.getPhone() ?: ""),
                            ),
                        )

                    val token = response.data?.token ?: throw SessionExpiredException()
                    tokenManager.saveTokens(token.accessToken, token.refreshToken)
                    token.accessToken
                }
            }
        }

        fun getOrCreateDeviceId(): String {
            tokenManager.getDeviceId()?.let { return it }
            val deviceId = UUID.randomUUID().toString()
            tokenManager.saveDeviceId(deviceId)
            return deviceId
        }

        // Override the persisted device id. Use to match the official iCloudEMS app's device id so both
        // stay bound to the same account (the server enforces one device per account). Survives logout.
        fun setDeviceId(deviceId: String) {
            val trimmed = deviceId.trim()
            if (trimmed.isNotBlank()) tokenManager.saveDeviceId(trimmed)
        }

        fun getUserInfo(): UserInfo? {
            val access = tokenManager.getAccessToken() ?: return null
            return try {
                decodeUserInfo(access)
            } catch (_: Exception) {
                null
            }
        }

        fun isLoggedIn(): Boolean = tokenManager.hasTokens()

        fun logout() {
            tokenManager.clearCurrentAccount()
        }

        private fun decodeUserInfo(accessToken: String): UserInfo {
            val parts = accessToken.split(".")
            require(parts.size >= 2) { "Invalid JWT" }
            val payload = String(Base64.decode(paddedBase64(parts[1]), Base64.URL_SAFE or Base64.NO_WRAP))
            val jwt = json.decodeFromString<JwtPayload>(payload)
            val admno = jwt.admno.ifBlank { jwt.preferredUsername }
            return UserInfo(
                admno = admno,
                brId = jwt.brId,
                name = jwt.name,
                email = jwt.email,
                phoneNumber = jwt.phoneNumber,
                clientId = jwt.clientId,
                preferredUsername = jwt.preferredUsername,
            )
        }

        private fun isExpired(token: String): Boolean {
            return try {
                val parts = token.split(".")
                if (parts.size < 2) return true
                val payload = String(Base64.decode(paddedBase64(parts[1]), Base64.URL_SAFE or Base64.NO_WRAP))
                val jwt = json.decodeFromString<JwtPayload>(payload)
                jwt.exp < (System.currentTimeMillis() / 1000) + 60
            } catch (_: Exception) {
                true
            }
        }

        private fun paddedBase64(value: String): String {
            val remainder = value.length % 4
            return if (remainder == 0) value else value + "=".repeat(4 - remainder)
        }
    }

class SessionExpiredException : Exception("Session expired")
