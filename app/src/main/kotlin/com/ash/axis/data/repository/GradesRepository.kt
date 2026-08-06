package com.ash.axis.data.repository

import com.ash.axis.data.api.ICloudEmsApi
import com.ash.axis.data.db.CacheDao
import com.ash.axis.data.db.CacheEntity
import com.ash.axis.domain.model.CourseMarks
import com.ash.axis.domain.model.ExamSession
import com.ash.axis.domain.model.GradesData
import com.ash.axis.domain.model.PerformanceData
import com.ash.axis.domain.model.PerformanceOption
import com.ash.axis.domain.model.PerformanceSetup
import com.ash.axis.tenant.Tenants
import com.ash.core.storage.CacheFreshness
import com.ash.core.storage.CachePolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
                cachedStringList(cacheKey)?.let {
                    return SemesterResult(semesters = it, maxSemFromClasses = null)
                }
            }

            authRepository.refreshTokenIfNeeded()
            val body =
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("from", "app")
                    .addFormDataPart("method", "showSemester")
                    .addFormDataPart("user_id", admno)
                    .addFormDataPart("br_id", brId.toString())
                    .addFormDataPart("client", Tenants.GU.clientCode)
                    .addFormDataPart("student_class", classId)
                    .addFormDataPart("current_acad_yr", academicYear)
                    .addFormDataPart("source", "")
                    .addFormDataPart("admnum", admno)
                    .build()

            val response = api.postReportCardController(body)
            val result = parseResponse("showSemester", requireBody("reportCardController/showSemester", response))
            val obj = result.jsonObject
            val semesters = parseSemesterNumbers(obj)
            val maxSem = parseMaxSemFromClasses(obj)
            storeStringList(cacheKey, semesters)
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
                cachedSessions(cacheKey)?.let { return it }
            }

            authRepository.refreshTokenIfNeeded()
            val body =
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("from", "app")
                    .addFormDataPart("method", "showSession")
                    .addFormDataPart("user_id", admno)
                    .addFormDataPart("br_id", brId.toString())
                    .addFormDataPart("client", Tenants.GU.clientCode)
                    .addFormDataPart("semesterNumeric", semesterNumeric)
                    .addFormDataPart("source", "")
                    .addFormDataPart("admnum", admno)
                    .build()

            val response = api.postReportCardController(body)
            val result = parseResponse("showSession", requireBody("reportCardController/showSession", response))
            val obj = result.jsonObject
            return parseExamSessions(obj).also { sessions ->
                storeSessions(cacheKey, sessions)
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
                cached(cacheKey)?.let { return it }
            }

            return try {
                authRepository.refreshTokenIfNeeded()
                val bodyBuilder =
                    MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("from", "app")
                        .addFormDataPart("method", "SubmitForm")
                        .addFormDataPart("user_id", admno)
                        .addFormDataPart("br_id", brId.toString())
                        .addFormDataPart("client", Tenants.GU.clientCode)
                        .addFormDataPart("exam_session", sessionId)
                        .addFormDataPart("source", "")
                        .addFormDataPart("admnum", admno)

                semesters.forEach { sem ->
                    bodyBuilder.addFormDataPart("semesterNumeric[]", sem)
                }

                val response = api.postReportCardController(bodyBuilder.build())
                val result = parseResponse("SubmitForm", requireBody("reportCardController/SubmitForm", response))
                val data = parseGradesData(result)
                store(cacheKey, data)
                data
            } catch (e: Exception) {
                cachedAnyAge(cacheKey) ?: throw e
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
                cachedPerformance(cacheKey)?.let { return it.courses }
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
            storePerformance(cacheKey, data)
            return data.courses
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

        private suspend fun cached(key: String): GradesData? {
            val entry = cacheDao.get(key) ?: return null
            if (CachePolicy.ATTENDANCE.evaluate(entry.cachedAt) == CacheFreshness.EXPIRED) return null
            return try {
                json.decodeFromString(GradesData.serializer(), entry.data)
            } catch (_: Exception) {
                null
            }
        }

        private suspend fun cachedAnyAge(key: String): GradesData? {
            val entry = cacheDao.get(key) ?: return null
            return try {
                json.decodeFromString(GradesData.serializer(), entry.data)
            } catch (_: Exception) {
                null
            }
        }

        private suspend fun store(
            key: String,
            data: GradesData,
        ) {
            cacheDao.put(
                CacheEntity(
                    key = key,
                    data = json.encodeToString(GradesData.serializer(), data),
                    cachedAt = System.currentTimeMillis(),
                ),
            )
        }

        private suspend fun cachedSessions(key: String): List<ExamSession>? {
            val entry = cacheDao.get(key) ?: return null
            if (CachePolicy.ATTENDANCE.evaluate(entry.cachedAt) == CacheFreshness.EXPIRED) return null
            return try {
                json.decodeFromString(sessionsSerializer, entry.data)
            } catch (_: Exception) {
                null
            }
        }

        private suspend fun storeSessions(
            key: String,
            sessions: List<ExamSession>,
        ) {
            cacheDao.put(
                CacheEntity(
                    key = key,
                    data = json.encodeToString(sessionsSerializer, sessions),
                    cachedAt = System.currentTimeMillis(),
                ),
            )
        }

        private suspend fun cachedStringList(key: String): List<String>? {
            val entry = cacheDao.get(key) ?: return null
            if (CachePolicy.ATTENDANCE.evaluate(entry.cachedAt) == CacheFreshness.EXPIRED) return null
            return try {
                json.decodeFromString(stringListSerializer, entry.data)
            } catch (_: Exception) {
                null
            }
        }

        private suspend fun storeStringList(
            key: String,
            list: List<String>,
        ) {
            cacheDao.put(
                CacheEntity(
                    key = key,
                    data = json.encodeToString(stringListSerializer, list),
                    cachedAt = System.currentTimeMillis(),
                ),
            )
        }

        private suspend fun cachedPerformance(key: String): PerformanceData? {
            val entry = cacheDao.get(key) ?: return null
            if (CachePolicy.ATTENDANCE.evaluate(entry.cachedAt) == CacheFreshness.EXPIRED) return null
            return try {
                json.decodeFromString(PerformanceData.serializer(), entry.data)
            } catch (_: Exception) {
                null
            }
        }

        private suspend fun storePerformance(
            key: String,
            data: PerformanceData,
        ) {
            cacheDao.put(
                CacheEntity(
                    key = key,
                    data = json.encodeToString(PerformanceData.serializer(), data),
                    cachedAt = System.currentTimeMillis(),
                ),
            )
        }

        private companion object {
            val sessionsSerializer =
                kotlinx.serialization.builtins.ListSerializer(ExamSession.serializer())
            val stringListSerializer =
                kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>())
        }
    }
