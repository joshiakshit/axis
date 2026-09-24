package com.ash.axis.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class GradesParserTest {
    @Test
    fun `report card accepts session alias and unpublished result`() {
        val data =
            parseGradesData(
                Json.parseToJsonElement(
                    """{"isResultPublish":false,"noResultMsg":"Pending","tempDataArray":[{"exam_session":"session"}]}""",
                ),
            )

        assertFalse(data.isResultPublish)
        assertEquals("Pending", data.noResultMsg)
        assertEquals("session", data.reportCards.single().examSession)
    }

    @Test
    fun `semester maximum accepts roman and numeric class labels`() {
        val data =
            Json.parseToJsonElement(
                """{"classes":[{"sem_disp_name":"Semester IV"},{"sem_disp_name":"Semester 5"}]}""",
            ).jsonObject

        assertEquals(5, parseMaxSemFromClasses(data))
    }
}
