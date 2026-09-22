package com.ash.axis.domain.model

data class QrScanResult(
    val success: Boolean,
    val message: String,
    val rawResponse: String,
    val httpStatus: Int? = null,
    val httpDurationMs: Long? = null,
)
