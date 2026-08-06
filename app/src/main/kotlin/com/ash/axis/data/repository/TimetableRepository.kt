package com.ash.axis.data.repository

import com.ash.axis.data.api.ICloudEmsApi
import com.ash.axis.data.db.CacheDao
import com.ash.axis.domain.model.TimetableSlot
import com.ash.axis.tenant.Tenants
import com.ash.core.storage.CachePolicy
import com.ash.core.storage.CachedResult
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooGenericExceptionCaught")
class TimetableRepository
    @Inject
    constructor(
        private val api: ICloudEmsApi,
        cacheDao: CacheDao,
        private val authRepository: AuthRepository,
        json: Json,
    ) {
        private val cacheStore = TimetableCacheStore(cacheDao, json)
        private val studentApi = StudentApiParser(json)
        private val normalizer = TimetableNormalizer(json)

        private fun timetableKey(
            admno: String,
            startDate: String,
            endDate: String,
        ) = "v2_timetable_${admno}_${startDate}_$endDate"

        suspend fun peekTimetable(
            admno: String,
            startDate: String,
            endDate: String,
        ): CachedResult<Map<String, List<TimetableSlot>>>? {
            return cacheStore.peek(timetableKey(admno, startDate, endDate))
        }

        suspend fun getTimetable(
            admno: String,
            brId: Int,
            acadYear: String,
            startDate: String,
            endDate: String,
            forceRefresh: Boolean = false,
        ): Map<String, List<TimetableSlot>> {
            val cacheKey = timetableKey(admno, startDate, endDate)
            if (!forceRefresh) {
                cacheStore.cached(cacheKey, CachePolicy.TIMETABLE)?.let { return it }
            }

            return try {
                val result = fetchTimetableResponse(admno, brId, acadYear, startDate, endDate)
                val timetable = normalizer.normalizeTimetable(result)
                cacheStore.store(cacheKey, timetable)
                timetable
            } catch (e: Exception) {
                cacheStore.cachedAnyAge(cacheKey) ?: throw e
            }
        }

        suspend fun getDateKeyedTimetable(
            admno: String,
            brId: Int,
            acadYear: String,
            startDate: String,
            endDate: String,
            forceRefresh: Boolean = false,
        ): Map<LocalDate, List<TimetableSlot>> {
            val cacheKey = "v2_timetable_dated_${admno}_${startDate}_$endDate"
            if (!forceRefresh) {
                cacheStore.cachedDateKeyed(cacheKey, CachePolicy.TIMETABLE)?.let { return it }
            }

            return try {
                val result = fetchTimetableResponse(admno, brId, acadYear, startDate, endDate)
                val timetable = normalizer.normalizeDateKeyed(result, LocalDate.parse(startDate), LocalDate.parse(endDate))
                cacheStore.storeDateKeyed(cacheKey, timetable)
                timetable
            } catch (e: Exception) {
                cacheStore.cachedDateKeyedAnyAge(cacheKey) ?: throw e
            }
        }

        suspend fun clearCache() {
            cacheStore.clear()
        }

        private suspend fun fetchTimetableResponse(
            admno: String,
            brId: Int,
            acadYear: String,
            startDate: String,
            endDate: String,
        ) = studentApi.parseStudentResponse(
            studentApi.requireBody(
                endpoint = "getTimetable",
                response =
                    run {
                        authRepository.refreshTokenIfNeeded()
                        api.postTimetable(
                            studentApi.jsonBody(
                                "from" to "app",
                                "empid" to "",
                                "action" to "wdefault",
                                "method" to "getData",
                                "startDate" to startDate,
                                "endDate" to endDate,
                                "br_id" to brId,
                                "admno" to admno,
                                "room" to "",
                                "client" to Tenants.GU.clientCode,
                                "acadyr" to acadYear,
                            ),
                        )
                    },
            ),
        )
    }
