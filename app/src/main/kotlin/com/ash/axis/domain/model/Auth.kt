package com.ash.axis.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenData(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class LoginResponse(
    val data: LoginResponseData? = null,
)

@Serializable
data class LoginResponseData(
    val token: TokenData? = null,
    val username: String? = null,
    val message: String? = null,
)

@Serializable
data class JwtPayload(
    val exp: Long = 0,
    val iat: Long = 0,
    val admno: String = "",
    @SerialName("br_id") val brId: Int = 0,
    val name: String = "",
    val email: String = "",
    @SerialName("phone_number") val phoneNumber: String = "",
    @SerialName("preferred_username") val preferredUsername: String = "",
    @SerialName("user_type") val userType: String = "",
    @SerialName("client_id") val clientId: String = "",
)

data class UserInfo(
    val admno: String,
    val brId: Int,
    val name: String,
    val email: String,
    val phoneNumber: String,
    val clientId: String = "",
    val preferredUsername: String = "",
)
