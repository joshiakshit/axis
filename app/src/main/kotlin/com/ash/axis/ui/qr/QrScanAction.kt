package com.ash.axis.ui.qr

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
