package com.ash.axis.data.repository

import com.ash.axis.data.academic.AcademicResource
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.api.ICloudEmsApi
import com.ash.axis.data.api.QrAttendanceApi
import com.ash.axis.data.db.CacheDao
import com.ash.axis.data.db.JsonCache
import com.ash.axis.domain.model.AcadYear
import com.ash.axis.domain.model.AttendanceResponse
import com.ash.axis.domain.model.ClassInfo
import com.ash.axis.domain.model.DaywiseResponse
import com.ash.axis.domain.model.QrScanResult
import com.ash.axis.domain.model.SemesterOption
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.axis.tenant.Tenants
import com.ash.core.storage.CachePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
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
        private val typedCache = JsonCache(cacheDao, json)
        private val academicCacheDao = cacheDao
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val summary =
            AcademicResource(
                scope,
                { key: AttendanceKey -> typedCache.read(key.cacheKey, AttendanceResponse.serializer(), CachePolicy.ATTENDANCE) },
                { key ->
                    requireActiveAccount(key.context)
                    fetchAttendance(key.context.admno, key.context.brId, key.classId, key.year, key.context.clientId).also {
                        requireActiveAccount(key.context)
                    }
                },
                { key, data -> typedCache.write(key.cacheKey, data, AttendanceResponse.serializer()) },
            )
        private val daily =
            AcademicResource(
                scope,
                { key: DaywiseKey -> typedCache.read(key.cacheKey, DaywiseResponse.serializer(), CachePolicy.DAYWISE) },
                { key ->
                    requireActiveAccount(key.context)
                    fetchDaywise(key.context.admno, key.context.brId, key.year, key.fromDate, key.toDate, key.context.clientId).also {
                        requireActiveAccount(key.context)
                    }
                },
                { key, data -> typedCache.write(key.cacheKey, data, DaywiseResponse.serializer()) },
            )

        suspend fun observeSummary(key: AttendanceKey): StateFlow<AcademicSnapshot<AttendanceResponse>> = summary.observe(key)

        suspend fun requestSummary(
            key: AttendanceKey,
            force: Boolean = false,
        ): StateFlow<AcademicSnapshot<AttendanceResponse>> = summary.request(key, force)

        suspend fun observeDaywise(key: DaywiseKey): StateFlow<AcademicSnapshot<DaywiseResponse>> = daily.observe(key)

        suspend fun requestDaywise(
            key: DaywiseKey,
            force: Boolean = false,
        ): StateFlow<AcademicSnapshot<DaywiseResponse>> = daily.request(key, force)

        suspend fun invalidateSummary(key: AttendanceKey) = summary.invalidate(key, clear = { academicCacheDao.delete(key.cacheKey) })

        suspend fun invalidateDaywise(key: DaywiseKey) = daily.invalidate(key, clear = { academicCacheDao.delete(key.cacheKey) })

        suspend fun invalidateDaywiseForAccount(context: StudentRequestContext) {
            val prefix = "v4_daywise_${context.admno}_${context.brId}_${context.clientId}_"
            daily.invalidateAll { academicCacheDao.deletePrefix(prefix) }
        }

        suspend fun deactivateAcademicData() {
            summary.deactivate()
            daily.deactivate()
        }

        private fun requireActiveAccount(context: StudentRequestContext) {
            val user = authRepository.getUserInfo()
            check(user != null && user.admno == context.admno && user.brId == context.brId && user.clientId == context.clientId) {
                "Academic request account changed"
            }
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
                            try {
                                val options =
                                    getClasses(admno, brId, year.id, forceRefresh)
                                        .filter { it.id.isNotBlank() }
                                        .map { classInfo ->
                                            SemesterOption(
                                                yearId = year.id,
                                                classId = classInfo.id,
                                                label = classInfo.displayLabel(year),
                                            )
                                        }
                                Result.success(options)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                Result.failure(error)
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
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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
                val attendance = fetchAttendance(admno, brId, classId, year)
                cacheStore.store(cacheKey, attendance, AttendanceResponse.serializer())
                attendance
            } catch (e: CancellationException) {
                throw e
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

            val daywise = fetchDaywise(admno, brId, year, fromDate, toDate)
            cacheStore.store(cacheKey, daywise, DaywiseResponse.serializer())
            return daywise
        }

        private suspend fun fetchAttendance(
            admno: String,
            brId: Int,
            classId: String,
            year: String,
            clientId: String = Tenants.GU.clientCode,
        ): AttendanceResponse {
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
                                    "client" to clientId,
                                    "branch_id" to brId,
                                    "year" to year,
                                    "curyear" to year,
                                    "classid" to classId,
                                ),
                            ),
                    ),
                )
            return json.decodeFromJsonElement(
                AttendanceResponse.serializer(),
                result.objectOrObjectValue("attendance", "Attendance", "data", "result"),
            )
        }

        private suspend fun fetchDaywise(
            admno: String,
            brId: Int,
            year: String,
            fromDate: String,
            toDate: String,
            clientId: String = Tenants.GU.clientCode,
        ): DaywiseResponse {
            authRepository.refreshTokenIfNeeded()
            val result =
                studentApi.parseStudentResponse(
                    studentApi.requireBody(
                        "getDaywiseAttendance",
                        api.postAttendance(
                            studentApi.jsonBody(
                                "from" to "app",
                                "method" to "GetDailyReport",
                                "admno" to admno,
                                "client" to clientId,
                                "branch_id" to brId,
                                "year" to year,
                                "from_date" to fromDate,
                                "to_date" to toDate,
                            ),
                        ),
                    ),
                )
            return json.decodeFromJsonElement(
                DaywiseResponse.serializer(),
                result.objectOrObjectValue("daywise", "Daywise", "data", "result"),
            )
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
            onSubmissionStart: () -> Unit = {},
        ): QrScanResult {
            val collegeId = clientId.ifBlank { Tenants.GU.id }
            authRepository.refreshTokenIfNeeded()
            val body =
                studentApi.jsonBody(
                    "data" to rawQr,
                    "usermasterkey" to admno,
                    "lastmodifiedby" to email,
                    "latitude" to latitude?.let { "'$it'" }.orEmpty(),
                    "longitude" to longitude?.let { "'$it'" }.orEmpty(),
                    "userselfie" to userSelfie,
                    "br_id" to brId,
                    "collegeid" to collegeId,
                )
            val startedAt = System.nanoTime()
            onSubmissionStart()
            val response = qrApi.sendScanQR(body)
            val durationMs = (System.nanoTime() - startedAt) / 1_000_000
            val raw =
                studentApi.requireBody(
                    endpoint = "sendScanQR",
                    response = response,
                ).string()

            return qrResultParser.parse(raw, defaultSuccess = true).copy(httpStatus = response.code(), httpDurationMs = durationMs)
        }

        suspend fun clearCache() {
            daily.deactivate()
            summary.clearCache { cacheStore.clear() }
        }
    }

data class AttendanceKey(val context: StudentRequestContext, val classId: String, val year: String) {
    val cacheKey: String = "v4_attendance_${context.admno}_${context.brId}_${context.clientId}_${year}_$classId"

    override fun equals(other: Any?): Boolean = other is AttendanceKey && cacheKey == other.cacheKey

    override fun hashCode(): Int = cacheKey.hashCode()
}

data class DaywiseKey(val context: StudentRequestContext, val year: String, val fromDate: String, val toDate: String) {
    val cacheKey: String = "v4_daywise_${context.admno}_${context.brId}_${context.clientId}_${year}_${fromDate}_$toDate"

    override fun equals(other: Any?): Boolean = other is DaywiseKey && cacheKey == other.cacheKey

    override fun hashCode(): Int = cacheKey.hashCode()
}
