@file:Suppress("TooManyFunctions")

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

private enum class CameraAction { Selfie, Qr }

@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
@Composable
fun QrScanFlow(
    visible: Boolean,
    isSubmitting: Boolean,
    message: String?,
    success: Boolean?,
    onSubmit: (rawQr: String, selfie: String) -> Unit,
    onShowMessage: (String) -> Unit,
    onClearMessage: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showQrAttendanceDialog by remember { mutableStateOf(false) }
    var showSelfieCamera by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var qrSelfie by remember { mutableStateOf<String?>(null) }
    var pendingCameraAction by remember { mutableStateOf<CameraAction?>(null) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(success) {
        if (success == true) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun closeFlow() {
        showQrAttendanceDialog = false
        showSelfieCamera = false
        showQrScanner = false
        pendingCameraAction = null
        onDismiss()
    }

    LaunchedEffect(visible) {
        if (visible) {
            showQrAttendanceDialog = true
            if (hasPermission(context, Manifest.permission.CAMERA)) {
                showSelfieCamera = true
            }
        } else {
            showQrAttendanceDialog = false
            showSelfieCamera = false
            showQrScanner = false
            pendingCameraAction = null
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val cameraGranted = grants[Manifest.permission.CAMERA] == true || hasPermission(context, Manifest.permission.CAMERA)
            if (cameraGranted) {
                when (pendingCameraAction) {
                    CameraAction.Selfie -> showSelfieCamera = true
                    CameraAction.Qr -> if (qrSelfie != null) showQrScanner = true
                    null -> Unit
                }
            } else {
                onShowMessage("Camera permission is required to scan attendance QR codes")
            }
            pendingCameraAction = null
        }
    val openSelfieCamera = {
        if (hasPermission(context, Manifest.permission.CAMERA)) {
            showSelfieCamera = true
        } else {
            pendingCameraAction = CameraAction.Selfie
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }
    val openQrScanner = {
        if (qrSelfie != null) {
            showQrAttendanceDialog = false
            showSelfieCamera = false
            if (hasPermission(context, Manifest.permission.CAMERA)) {
                showQrScanner = true
            } else {
                pendingCameraAction = CameraAction.Qr
                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            }
        }
    }
    val attachSelfieLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) {
                onShowMessage("Image attachment cancelled")
            } else {
                uri.toDataUri(context)?.let {
                    qrSelfie = it
                    showSelfieCamera = false
                    showQrAttendanceDialog = false
                    if (hasPermission(context, Manifest.permission.CAMERA)) {
                        showQrScanner = true
                    } else {
                        pendingCameraAction = CameraAction.Qr
                        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                    }
                } ?: onShowMessage("Could not read selected image")
            }
        }

    if (showQrAttendanceDialog && !showSelfieCamera) {
        QrAttendanceDialog(
            selfieReady = qrSelfie != null,
            submitting = isSubmitting,
            onTakeSelfie = openSelfieCamera,
            onAttachImage = { attachSelfieLauncher.launch("image/*") },
            onScanQr = {
                if (qrSelfie != null) {
                    openQrScanner()
                }
            },
            onClearSelfie = { qrSelfie = null },
            onCancel = { closeFlow() },
        )
    }

    if (showQrAttendanceDialog && showSelfieCamera) {
        FrontSelfieDialog(
            onCapture = {
                qrSelfie = it
                showSelfieCamera = false
                openQrScanner()
            },
            onAttachImage = {
                showSelfieCamera = false
                attachSelfieLauncher.launch("image/*")
            },
            onCancel = { showSelfieCamera = false },
            onError = onShowMessage,
        )
    }

    if (showQrScanner) {
        QrScannerDialog(
            onQrScanned = { rawQr ->
                val selfie = qrSelfie
                if (selfie == null) {
                    showQrScanner = false
                    showQrAttendanceDialog = true
                } else {
                    showQrScanner = false
                    showQrAttendanceDialog = false
                    showSelfieCamera = false
                    qrSelfie = null
                    onSubmit(rawQr, selfie)
                    onDismiss()
                }
            },
            onCancel = {
                showQrScanner = false
                showQrAttendanceDialog = true
            },
            onError = onShowMessage,
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
