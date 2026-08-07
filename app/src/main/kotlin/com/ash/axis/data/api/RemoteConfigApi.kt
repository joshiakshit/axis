package com.ash.axis.data.api

import com.ash.axis.data.config.RemoteConfig
import retrofit2.http.GET

interface RemoteConfigApi {
    @GET("v1/config")
    suspend fun getConfig(): RemoteConfig
}
