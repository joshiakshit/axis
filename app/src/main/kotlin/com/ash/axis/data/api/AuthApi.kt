package com.ash.axis.data.api

import com.ash.axis.domain.model.LoginResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("users/login")
    suspend fun requestOtp(
        @Body body: Map<String, String>,
    ): LoginResponse

    @POST("users/login/validate")
    suspend fun validateOtp(
        @Body body: Map<String, String>,
    ): LoginResponse

    @POST("users/login/refresh")
    suspend fun refreshToken(
        @Body body: Map<String, String>,
    ): LoginResponse

    @POST("calendar/getholiday")
    suspend fun getHolidays(
        @Body body: Map<String, String>,
    ): Response<ResponseBody>

    @POST("calendar/getevent")
    suspend fun getEvents(
        @Body body: Map<String, String>,
    ): Response<ResponseBody>
}
