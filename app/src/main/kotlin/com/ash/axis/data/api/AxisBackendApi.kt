package com.ash.axis.data.api

import com.ash.axis.data.config.RemoteConfig
import com.ash.axis.data.session.AdminUser
import com.ash.axis.data.session.ApproveAllResponse
import com.ash.axis.data.session.AxisSession
import com.ash.axis.data.session.ConfigPatch
import com.ash.axis.data.session.EventsRequest
import com.ash.axis.data.session.HealthResponse
import com.ash.axis.data.session.SessionRequest
import com.ash.axis.data.session.UsersResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
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

    @GET("v1/admin/config")
    suspend fun getConfig(
        @Header("Authorization") auth: String,
    ): RemoteConfig

    @PUT("v1/admin/config")
    suspend fun putConfig(
        @Header("Authorization") auth: String,
        @Body patch: ConfigPatch,
    ): RemoteConfig

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

    @POST("v1/admin/users/{admno}/ban")
    suspend fun ban(
        @Path("admno") admno: String,
        @Header("Authorization") auth: String,
    ): AdminUser

    @POST("v1/admin/approve-all")
    suspend fun approveAll(
        @Header("Authorization") auth: String,
    ): ApproveAllResponse

    @GET("v1/admin/health")
    suspend fun health(
        @Header("Authorization") auth: String,
    ): HealthResponse

    @POST("v1/events")
    suspend fun events(
        @Header("Authorization") auth: String,
        @Body body: EventsRequest,
    )
}
