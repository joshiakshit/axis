package com.ash.axis.data.api

import com.ash.axis.data.session.AxisSession
import com.ash.axis.data.session.EventsRequest
import com.ash.axis.data.session.SessionRequest
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AxisBackendApi {
    @POST("v1/session")
    suspend fun session(
        @Body body: SessionRequest,
    ): AxisSession

    @POST("v1/events")
    suspend fun events(
        @Header("Authorization") auth: String,
        @Body body: EventsRequest,
    )
}
