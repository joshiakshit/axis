package com.ash.axis.data.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface QrAttendanceApi {
    @POST("student/att/qrscan")
    suspend fun sendScanQR(
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("student/att/getattendanceqrtemp")
    suspend fun getAttendanceQrTemp(
        @Body body: RequestBody,
    ): Response<ResponseBody>
}
