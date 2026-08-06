package com.ash.axis.data.repository

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response

internal class StudentApiParser(
    private val json: Json,
) {
    fun parseStudentResponse(body: ResponseBody): JsonElement {
        val raw = body.string().trim()
        val jsonStart =
            listOf(raw.indexOf('{'), raw.indexOf('['))
                .filter { it >= 0 }
                .minOrNull()
                ?: throw IcloudServerException(message = "Student API returned a non-JSON response")
        return json.parseToJsonElement(raw.substring(jsonStart))
    }

    fun requireBody(
        endpoint: String,
        response: Response<ResponseBody>,
    ): ResponseBody {
        if (response.isSuccessful) {
            return response.body() ?: error("$endpoint failed: empty response body")
        }

        val code = response.code()
        val errorBody = response.errorBody()?.string()
        Log.d("StudentApi", "[$endpoint] HTTP $code body=${errorBody?.trim()?.take(500)}")
        if (code == 401) throw SessionExpiredException()
        throw IcloudServerException(code, "$endpoint failed: HTTP $code")
    }

    fun jsonBody(vararg pairs: Pair<String, Any>): RequestBody {
        val payload =
            buildJsonObject {
                pairs.forEach { (key, value) ->
                    when (value) {
                        is Number -> put(key, JsonPrimitive(value))
                        is Boolean -> put(key, JsonPrimitive(value))
                        else -> put(key, JsonPrimitive(value.toString()))
                    }
                }
            }
        return payload.toString().toRequestBody("application/json".toMediaType())
    }
}

internal fun JsonElement.arrayOrObjectValue(vararg keys: String): JsonElement {
    if (this is JsonArray) return this
    val obj = this as? JsonObject ?: error("Student API returned an unexpected response shape")
    return keys.firstNotNullOfOrNull { obj[it] }
        ?: error("Student API response is missing expected data")
}

internal fun JsonElement.objectOrObjectValue(vararg keys: String): JsonElement {
    val obj = this as? JsonObject ?: error("Student API returned an unexpected response shape")
    if ("table" in obj || "DateArray" in obj) return obj
    return keys.firstNotNullOfOrNull { obj[it] }
        ?: error("Student API response is missing expected data")
}
