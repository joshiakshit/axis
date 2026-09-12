package com.ash.axis.data.repository

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TimetableRoutingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `selects v1 only for boolean true and string one`() {
        val response = json.parseToJsonElement("""{"status":true,"enble_batch_wise_course_flow":"1"}""")

        assertEquals(TimetableRoute.V1, TimetableRouteSelector.select(response))
    }

    @Test
    fun `uses legacy for string true`() {
        val response = json.parseToJsonElement("""{"status":"true","enble_batch_wise_course_flow":"1"}""")

        assertEquals(TimetableRoute.LEGACY, TimetableRouteSelector.select(response))
    }

    @Test
    fun `uses legacy for false or incomplete responses`() {
        val falseResponse = json.parseToJsonElement("""{"status":"false","enble_batch_wise_course_flow":"false"}""")
        val incompleteResponse = json.parseToJsonElement("""{"status":true}""")

        assertEquals(TimetableRoute.LEGACY, TimetableRouteSelector.select(falseResponse))
        assertEquals(TimetableRoute.LEGACY, TimetableRouteSelector.select(incompleteResponse))
    }

    @Test
    fun `accepts a recognized empty timetable`() {
        val response = json.parseToJsonElement("""{"emp_timetable":{"Mon":[],"Tue":[]}}""")

        assertEquals(response, TimetableResponseValidator.requireSchedule(response))
    }

    @Test
    fun `rejects a timetable feature response as schedule data`() {
        val response = json.parseToJsonElement("""{"status":false,"enble_batch_wise_course_flow":"false"}""")

        assertThrows(IcloudServerException::class.java) {
            TimetableResponseValidator.requireSchedule(response)
        }
    }
}
