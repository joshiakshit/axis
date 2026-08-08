package com.ash.axis.data.api

import com.ash.axis.data.session.AdminUser
import com.ash.axis.data.session.AxisSession
import com.ash.axis.data.session.SessionRequest
import com.ash.axis.data.session.UsersResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface AxisBackendApi {
    @POST("v1/session")
    suspend fun session(
        @Body body: SessionRequest,
    ): AxisSession

    @GET("v1/admin/users")
    suspend fun listUsers(
        @Header("Authorization") auth: String,
    ): UsersResponse

    @POST("v1/admin/users/{admno}/allow")
    suspend fun allow(
        @Path("admno") admno: String,
        @Header("Authorization") auth: String,
    ): AdminUser

    @POST("v1/admin/users/{admno}/kick")
    suspend fun kick(
        @Path("admno") admno: String,
        @Header("Authorization") auth: String,
    ): AdminUser
}
