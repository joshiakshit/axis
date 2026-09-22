package com.ash.axis.ui.qr

import android.util.Log
import com.ash.axis.data.repository.IcloudServerException
import com.ash.axis.data.repository.SessionExpiredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException

internal enum class QrScanMode { ATTENDANCE, RECOGNITION_ONLY }

internal enum class QrScanAction { SUBMIT, RECOGNIZED_ONLY, IGNORE }

internal fun qrScanAction(
    mode: QrScanMode,
    hasSelfie: Boolean,
    isSubmitting: Boolean,
): QrScanAction =
    when {
        mode == QrScanMode.RECOGNITION_ONLY -> QrScanAction.RECOGNIZED_ONLY
        hasSelfie && !isSubmitting -> QrScanAction.SUBMIT
        else -> QrScanAction.IGNORE
    }

internal enum class QrStage { SELFIE, CAMERA_RELEASE, CAMERA_BIND, FIRST_FRAME, RECOGNITION, AUTHENTICATION, SUBMISSION, RESPONSE, RESULT }

internal data class QrDiagnosticState(
    val attemptId: String = "",
    val stage: QrStage = QrStage.SELFIE,
    val mode: QrScanMode = QrScanMode.ATTENDANCE,
    val frameCount: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val rotation: Int = 0,
    val opticalZoom: Float = 1f,
    val digitalZoom: Float = 1f,
    val cropWidth: Int = 0,
    val cropHeight: Int = 0,
    val lastDecodeMs: Long = 0,
    val decoder: String = "none",
    val mlKitMisses: Int = 0,
    val mlKitErrors: Int = 0,
    val zxingMisses: Int = 0,
    val httpStatus: Int? = null,
    val httpMs: Long? = null,
    val result: String = "pending",
)

internal class QrDiagnostics(
    private val enabled: Boolean,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val logger: (String) -> Unit = { Log.d("AxisQr", it) },
) {
    private val mutableState = MutableStateFlow(QrDiagnosticState())
    val state = mutableState.asStateFlow()
    private var sequence = 0
    private var startedAt = 0L
    private var stageAt = 0L
    private var summaryAt = 0L
    private var summaryFrames = 0
    private var summaryDecodeMs = 0L
    private var summaryDecodeCount = 0
    private var summaryDecodeMaxMs = 0L

    @Synchronized
    fun start(mode: QrScanMode) {
        if (!enabled) return
        val now = clockMs()
        sequence++
        startedAt = now
        stageAt = now
        summaryAt = now
        summaryFrames = 0
        summaryDecodeMs = 0
        summaryDecodeCount = 0
        summaryDecodeMaxMs = 0
        mutableState.value = QrDiagnosticState(attemptId = "${System.currentTimeMillis()}-$sequence", mode = mode)
        write("start mode=${mode.name.lowercase()}")
    }

    @Synchronized
    fun stage(stage: QrStage) {
        if (!enabled) return
        val now = clockMs()
        val previous = mutableState.value.stage
        mutableState.value = mutableState.value.copy(stage = stage)
        write(
            "stage=${stage.name.lowercase()} previous=${previous.name.lowercase()} " +
                "previousMs=${now - stageAt} totalMs=${now - startedAt}",
        )
        stageAt = now
    }

    @Synchronized
    fun frame(
        width: Int,
        height: Int,
        rotation: Int,
        opticalZoom: Float,
        digitalZoom: Float,
    ) {
        if (!enabled) return
        val cropWidth = (width / digitalZoom.coerceAtLeast(1f)).toInt().coerceIn(1, width)
        val cropHeight = (height / digitalZoom.coerceAtLeast(1f)).toInt().coerceIn(1, height)
        val current = mutableState.value
        if (current.frameCount == 0) stage(QrStage.FIRST_FRAME)
        mutableState.value =
            mutableState.value.copy(
                frameCount = current.frameCount + 1,
                width = width,
                height = height,
                rotation = rotation,
                opticalZoom = opticalZoom,
                digitalZoom = digitalZoom,
                cropWidth = cropWidth,
                cropHeight = cropHeight,
            )
        summaryFrames++
        val elapsed = clockMs() - summaryAt
        if (elapsed >= 1000) {
            val average = if (summaryDecodeCount == 0) 0 else summaryDecodeMs / summaryDecodeCount
            val framesPerSecond = summaryFrames * 1000 / elapsed
            write(
                "frames=$summaryFrames fps=$framesPerSecond decodeAvgMs=$average decodeMaxMs=$summaryDecodeMaxMs " +
                    "mlkitMisses=${state.value.mlKitMisses} " +
                    "mlkitErrors=${state.value.mlKitErrors} zxingMisses=${state.value.zxingMisses} " +
                    "size=${width}x$height rotation=$rotation " +
                    "opticalZoom=$opticalZoom digitalZoom=$digitalZoom crop=${cropWidth}x$cropHeight",
            )
            summaryAt = clockMs()
            summaryFrames = 0
            summaryDecodeMs = 0
            summaryDecodeCount = 0
            summaryDecodeMaxMs = 0
        }
    }

    @Synchronized
    fun decodeMiss(
        decoder: String,
        durationMs: Long,
    ) {
        if (!enabled) return
        val current = mutableState.value
        mutableState.value =
            when (decoder) {
                "mlkit" -> current.copy(mlKitMisses = current.mlKitMisses + 1, lastDecodeMs = durationMs)
                "mlkit_error" -> current.copy(mlKitErrors = current.mlKitErrors + 1, lastDecodeMs = durationMs)
                else -> current.copy(zxingMisses = current.zxingMisses + 1, lastDecodeMs = durationMs)
            }
        summaryDecodeMs += durationMs
        summaryDecodeCount++
        summaryDecodeMaxMs = maxOf(summaryDecodeMaxMs, durationMs)
    }

    @Synchronized
    fun recognized(
        decoder: String,
        durationMs: Long,
    ) {
        if (!enabled) return
        mutableState.value = mutableState.value.copy(decoder = decoder, lastDecodeMs = durationMs)
        stage(QrStage.RECOGNITION)
        write("recognized decoder=$decoder decodeMs=$durationMs frames=${state.value.frameCount}")
    }

    @Synchronized
    fun response(
        status: Int?,
        durationMs: Long,
        success: Boolean,
    ) {
        if (!enabled) return
        mutableState.value =
            mutableState.value.copy(httpStatus = status, httpMs = durationMs, result = if (success) "success" else "failure")
        stage(QrStage.RESPONSE)
        write("response status=${status ?: "none"} durationMs=$durationMs result=${state.value.result}")
    }

    @Synchronized
    fun error(
        error: Exception,
        status: Int?,
        durationMs: Long,
    ) {
        if (!enabled) return
        mutableState.value = mutableState.value.copy(httpStatus = status, httpMs = durationMs, result = "error")
        stage(QrStage.RESPONSE)
        val kind =
            when (error) {
                is SessionExpiredException -> "session"
                is IcloudServerException -> "http"
                is HttpException -> if (error.code() == 401) "session" else "http"
                is java.io.IOException -> "network"
                else -> "application"
            }
        write("response status=${status ?: "none"} durationMs=$durationMs result=error kind=$kind")
    }

    private fun write(message: String) {
        logger("attempt=${mutableState.value.attemptId} $message")
    }
}
