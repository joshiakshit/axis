package com.ash.axis.ui.qr

import androidx.camera.core.ImageCapture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun FrontSelfieDialog(
    onCapture: (String) -> Unit,
    onAttachImage: () -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Back")
            }
        },
        title = { Text("Take selfie") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FrontCameraPreview(
                    lifecycleOwner = lifecycleOwner,
                    onReady = { imageCapture = it },
                    onError = onError,
                )
                SelfieSourceRow(
                    title = "Take picture",
                    subtitle = "Capture this front-camera selfie",
                    icon = Icons.Default.PhotoCamera,
                    onClick = {
                        captureSelfie(
                            context = context,
                            imageCapture = imageCapture,
                            onCapture = onCapture,
                            onError = onError,
                        )
                    },
                )
                SelfieSourceRow(
                    title = "Upload image",
                    subtitle = "Attach an existing selfie instead",
                    icon = Icons.Default.Upload,
                    onClick = onAttachImage,
                )
            }
        },
    )
}
