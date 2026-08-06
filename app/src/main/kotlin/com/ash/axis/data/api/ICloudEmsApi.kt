package com.ash.axis.data.api

import kotlinx.serialization.json.JsonObject
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
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

    @POST("corecampus/student/grades/new/save/myreportcardSave.php")
    suspend fun postGrades(
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("corecampus/student/grades/new/save/myreportcardController.php")
    suspend fun postReportCardController(
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("corecampus/admin/reports/new/save/cutlist_marksheetSave.php")
    suspend fun postPrintReportCard(
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json",
        "Referer: gustudentapp.icloudems.com",
    )
    @POST("api/main.php")
    suspend fun postApiMain(
        @Body body: JsonObject,
    ): Response<ResponseBody>
}
