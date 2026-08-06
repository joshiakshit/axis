package com.ash.axis.data.repository

import com.ash.axis.domain.model.TimetableSlot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.DayOfWeek
import java.time.LocalDate

internal class TimetableNormalizer(
    private val json: Json,
) {
    private val dayOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val dateRegex = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val dayOfWeekMap =
        mapOf(
            "Mon" to DayOfWeek.MONDAY,
            "Tue" to DayOfWeek.TUESDAY,
            "Wed" to DayOfWeek.WEDNESDAY,
            "Thu" to DayOfWeek.THURSDAY,
            "Fri" to DayOfWeek.FRIDAY,
            "Sat" to DayOfWeek.SATURDAY,
            "Sun" to DayOfWeek.SUNDAY,
        )

    @Suppress("NestedBlockDepth")
    fun normalizeTimetable(raw: JsonElement): Map<String, List<TimetableSlot>> {
        val normalized = dayOrder.associateWith { mutableListOf<TimetableSlot>() }.toMutableMap()
        val source = raw.objectValueOrSelf("emp_timetable", "timetable", "data", "result")

        when (source) {
            is JsonArray -> source.forEach { addSlot(normalized, it, null) }
            is JsonObject -> addObjectSlots(source, normalized)
            else -> Unit
        }

        return normalized
    }

    @Suppress("NestedBlockDepth", "CyclomaticComplexMethod")
    fun normalizeDateKeyed(
        raw: JsonElement,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
    ): Map<LocalDate, List<TimetableSlot>> {
        val result = mutableMapOf<LocalDate, MutableList<TimetableSlot>>()
        val source = raw.objectValueOrSelf("emp_timetable", "timetable", "data", "result")

        when (source) {
            is JsonObject -> addDateKeyedObjectSlots(source, result, rangeStart, rangeEnd)
            is JsonArray -> addDateKeyedArraySlots(source, result, rangeStart, rangeEnd)
            else -> Unit
        }

        return result
    }

    private fun addObjectSlots(
        source: JsonObject,
        normalized: MutableMap<String, MutableList<TimetableSlot>>,
    ) {
        source.forEach { (key, value) ->
            val fallbackDay = dayNameFromKey(key)
            when (value) {
                is JsonArray -> value.forEach { addSlot(normalized, it, fallbackDay) }
                is JsonObject -> value.values.forEach { addSlot(normalized, it, fallbackDay) }
                else -> Unit
            }
        }
    }

    private fun addDateKeyedObjectSlots(
        source: JsonObject,
        result: MutableMap<LocalDate, MutableList<TimetableSlot>>,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
    ) {
        source.forEach { (key, value) ->
            val date = dateFromKey(key, rangeStart, rangeEnd)
            val slots =
                when (value) {
                    is JsonArray -> value.mapNotNull { decodeSlot(it) }
                    is JsonObject -> value.values.mapNotNull { decodeSlot(it) }
                    else -> emptyList()
                }
            if (date != null && slots.isNotEmpty()) {
                result.getOrPut(date) { mutableListOf() }.addAll(slots)
            }
        }
    }

    private fun addDateKeyedArraySlots(
        source: JsonArray,
        result: MutableMap<LocalDate, MutableList<TimetableSlot>>,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
    ) {
        source.mapNotNull { decodeSlot(it) }.forEach { slot ->
            val date = dateFromKey(slot.day, rangeStart, rangeEnd)
            if (date != null) {
                result.getOrPut(date) { mutableListOf() }.add(slot)
            }
        }
    }

    private fun JsonElement.objectValueOrSelf(vararg keys: String): JsonElement {
        val obj = this as? JsonObject ?: return this
        return keys.firstNotNullOfOrNull { obj[it] } ?: this
    }

    private fun addSlot(
        normalized: MutableMap<String, MutableList<TimetableSlot>>,
        element: JsonElement,
        fallbackDay: String?,
    ) {
        val slot = decodeSlot(element) ?: return
        val day = dayNameFromKey(slot.day.ifBlank { fallbackDay.orEmpty() })
        normalized.getOrPut(day) { mutableListOf() }.add(slot.copy(day = slot.day.ifBlank { day }))
    }

    private fun dayNameFromKey(key: String): String {
        if (key in dayOrder) return key
        if (dateRegex.matches(key)) {
            return runCatching {
                dayNames[LocalDate.parse(key).dayOfWeek.value % 7]
            }.getOrDefault(key.take(3))
        }
        return key.take(3).replaceFirstChar { it.uppercase() }
    }

    private fun dateFromKey(
        key: String,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
    ): LocalDate? {
        if (dateRegex.matches(key)) {
            return runCatching { LocalDate.parse(key) }.getOrNull()
        }
        val dayName = key.take(3).replaceFirstChar { it.uppercase() }
        val dow = dayOfWeekMap[dayName] ?: return null
        val delta = (dow.value - rangeStart.dayOfWeek.value + 7) % 7
        val candidate = rangeStart.plusDays(delta.toLong())
        return candidate.takeIf { it <= rangeEnd }
    }

    private fun decodeSlot(element: JsonElement): TimetableSlot? {
        var slot = runCatching { json.decodeFromJsonElement(TimetableSlot.serializer(), element) }.getOrNull() ?: return null
        slot = patchMissingNames(slot, element)
        return if (slot.hasContent()) slot else null
    }

    private fun patchMissingNames(
        slot: TimetableSlot,
        raw: JsonElement,
    ): TimetableSlot {
        if (raw !is JsonObject) return slot
        var patched = slot
        if (patched.subname.isNullOrBlank()) {
            val name = raw.stringField("SubName") ?: raw.stringField("subName") ?: raw.stringField("subject_name")
            if (!name.isNullOrBlank()) patched = patched.copy(subname = name)
        }
        if (patched.fromTime.isBlank()) {
            val fromTime = raw.stringField("fromtime") ?: raw.stringField("FromTime")
            if (!fromTime.isNullOrBlank()) patched = patched.copy(fromTime = fromTime)
        }
        if (patched.toTime.isBlank()) {
            val toTime = raw.stringField("totime") ?: raw.stringField("ToTime")
            if (!toTime.isNullOrBlank()) patched = patched.copy(toTime = toTime)
        }
        return patched
    }

    private fun TimetableSlot.hasContent(): Boolean =
        fromTime.isNotBlank() ||
            toTime.isNotBlank() ||
            subjectId.isNotBlank() ||
            !subject_full.isNullOrBlank() ||
            !subjectName.isNullOrBlank() ||
            !subname.isNullOrBlank()

    private fun JsonObject.stringField(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
