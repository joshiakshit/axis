package com.ash.axis.data.repository

import com.ash.axis.data.api.AuthApi
import com.ash.axis.data.api.UserApi
import com.ash.axis.data.config.RemoteConfigRepository
import com.ash.axis.data.device.DeviceIdProvider
import com.ash.axis.domain.model.JwtPayload
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.axis.domain.model.UserInfo
import com.ash.core.security.TokenManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.time.Duration
import java.util.Base64
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
        private val userApi: UserApi,
        private val tokenManager: TokenManager,
        private val deviceIdProvider: DeviceIdProvider,
        private val remoteConfig: RemoteConfigRepository,
        private val json: Json,
    ) {
        private val refreshMutex = Mutex()
        private val profileMutex = Mutex()

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
                        "appversion" to remoteConfig.appVersion(),
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
                        "appversion" to remoteConfig.appVersion(),
                    ),
                )

            val data = response.data
            val token =
                data?.token
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
            return deviceIdProvider.get()
        }

        fun getUserInfo(): UserInfo? {
            val access = tokenManager.getAccessToken() ?: return null
            return try {
                decodeUserInfo(access).copy(academicYear = tokenManager.getAcademicYear().orEmpty())
            } catch (_: Exception) {
                null
            }
        }

        suspend fun requireStudentRequestContext(forceProfileRefresh: Boolean = false): StudentRequestContext {
            val user = getUserInfo() ?: error("Not logged in")
            if (user.clientId.isBlank()) error("Session did not include a client ID")
            val observedFetchTime = tokenManager.getAcademicYearFetchedAt()
            val academicYear =
                if (!forceProfileRefresh && isProfileFresh(user.academicYear, observedFetchTime)) {
                    user.academicYear
                } else {
                    profileMutex.withLock {
                        val currentUser = getUserInfo() ?: error("Not logged in")
                        val cachedYear = currentUser.academicYear
                        val cachedAt = tokenManager.getAcademicYearFetchedAt()
                        val refreshedByAnotherRequest =
                            forceProfileRefresh &&
                                cachedAt > observedFetchTime &&
                                cachedYear.isNotBlank()
                        when {
                            refreshedByAnotherRequest -> cachedYear
                            !forceProfileRefresh && isProfileFresh(cachedYear, cachedAt) -> cachedYear
                            else -> fetchAcademicYear(currentUser, forceProfileRefresh, cachedYear)
                        }
                    }
                }

            return StudentRequestContext(
                admno = user.admno,
                brId = user.brId,
                clientId = user.clientId,
                academicYear = academicYear,
            )
        }

        fun isLoggedIn(): Boolean = tokenManager.hasTokens()

        fun logout() {
            tokenManager.clearCurrentAccount()
        }

        private fun decodeUserInfo(accessToken: String): UserInfo {
            val parts = accessToken.split(".")
            require(parts.size >= 2) { "Invalid JWT" }
            val payload = String(Base64.getUrlDecoder().decode(paddedBase64(parts[1])))
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
                userType = jwt.userType,
            )
        }

        private fun isExpired(token: String): Boolean {
            return try {
                val parts = token.split(".")
                if (parts.size < 2) return true
                val payload = String(Base64.getUrlDecoder().decode(paddedBase64(parts[1])))
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

        @Suppress("TooGenericExceptionCaught")
        private suspend fun fetchAcademicYear(
            user: UserInfo,
            forceProfileRefresh: Boolean,
            cachedYear: String,
        ): String {
            return try {
                refreshTokenIfNeeded()
                val academicYear =
                    userApi.getPersonalDetails(
                        mapOf(
                            "code" to user.admno,
                            "type" to user.userType,
                        ),
                    ).data?.result?.academicYear.orEmpty()
                if (academicYear.isBlank()) error("Personal details did not include an academic year")
                tokenManager.saveAcademicYear(academicYear, System.currentTimeMillis())
                academicYear
            } catch (e: Exception) {
                if (!forceProfileRefresh && cachedYear.isNotBlank()) cachedYear else throw e
            }
        }

        private fun isProfileFresh(
            academicYear: String,
            fetchedAt: Long,
        ): Boolean =
            academicYear.isNotBlank() &&
                fetchedAt > 0L &&
                System.currentTimeMillis() - fetchedAt <= PROFILE_TTL_MS

        private companion object {
            val PROFILE_TTL_MS: Long = Duration.ofHours(24).toMillis()
        }
    }

class SessionExpiredException : Exception("Session expired")
