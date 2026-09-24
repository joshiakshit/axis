package com.ash.axis.data.repository

import com.ash.axis.data.api.ICloudEmsApi
import com.ash.axis.data.db.CacheDao
import com.ash.axis.data.db.JsonCache
import com.ash.axis.domain.model.AdmitCardEntry
import com.ash.axis.domain.model.CourseMarks
import com.ash.axis.domain.model.ExamSession
import com.ash.axis.domain.model.GradesData
import com.ash.axis.domain.model.PerformanceData
import com.ash.axis.domain.model.PerformanceOption
import com.ash.axis.domain.model.PerformanceSetup
import com.ash.axis.tenant.Tenants
import com.ash.core.storage.CachePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooGenericExceptionCaught", "LargeClass", "TooManyFunctions")
class GradesRepository
    @Inject
    constructor(
        private val api: ICloudEmsApi,
        private val cacheDao: CacheDao,
        private val authRepository: AuthRepository,
        private val json: Json,
    ) {
        private val cache = JsonCache(cacheDao, json)

        data class SemesterResult(
            val semesters: List<String>,
            val maxSemFromClasses: Int?,
        )

        private data class PerformanceContext(
            val academicYears: List<com.ash.axis.domain.model.PerformanceAcadYear>,
            val semester: String,
        )

        suspend fun getSemesters(
            admno: String,
            brId: Int,
            classId: String,
            academicYear: String,
            forceRefresh: Boolean = false,
        ): SemesterResult {
            val cacheKey = "v2_grade_semesters_${admno}_${classId}_$academicYear"
            if (!forceRefresh) {
                cache.readAccepted(cacheKey, stringListSerializer, CachePolicy.ATTENDANCE)?.data?.let {
                    return SemesterResult(semesters = it, maxSemFromClasses = null)
                }
            }

            authRepository.refreshTokenIfNeeded()
            val body =
                reportCardForm(admno, brId, "showSemester")
                    .addFormDataPart("student_class", classId)
                    .addFormDataPart("current_acad_yr", academicYear)
                    .build()

            val response = api.postReportCardController(body)
            val result = parseResponse("showSemester", requireBody("reportCardController/showSemester", response))
            val obj = result.jsonObject
            val semesters = parseSemesterNumbers(obj)
            val maxSem = parseMaxSemFromClasses(obj)
            cache.write(cacheKey, semesters, stringListSerializer)
            return SemesterResult(semesters = semesters, maxSemFromClasses = maxSem)
        }

        suspend fun getSessions(
            admno: String,
            brId: Int,
            semesterNumeric: String,
            forceRefresh: Boolean = false,
        ): List<ExamSession> {
            val cacheKey = "v2_grade_sessions_${admno}_$semesterNumeric"
            if (!forceRefresh) {
                cache.readAccepted(cacheKey, sessionsSerializer, CachePolicy.ATTENDANCE)?.data?.let { return it }
            }

            authRepository.refreshTokenIfNeeded()
            val body =
                reportCardForm(admno, brId, "showSession")
                    .addFormDataPart("semesterNumeric", semesterNumeric)
                    .build()

            val response = api.postReportCardController(body)
            val result = parseResponse("showSession", requireBody("reportCardController/showSession", response))
            val obj = result.jsonObject
            return parseExamSessions(obj).also { sessions ->
                cache.write(cacheKey, sessions, sessionsSerializer)
            }
        }

        suspend fun getGrades(
            admno: String,
            brId: Int,
            sessionId: String,
            semesters: List<String>,
            forceRefresh: Boolean = false,
        ): GradesData {
            val cacheKey = "v2_grades_${admno}_${sessionId}_${semesters.joinToString(",")}"
            if (!forceRefresh) {
                cache.readAccepted(cacheKey, GradesData.serializer(), CachePolicy.ATTENDANCE)?.data?.let { return it }
            }

            return try {
                authRepository.refreshTokenIfNeeded()
                val bodyBuilder = reportCardForm(admno, brId, "SubmitForm").addFormDataPart("exam_session", sessionId)

                semesters.forEach { sem ->
                    bodyBuilder.addFormDataPart("semesterNumeric[]", sem)
                }

                val response = api.postReportCardController(bodyBuilder.build())
                val result = parseResponse("SubmitForm", requireBody("reportCardController/SubmitForm", response))
                val data = parseGradesData(result)
                cache.write(cacheKey, data, GradesData.serializer())
                data
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cache.read(cacheKey, GradesData.serializer(), CachePolicy.ATTENDANCE)?.data ?: throw e
            }
        }

        @Suppress("LongMethod")
        suspend fun getReportCardPdf(
            admno: String,
            brId: Int,
            marksheetType: String,
            subExamTypeAA: String,
            classId: String,
            acadYear: String,
        ): ByteArray {
            authRepository.refreshTokenIfNeeded()
            val body =
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("from", "app")
                    .addFormDataPart("action", "searchNow")
                    .addFormDataPart("marksheet_type", marksheetType)
                    .addFormDataPart("this_type", "byStudLogin")
                    .addFormDataPart("sub_exam_typeAA", subExamTypeAA)
                    .addFormDataPart("class_id", classId)
                    .addFormDataPart("acad_year", acadYear)
                    .addFormDataPart("admno", admno)
                    .addFormDataPart("br_id", brId.toString())
                    .addFormDataPart("client", Tenants.GU.clientCode)
                    .addFormDataPart("user_id", admno)
                    .addFormDataPart("admnum", admno)
                    .build()

            val response = api.postPrintReportCard(body)
            val bytes = requireBody("printReportCard", response).bytes()
            return bytes
        }

        suspend fun getPerformanceSetup(
            admno: String,
            brId: Int,
        ): PerformanceSetup {
            val context = getPerformanceContext(admno, brId)
            return PerformanceSetup(academicYears = context.academicYears, semester = context.semester)
        }

        suspend fun getPerformanceSessions(
            admno: String,
            brId: Int,
            academicYear: String,
        ): List<PerformanceOption> {
            val result =
                postPerformanceForm(
                    tag = "perfSessions",
                    admno = admno,
                    brId = brId,
                    action = "allExamSession",
                    extras = mapOf("acad_year" to academicYear),
                )
            return parseOptions(result.jsonObject["getMyExamSession"]).map { it.toPerformanceOption() }
        }

        suspend fun getPerformanceClasses(
            admno: String,
            brId: Int,
            academicYear: String,
            examSession: String,
        ): List<PerformanceOption> {
            val result =
                postPerformanceForm(
                    tag = "perfClasses",
                    admno = admno,
                    brId = brId,
                    action = "getClass",
                    extras =
                        mapOf(
                            "acad_year" to academicYear,
                            "exam_session" to examSession,
                        ),
                )
            return parsePerformanceClasses(result.jsonObject["getMyClass"]).map { it.toPerformanceOption() }
        }

        suspend fun getPerformanceDivisions(
            admno: String,
            brId: Int,
            classId: String,
        ): List<PerformanceOption> {
            val result =
                postPerformanceForm(
                    tag = "perfDivisions",
                    admno = admno,
                    brId = brId,
                    action = "getDivision",
                    extras = mapOf("class_id" to classId),
                )
            return parseOptions(result.jsonObject["getMyDevision"]).map { it.toPerformanceOption() }
        }

        suspend fun getPerformanceExams(
            admno: String,
            brId: Int,
            academicYear: String,
            classId: String,
            division: String,
        ): List<PerformanceOption> {
            val result =
                postPerformanceForm(
                    tag = "perfExamNames",
                    admno = admno,
                    brId = brId,
                    action = "getMyExam",
                    extras =
                        mapOf(
                            "class_id" to classId,
                            "acad_year" to academicYear,
                            "division" to division,
                        ),
                )
            return parseOptions(result.jsonObject["datalist"]).map { it.toPerformanceOption() }
        }

        @Suppress("LongParameterList")
        suspend fun getPerformanceMarks(
            admno: String,
            brId: Int,
            academicYear: String,
            semester: String,
            examSession: String,
            classId: String,
            division: String,
            examIds: List<String>,
            forceRefresh: Boolean = false,
        ): List<CourseMarks> {
            if (examIds.isEmpty()) return emptyList()
            val cacheKey =
                "v5_perf_marks_${admno}_${academicYear}_${semester}_${examSession}_${classId}_${division}_${examIds.joinToString("-")}"
            if (!forceRefresh) {
                cache.readAccepted(cacheKey, PerformanceData.serializer(), CachePolicy.ATTENDANCE)?.data?.let {
                    return it.courses
                }
            }

            val result =
                postPerformanceForm(
                    tag = "perfMarks",
                    admno = admno,
                    brId = brId,
                    action = "searchNow",
                    extras =
                        mapOf(
                            "class_id" to classId,
                            "acad_year" to academicYear,
                            "div_id" to division,
                            "exam_session" to examSession,
                            "exam_id[]" to examIds.joinToString(","),
                            "result_with_scheme" to "0",
                        ),
                )
            val data = PerformanceData(courses = parseAcademicPerformanceMarks(result))
            cache.write(cacheKey, data, PerformanceData.serializer())
            return data.courses
        }

        suspend fun getAdmitCard(
            admno: String,
            brId: Int,
            forceRefresh: Boolean = false,
        ): List<AdmitCardEntry> {
            val cacheKey = "v1_admit_card_$admno"
            if (!forceRefresh) {
                cache.readAccepted(cacheKey, admitCardSerializer, CachePolicy.ATTENDANCE)?.data?.let { return it }
            }

            authRepository.refreshTokenIfNeeded()
            val body =
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("from", "app")
                    .addFormDataPart("method", "index")
                    .addFormDataPart("user_id", admno)
                    .addFormDataPart("br_id", brId.toString())
                    .addFormDataPart("client", Tenants.GU.clientCode)
                    .addFormDataPart("admnum", admno)
                    .build()

            return try {
                val response = api.postAdmitCard(body)
                val result = parseResponse("admitCard", requireBody("admitCard", response))
                val entries = parseAdmitCardEntries(result)
                cache.write(cacheKey, entries, admitCardSerializer)
                entries
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cache.read(cacheKey, admitCardSerializer, CachePolicy.ATTENDANCE)?.data ?: throw e
            }
        }

        private fun parseAdmitCardEntries(element: JsonElement): List<AdmitCardEntry> {
            val array =
                when (element) {
                    is JsonArray -> element
                    is JsonObject -> element.firstArray("data", "datalist", "result", "admit_card") ?: return emptyList()
                    else -> return emptyList()
                }
            return array.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                AdmitCardEntry(
                    date = obj.firstString("exam_date", "date"),
                    subjectName = obj.firstString("subject_name", "sub_name", "subjectName"),
                    subjectCode = obj.firstString("subject_code", "sub_code", "subjectCode"),
                    fromTime = obj.firstString("from_time", "fromTime", "start_time"),
                    toTime = obj.firstString("to_time", "toTime", "end_time"),
                    room = obj.firstString("room_no", "room"),
                    seat = obj.firstString("seat_no", "seat"),
                )
            }
        }

        private fun reportCardForm(
            admno: String,
            brId: Int,
            method: String,
        ) = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("from", "app")
            .addFormDataPart("method", method)
            .addFormDataPart("user_id", admno)
            .addFormDataPart("br_id", brId.toString())
            .addFormDataPart("client", Tenants.GU.clientCode)
            .addFormDataPart("source", "")
            .addFormDataPart("admnum", admno)

        // Field names vary across PHP backend releases.
        private fun JsonObject.firstString(vararg keys: String): String {
            keys.forEach { key ->
                (this[key] as? JsonPrimitive)?.contentOrNull?.let { return it }
            }
            return ""
        }

        private fun JsonObject.firstArray(vararg keys: String): JsonArray? {
            keys.forEach { key ->
                (this[key] as? JsonArray)?.let { return it }
            }
            return null
        }

        private fun SelectionOption.toPerformanceOption() = PerformanceOption(id = id, label = label)

        private suspend fun getPerformanceContext(
            admno: String,
            brId: Int,
        ): PerformanceContext {
            val result =
                postPerformanceForm(
                    tag = "perfContext",
                    admno = admno,
                    brId = brId,
                    action = "index",
                    extras = mapOf("branch_id" to brId.toString()),
                )
            val years = parsePerformanceAcadYears(result.jsonObject)
            return PerformanceContext(academicYears = years, semester = "")
        }

        private suspend fun postPerformanceForm(
            tag: String,
            admno: String,
            brId: Int,
            action: String,
            extras: Map<String, String> = emptyMap(),
        ): JsonElement {
            authRepository.refreshTokenIfNeeded()
            val builder =
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("action", action)
                    .addFormDataPart("admnum", admno)
                    .addFormDataPart("from", "app")
                    .addFormDataPart("client", Tenants.GU.clientCode)

            if ("branch_id" !in extras) {
                builder.addFormDataPart("br_id", brId.toString())
            }
            extras.filterValues { it.isNotBlank() }.forEach { (key, value) ->
                if (key.endsWith("[]") && "," in value) {
                    value.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach {
                        builder.addFormDataPart(key, it)
                    }
                } else {
                    builder.addFormDataPart(key, value)
                }
            }

            return parseResponse(tag, requireBody(tag, api.postGrades(builder.build())))
        }

        private fun parseResponse(
            tag: String,
            body: ResponseBody,
        ): JsonElement {
            val raw = body.string().trim()
            val jsonStart =
                listOf(raw.indexOf('{'), raw.indexOf('['))
                    .filter { it >= 0 }
                    .minOrNull()
                    ?: throw IcloudServerException(message = "[$tag] Grades API returned a non-JSON response")
            val element = json.parseToJsonElement(raw.substring(jsonStart))
            if (element is JsonObject) {
                val status = element["status"]?.jsonPrimitive?.contentOrNull
                if (status == "false" || status == "fail") {
                    val msg =
                        element["message"]?.jsonPrimitive?.contentOrNull
                            ?: element["msg"]?.jsonPrimitive?.contentOrNull
                            ?: "Server returned status=false"
                    throw IcloudServerException(message = msg)
                }
            }
            return element
        }

        private fun requireBody(
            endpoint: String,
            response: Response<ResponseBody>,
        ): ResponseBody {
            if (response.isSuccessful) {
                return response.body() ?: error("$endpoint failed: empty response body")
            }
            val code = response.code()
            val errorBody = response.errorBody()?.string()?.trim()?.take(200)
            if (code == 401) throw SessionExpiredException()
            throw IcloudServerException(code, "$endpoint failed: HTTP $code${errorBody?.let { ": $it" }.orEmpty()}")
        }

        private companion object {
            val sessionsSerializer =
                kotlinx.serialization.builtins.ListSerializer(ExamSession.serializer())
            val stringListSerializer =
                kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>())
            val admitCardSerializer =
                kotlinx.serialization.builtins.ListSerializer(AdmitCardEntry.serializer())
        }
    }
