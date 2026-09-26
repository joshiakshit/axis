package com.ash.axis.data.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ICloudEmsApi {
    @POST("corecampus/student/attendance/ctrl_myattendance.php")
    suspend fun postAttendance(
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("corecampus/student/schedulerand/ctrl_tt_report.php")
    suspend fun postTimetable(
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("corecampus/student/schedulerandV1/controller/ctrl_tt_report_data_v1.php")
    suspend fun postTimetableV1(
        @Body body: RequestBody,
    ): Response<ResponseBody>
}
