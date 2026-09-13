package com.ash.axis.domain.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Holiday(
    @JsonNames("holiday_date", "date", "Date")
    val date: String = "",
    @JsonNames("holiday_name", "name", "Name", "title", "description")
    val name: String = "",
    @JsonNames("holiday_type", "type", "Type")
    val type: String = "",
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CalendarEvent(
    @JsonNames("event_date", "date", "Date")
    val date: String = "",
    @JsonNames("event_name", "title", "Title", "name")
    val title: String = "",
    @JsonNames("event_description", "description", "Description")
    val description: String = "",
    val isHoliday: Boolean = false,
)
