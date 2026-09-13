package com.ash.axis.data.repository

import com.ash.axis.data.api.UserApi
import com.ash.axis.data.db.CacheDao
import com.ash.axis.domain.model.CalendarEvent
import com.ash.axis.domain.model.Holiday
import com.ash.axis.domain.model.UserInfo
import com.ash.core.storage.CachePolicy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepository
    @Inject
    constructor(
        private val userApi: UserApi,
        cacheDao: CacheDao,
        private val json: Json,
    ) {
        private val feed = CachedFeed(cacheDao, json)
        private val serializer = ListSerializer(CalendarEvent.serializer())

        suspend fun getCalendar(
            user: UserInfo,
            start: LocalDate,
            end: LocalDate,
            forceRefresh: Boolean = false,
        ): FeedSnapshot<List<CalendarEvent>> {
            val key = "v2_calendar_${user.clientId}_${user.admno}_${user.brId}_${start}_$end"
            return feed.load(key, serializer, CachePolicy.ATTENDANCE, forceRefresh) {
                val body =
                    mapOf(
                        "br_id" to user.brId.toString(),
                        "fromdate" to start.toString(),
                        "todate" to end.toString(),
                        "lastmodifiedby" to user.email,
                    )
                val events =
                    json.feedArray(userApi.getEvents(body), "events").map {
                        json.decodeFromJsonElement(CalendarEvent.serializer(), it).let { event ->
                            event.copy(title = event.title.ifBlank { event.description }, isHoliday = false)
                        }.also(::validate)
                    }
                val holidays =
                    json.feedArray(userApi.getHolidays(body), "holidays").map {
                        val holiday = json.decodeFromJsonElement(Holiday.serializer(), it)
                        CalendarEvent(holiday.date, holiday.name, isHoliday = true).also(::validate)
                    }
                (events + holidays).filter { LocalDate.parse(it.date) in start..end }.sortedBy { it.date }
            }
        }

        private fun validate(event: CalendarEvent) {
            LocalDate.parse(event.date)
            check(event.title.isNotBlank()) { "Invalid calendar entry" }
        }
    }
