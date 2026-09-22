package com.ash.axis.ui.qr

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ash.axis.BuildConfig
import kotlinx.coroutines.delay

private enum class CameraAction { Selfie, Qr }

// Gap between tearing down the selfie (front) camera and binding the QR (rear) camera, so the
// selfie camera's async unbindAll can't unbind the freshly-bound rear camera (black-preview race).
@Suppress("TopLevelPropertyNaming")
private const val CAMERA_HANDOFF_DELAY_MS = 350L

@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
@Composable
internal fun QrScanFlow(
    visible: Boolean,
    isSubmitting: Boolean,
    message: String?,
    success: Boolean?,
    onSubmit: (rawQr: String, selfie: String) -> Unit,
    onShowMessage: (String) -> Unit,
    onClearMessage: () -> Unit,
    onDismiss: () -> Unit,
    diagnostics: QrDiagnostics,
) {
    var showSelfie by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var pendingScanner by remember { mutableStateOf(false) }
    var qrSelfie by remember { mutableStateOf<String?>(null) }
    var pendingCameraAction by remember { mutableStateOf<CameraAction?>(null) }
    var scanMode by remember { mutableStateOf(QrScanMode.ATTENDANCE) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Mount the scanner only after the selfie camera has had time to fully release.
    LaunchedEffect(pendingScanner) {
        if (pendingScanner) {
            delay(CAMERA_HANDOFF_DELAY_MS)
            showScanner = true
            pendingScanner = false
        }
    }

    LaunchedEffect(success) {
        if (success == true) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(message) {
        if (message != null) diagnostics.stage(QrStage.RESULT)
    }

    fun closeFlow() {
        showSelfie = false
        showScanner = false
        pendingScanner = false
        qrSelfie = null
        pendingCameraAction = null
        onDismiss()
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val granted = grants[Manifest.permission.CAMERA] == true || hasPermission(context, Manifest.permission.CAMERA)
            when {
                !granted -> {
                    onShowMessage("Camera permission is required to mark attendance")
                    closeFlow()
                }
                pendingCameraAction == CameraAction.Selfie -> showSelfie = true
                pendingCameraAction == CameraAction.Qr -> showScanner = true
                else -> Unit
            }
            pendingCameraAction = null
        }

    fun requestCamera(action: CameraAction) {
        pendingCameraAction = action
        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
    }

    fun openScanner() {
        diagnostics.stage(QrStage.CAMERA_RELEASE)
        showSelfie = false
        showScanner = false
        // Route through pendingScanner so the front camera fully unbinds before the rear camera binds.
        if (hasPermission(context, Manifest.permission.CAMERA)) pendingScanner = true else requestCamera(CameraAction.Qr)
    }

    LaunchedEffect(visible) {
        if (visible) {
            scanMode = QrScanMode.ATTENDANCE
            diagnostics.start(scanMode)
            diagnostics.stage(QrStage.SELFIE)
            qrSelfie = null
            showScanner = false
            if (hasPermission(context, Manifest.permission.CAMERA)) showSelfie = true else requestCamera(CameraAction.Selfie)
        } else {
            showSelfie = false
            showScanner = false
            pendingScanner = false
            qrSelfie = null
            pendingCameraAction = null
        }
    }

    val attachSelfieLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) {
                onShowMessage("Image attachment cancelled")
            } else {
                uri.toDataUri(context)?.let {
                    qrSelfie = it
                    openScanner()
                } ?: onShowMessage("Could not read selected image")
            }
        }

    if (showSelfie) {
        SelfieCaptureScreen(
            onCapture = {
                qrSelfie = it
                openScanner()
            },
            onAttachImage = { attachSelfieLauncher.launch("image/*") },
            onRecognitionOnly =
                if (BuildConfig.DEBUG) {
                    {
                        scanMode = QrScanMode.RECOGNITION_ONLY
                        diagnostics.start(scanMode)
                        openScanner()
                    }
                } else {
                    null
                },
            onCancel = { closeFlow() },
            onError = onShowMessage,
            diagnostics = diagnostics,
        )
    }

    if (pendingScanner) {
        CameraHandoffScreen(diagnostics)
    }

    if (showScanner) {
        QrScanScreen(
            onQrScanned = { rawQr, decoder ->
                when (qrScanAction(scanMode, qrSelfie != null, isSubmitting)) {
                    QrScanAction.RECOGNIZED_ONLY -> {
                        onShowMessage(recognitionOnlyMessage(decoder))
                        closeFlow()
                    }
                    QrScanAction.SUBMIT -> {
                        val selfie = requireNotNull(qrSelfie)
                        showScanner = false
                        qrSelfie = null
                        onSubmit(rawQr, selfie)
                        onDismiss()
                    }
                    QrScanAction.IGNORE -> Unit
                }
            },
            onCancel = { closeFlow() },
            onError = onShowMessage,
            diagnostics = diagnostics,
        )
    }

    if (message != null && success == true) {
        QrSuccessOverlay(onDismiss = onClearMessage)
    } else if (message != null) {
        QrResultDialog(
            success = false,
            message = message,
            onDismiss = onClearMessage,
        )
    }
}
