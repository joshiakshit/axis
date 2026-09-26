package com.ash.axis.data.api

import com.ash.axis.domain.model.PersonalDetailsResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface UserApi {
    @POST("users/personaldetails")
    suspend fun getPersonalDetails(
        @Body body: Map<String, String>,
    ): PersonalDetailsResponse
}
