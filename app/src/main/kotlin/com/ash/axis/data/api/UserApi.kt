package com.ash.axis.data.api

import com.ash.axis.domain.model.PersonalDetailsResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface UserApi {
    @POST("users/personaldetails")
    suspend fun getPersonalDetails(
        @Body body: Map<String, String>,
    ): PersonalDetailsResponse

    @POST("notifications/user/get")
    suspend fun getNotifications(
        @Body body: Map<String, String>,
    ): Response<ResponseBody>
}
