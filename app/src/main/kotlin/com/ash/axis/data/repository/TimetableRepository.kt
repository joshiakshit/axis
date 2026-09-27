package com.ash.axis.data.repository

import com.ash.axis.data.academic.AcademicResource
import com.ash.axis.data.academic.AcademicSnapshot
import com.ash.axis.data.api.ICloudEmsApi
import com.ash.axis.data.db.CacheDao
import com.ash.axis.data.db.JsonCache
import com.ash.axis.domain.model.StudentRequestContext
import com.ash.axis.domain.model.TimetableSlot
import com.ash.core.storage.CacheFreshness
import com.ash.core.storage.CachePolicy
import com.ash.core.storage.CachedResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooGenericExceptionCaught")
class TimetableRepository
    @Inject
    constructor(
        private val api: ICloudEmsApi,
        private val cacheDao: CacheDao,
        private val authRepository: AuthRepository,
        json: Json,
    ) {
        private val studentApi = StudentApiParser(json)
        private val normalizer = TimetableNormalizer(json)
        private val typedCache = JsonCache(cacheDao, json)
        private val mapSerializer = MapSerializer(String.serializer(), ListSerializer(TimetableSlot.serializer()))
        private val routeMutex = Mutex()
        private val routes = mutableMapOf<StudentRequestContext, TimetableRoute>()
        private val timetable =
            AcademicResource(
                CoroutineScope(SupervisorJob() + Dispatchers.IO),
                { key: TimetableKey ->
                    typedCache.read(key.cacheKey, TimetableData.serializer(), CachePolicy.TIMETABLE)
                        ?: readLegacyWeek(key)
                },
                { key ->
                    requireActiveAccount(key.context)
                    val route = resolveRoute(key.context)
                    val response = fetchTimetableResponse(key.context, route, key.startDate, key.endDate)
                    requireActiveAccount(key.context)
                    TimetableData(
                        normalizer.normalizeTimetable(response),
                        normalizer.normalizeDateKeyed(response, LocalDate.parse(key.startDate), LocalDate.parse(key.endDate))
                            .mapKeys { it.key.toString() },
                    )
                },
                { key, data -> typedCache.write(key.cacheKey, data, TimetableData.serializer()) },
            )

        suspend fun observeWeek(key: TimetableKey): StateFlow<AcademicSnapshot<TimetableData>> = timetable.observe(key)

        suspend fun requestWeek(
            key: TimetableKey,
            force: Boolean = false,
        ): StateFlow<AcademicSnapshot<TimetableData>> {
            if (force) clearRoute(key.context)
            timetable.prioritize(key)
            return timetable.request(key, force)
        }

        suspend fun requestPrefetchWeek(key: TimetableKey): StateFlow<AcademicSnapshot<TimetableData>> =
            timetable.request(key, speculative = true)

        suspend fun invalidateWeek(key: TimetableKey) = timetable.invalidate(key)

        suspend fun deactivateAcademicData() {
            timetable.deactivate()
            routeMutex.withLock { routes.clear() }
        }

        suspend fun clearRoute(context: StudentRequestContext) {
            routeMutex.withLock { routes.remove(context) }
        }

        private fun requireActiveAccount(context: StudentRequestContext) {
            val user = authRepository.getUserInfo()
            check(user != null && user.admno == context.admno && user.brId == context.brId && user.clientId == context.clientId) {
                "Academic request account changed"
            }
        }

        private suspend fun readLegacyWeek(key: TimetableKey): CachedResult<TimetableData>? =
            listOf("weekly", "dated").flatMap { kind ->
                TimetableRoute.entries.mapNotNull { route ->
                    val context = key.context
                    val cacheKey =
                        "v3_timetable_${kind}_${context.admno}_${context.brId}_${context.clientId}_" +
                            "${context.academicYear}_${route.name.lowercase()}_${key.startDate}_${key.endDate}"
                    val cached = typedCache.read(cacheKey, mapSerializer, CachePolicy.TIMETABLE) ?: return@mapNotNull null
                    val data =
                        if (kind == "dated") {
                            val weekly =
                                runCatching {
                                    cached.data.mapKeys {
                                        LocalDate.parse(it.key).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                                    }
                                }.getOrNull() ?: return@mapNotNull null
                            TimetableData(weekly, cached.data)
                        } else {
                            TimetableData(cached.data, null)
                        }
                    CachedResult(data, CacheFreshness.STALE, cached.cachedAtMillis)
                }
            }.maxByOrNull { it.cachedAtMillis }

        suspend fun clearCache() {
            timetable.clearCache { cacheDao.clearAll() }
            routeMutex.withLock { routes.clear() }
        }

        private fun timetableBody(
            context: StudentRequestContext,
            startDate: String,
            endDate: String,
        ) = studentApi.jsonBody(
            "from" to "app",
            "empid" to "",
            "action" to "wdefault",
            "method" to "getData",
            "startDate" to startDate,
            "endDate" to endDate,
            "br_id" to context.brId,
            "admno" to context.admno,
            "room" to "",
            "client" to context.clientId,
            "acadyr" to context.academicYear,
        )

        private fun featureBody(context: StudentRequestContext) =
            studentApi.jsonBody(
                "from" to "app",
                "empid" to "",
                "method" to "getbatchwisebranchwiseflow",
                "br_id" to context.brId,
                "admno" to context.admno,
                "client" to context.clientId,
                "acadyr" to context.academicYear,
            )

        private suspend fun resolveRoute(context: StudentRequestContext): TimetableRoute =
            routeMutex.withLock {
                routes[context]?.let { return@withLock it }
                val route =
                    try {
                        authRepository.refreshTokenIfNeeded()
                        val result =
                            studentApi.parseStudentResponse(
                                studentApi.requireBody("getTimetableRoute", api.postTimetableV1(featureBody(context))),
                            )
                        TimetableRouteSelector.select(result)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        TimetableRoute.LEGACY
                    }
                routes[context] = route
                route
            }

        private suspend fun fetchTimetableResponse(
            context: StudentRequestContext,
            route: TimetableRoute,
            startDate: String,
            endDate: String,
        ): JsonElement {
            authRepository.refreshTokenIfNeeded()
            val body = timetableBody(context, startDate, endDate)
            val response =
                when (route) {
                    TimetableRoute.V1 -> studentApi.requireBody("getTimetableV1", api.postTimetableV1(body))
                    TimetableRoute.LEGACY -> studentApi.requireBody("getTimetable", api.postTimetable(body))
                }
            return TimetableResponseValidator.requireSchedule(studentApi.parseStudentResponse(response))
        }
    }

data class TimetableKey(val context: StudentRequestContext, val startDate: String, val endDate: String) {
    val cacheKey: String =
        "v4_timetable_${context.admno}_${context.brId}_${context.clientId}_" +
            "${context.academicYear}_${startDate}_$endDate"
}

@Serializable
data class TimetableData(
    val weekly: Map<String, List<TimetableSlot>>,
    val dated: Map<String, List<TimetableSlot>>?,
)

internal enum class TimetableRoute { LEGACY, V1 }

internal object TimetableRouteSelector {
    fun select(response: JsonElement): TimetableRoute {
        val body = response as? JsonObject ?: return TimetableRoute.LEGACY
        val status = body["status"] as? JsonPrimitive ?: return TimetableRoute.LEGACY
        val enabled = body["enble_batch_wise_course_flow"] as? JsonPrimitive
        return if (!status.isString && status.booleanOrNull == true && enabled?.contentOrNull == "1") {
            TimetableRoute.V1
        } else {
            TimetableRoute.LEGACY
        }
    }
}

internal object TimetableResponseValidator {
    private val scheduleKeys = setOf("emp_timetable", "timetable", "data", "result")

    fun requireSchedule(response: JsonElement): JsonElement {
        val body = response as? JsonObject
        if (body == null || scheduleKeys.none(body::containsKey)) {
            throw IcloudServerException(message = "Timetable response did not include schedule data")
        }
        return response
    }
}
