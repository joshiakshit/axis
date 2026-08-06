package com.ash.axis.data.repository

import com.ash.axis.domain.model.QrScanResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal class QrScanResultParser(
    private val json: Json,
) {
    fun parse(
        raw: String,
        defaultSuccess: Boolean,
    ): QrScanResult {
        val body = raw.trim()
        val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull()
        val obj = parsed as? JsonObject
        val statusText =
            listOf("status", "success", "ok", "result")
                .firstNotNullOfOrNull { key -> obj?.get(key)?.primitiveTextOrNull() }
                .orEmpty()
        val message =
            listOf("message", "msg", "error", "detail")
                .firstNotNullOfOrNull { key -> obj?.get(key)?.primitiveTextOrNull() }
                ?: body.take(220).ifBlank { "QR attendance response received" }
        val success =
            when (statusText.lowercase()) {
                "true", "1", "success", "ok", "yes" -> true
                "false", "0", "failed", "fail", "error", "no" -> false
                else -> defaultSuccess && !message.contains("fail", ignoreCase = true) && !message.contains("error", ignoreCase = true)
            }

        return QrScanResult(success = success, message = message, rawResponse = body)
    }

    private fun JsonElement.primitiveTextOrNull(): String? =
        (this as? JsonPrimitive)?.let { primitive ->
            primitive.contentOrNull
        }
}
