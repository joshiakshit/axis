package com.ash.axis.data.repository

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import retrofit2.Response

class StudentApiParserTest {
    @Test
    fun `HTTP error logging excludes the server response body`() {
        mockkStatic(Log::class)
        try {
            every { Log.d(any(), any()) } returns 0
            val response = Response.error<okhttp3.ResponseBody>(503, "private student data".toResponseBody())
            assertThrows<IcloudServerException> { StudentApiParser(Json).requireBody("attendance", response) }
            verify(exactly = 1) { Log.d("StudentApi", "[attendance] HTTP 503") }
            verify(exactly = 0) { Log.d(any(), match { it.contains("private student data") }) }
        } finally {
            unmockkStatic(Log::class)
        }
    }
}
