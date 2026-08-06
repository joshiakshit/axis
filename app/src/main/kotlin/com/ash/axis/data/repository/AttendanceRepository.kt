package com.ash.axis.data.repository

import com.ash.axis.data.api.ICloudEmsApi
import com.ash.axis.data.api.QrAttendanceApi
import com.ash.axis.data.db.CacheDao
import com.ash.axis.domain.model.AcadYear
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.ClassInfo
import com.ash.axis.domain.model.DaywiseResponse
import com.ash.axis.domain.model.QrScanResult
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.tenant.Tenants
import com.ash.core.storage.CachePolicy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("TopLevelPropertyNaming")
const val SELECTED_SEMESTER_YEAR_KEY = "selected_semester_year_id"

@Suppress("TopLevelPropertyNaming")
const val SELECTED_SEMESTER_CLASS_KEY = "selected_semester_class_id"

@Singleton
@Suppress("TooGenericExceptionCaught")
class AttendanceRepository
    @Inject
    constructor(
        private val api: ICloudEmsApi,
        private val qrApi: QrAttendanceApi,
        cacheDao: CacheDao,
        private val authRepository: AuthRepository,
        private val json: Json,
    ) {
        private val cacheStore = AttendanceCacheStore(cacheDao, json)
        private val studentApi = StudentApiParser(json)
        private val qrResultParser = QrScanResultParser(json)
        private val acadYearListSerializer = ListSerializer(AcadYear.serializer())
        private val classInfoListSerializer = ListSerializer(ClassInfo.serializer())

        suspend fun getLatestSemester(
            admno: String,
            brId: Int,
            forceRefresh: Boolean = false,
        ): SemesterOption {
            return getSemesterOptions(admno, brId, forceRefresh).firstOrNull()
                ?: error("No semester data found. Your classes may not be enrolled yet.")
        }

        suspend fun getPreferredSemester(
            admno: String,
            brId: Int,
            selectedYearId: String,
            selectedClassId: String,
            forceRefresh: Boolean = false,
        ): SemesterOption {
            val options = getSemesterOptions(admno, brId, forceRefresh)
            return options.firstOrNull { it.yearId == selectedYearId && it.classId == selectedClassId }
                ?: options.firstOrNull()
                ?: error("No semester data found. Your classes may not be enrolled yet.")
        }

        suspend fun getSemesterOptions(
            admno: String,
            brId: Int,
            forceRefresh: Boolean = false,
        ): List<SemesterOption> {
            val years = getAcadYears(admno, brId, forceRefresh)
            if (years.isEmpty()) error("No academic years found. Check your account or try again later.")

            val results =
                coroutineScope {
                    years.map { year ->
                        async {
                            runCatching {
                                getClasses(admno, brId, year.id, forceRefresh)
                                    .filter { it.id.isNotBlank() }
                                    .map { classInfo ->
                                        SemesterOption(
                                            yearId = year.id,
                                            classId = classInfo.id,
                                            label = classInfo.displayLabel(year),
                                        )
                                    }
                            }
                        }
                    }.awaitAll()
                }

            val options = results.mapNotNull { it.getOrNull() }.flatten()
            if (options.isEmpty()) {
                results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
            }

            return options.distinctBy { "${it.yearId}:${it.classId}" }
                .sortedWith(latestSemesterFirst())
        }

        suspend fun getAcadYears(
            admno: String,
            brId: Int,
            forceRefresh: Boolean = false,
        ): List<AcadYear> {
            val cacheKey = "v2_acad_years_$admno"
            if (!forceRefresh) {
                cacheStore.cached(cacheKey, CachePolicy.ATTENDANCE, acadYearListSerializer)?.let { return it }
            }

            return try {
                authRepository.refreshTokenIfNeeded()
                val result =
                    studentApi.parseStudentResponse(
                        studentApi.requireBody(
                            endpoint = "getAcadYears",
                            response =
                                api.postAttendance(
                                    studentApi.jsonBody(
                                        "from" to "app",
                                        "method" to "getAcadYear",
                                        "admno" to admno,
                                        "br_id" to brId,
                                        "client" to Tenants.GU.clientCode,
                                    ),
                                ),
                        ),
                    )

                val yearsJson = result.arrayOrObjectValue("AcadYears", "acadYears", "acad_years", "data")
                val years = json.decodeFromJsonElement(acadYearListSerializer, yearsJson)
                cacheStore.store(cacheKey, years, acadYearListSerializer)
                years
            } catch (e: Exception) {
                cacheStore.cachedAnyAge(cacheKey, acadYearListSerializer)
                    ?: throw e
            }
        }

        suspend fun getClasses(
            admno: String,
            brId: Int,
            year: String,
            forceRefresh: Boolean = false,
        ): List<ClassInfo> {
            val cacheKey = "v2_classes_${admno}_$year"
            if (!forceRefresh) {
                cacheStore.cached(cacheKey, CachePolicy.ATTENDANCE, classInfoListSerializer)?.let { return it }
            }

            return try {
                authRepository.refreshTokenIfNeeded()
                val result =
                    studentApi.parseStudentResponse(
                        studentApi.requireBody(
                            endpoint = "getClasses",
                            response =
                                api.postAttendance(
                                    studentApi.jsonBody(
                                        "from" to "app",
                                        "method" to "getclasses",
                                        "admno" to admno,
                                        "br_id" to brId,
                                        "client" to Tenants.GU.clientCode,
                                        "year" to year,
                                        "curyear" to year,
                                    ),
                                ),
                        ),
                    )

                val classesJson = result.arrayOrObjectValue("classes", "Classes", "class", "data")
                val classes = json.decodeFromJsonElement(classInfoListSerializer, classesJson)
                cacheStore.store(cacheKey, classes, classInfoListSerializer)
                classes
            } catch (e: Exception) {
                cacheStore.cachedAnyAge(cacheKey, classInfoListSerializer)
                    ?: throw e
            }
        }

        suspend fun getAttendance(
            admno: String,
            brId: Int,
            classId: String,
            year: String,
            forceRefresh: Boolean = false,
        ): AttendanceResponse {
            val cacheKey = "v2_attendance_${admno}_${classId}_$year"
            if (!forceRefresh) {
                cacheStore.cached(cacheKey, CachePolicy.ATTENDANCE, AttendanceResponse.serializer())?.let { return it }
            }

            return try {
                authRepository.refreshTokenIfNeeded()
                val result =
                    studentApi.parseStudentResponse(
                        studentApi.requireBody(
                            endpoint = "getAttendance",
                            response =
                                api.postAttendance(
                                    studentApi.jsonBody(
                                        "from" to "app",
                                        "method" to "GetCourseWiseReport",
                                        "admno" to admno,
                                        "client" to Tenants.GU.clientCode,
                                        "branch_id" to brId,
                                        "year" to year,
                                        "curyear" to year,
                                        "classid" to classId,
                                    ),
                                ),
                        ),
                    )

                val attendanceJson = result.objectOrObjectValue("attendance", "Attendance", "data", "result")
                val attendance = json.decodeFromJsonElement(AttendanceResponse.serializer(), attendanceJson)
                cacheStore.store(cacheKey, attendance, AttendanceResponse.serializer())
                attendance
            } catch (e: Exception) {
                cacheStore.cachedAnyAge(cacheKey, AttendanceResponse.serializer())
                    ?: throw e
            }
        }

        suspend fun getDaywiseAttendance(
            admno: String,
            brId: Int,
            year: String,
            fromDate: String,
            toDate: String,
            forceRefresh: Boolean = false,
        ): DaywiseResponse {
            val cacheKey = "v2_daywise_${admno}_${year}_${fromDate}_$toDate"
            if (!forceRefresh) {
                cacheStore.cached(cacheKey, CachePolicy.DAYWISE, DaywiseResponse.serializer())?.let { return it }
            }

            authRepository.refreshTokenIfNeeded()
            val result =
                studentApi.parseStudentResponse(
                    studentApi.requireBody(
                        endpoint = "getDaywiseAttendance",
                        response =
                            api.postAttendance(
                                studentApi.jsonBody(
                                    "from" to "app",
                                    "method" to "GetDailyReport",
                                    "admno" to admno,
                                    "client" to Tenants.GU.clientCode,
                                    "branch_id" to brId,
                                    "year" to year,
                                    "from_date" to fromDate,
                                    "to_date" to toDate,
                                ),
                            ),
                    ),
                )

            val daywiseJson = result.objectOrObjectValue("daywise", "Daywise", "data", "result")
            val daywise = json.decodeFromJsonElement(DaywiseResponse.serializer(), daywiseJson)
            cacheStore.store(cacheKey, daywise, DaywiseResponse.serializer())
            return daywise
        }

        suspend fun getAttendanceQrTemp(admno: String): QrScanResult {
            authRepository.refreshTokenIfNeeded()
            val raw =
                studentApi.requireBody(
                    endpoint = "getAttendanceQrTemp",
                    response =
                        qrApi.getAttendanceQrTemp(
                            studentApi.jsonBody(
                                "admno" to admno,
                            ),
                        ),
                ).string()

            return qrResultParser.parse(raw, defaultSuccess = true)
        }

        @Suppress("LongParameterList")
        suspend fun sendScanQR(
            rawQr: String,
            admno: String,
            email: String,
            brId: Int,
            latitude: Double?,
            longitude: Double?,
            userSelfie: String = "",
            clientId: String = "",
        ): QrScanResult {
            val collegeId = clientId.ifBlank { Tenants.GU.id }.uppercase()
            authRepository.refreshTokenIfNeeded()
            val raw =
                studentApi.requireBody(
                    endpoint = "sendScanQR",
                    response =
                        qrApi.sendScanQR(
                            studentApi.jsonBody(
                                "data" to rawQr,
                                "usermasterkey" to admno,
                                "lastmodifiedby" to email,
                                "latitude" to latitude?.let { "'$it'" }.orEmpty(),
                                "longitude" to longitude?.let { "'$it'" }.orEmpty(),
                                "userselfie" to userSelfie,
                                "br_id" to brId,
                                "collegeid" to collegeId,
                            ),
                        ),
                ).string()

            return qrResultParser.parse(raw, defaultSuccess = true)
        }

        suspend fun clearCache() {
            cacheStore.clear()
        }
    }
