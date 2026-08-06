package com.ash.axis.ui.qr

import androidx.camera.core.ZoomState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

@Suppress("LongMethod")
@Composable
internal fun QrScannerDialog(
    onQrScanned: (String) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var zoomRatio by remember { mutableStateOf(1f) }
    var cameraRef by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var hardwareMaxZoom by remember { mutableStateOf(16f) }

    DisposableEffect(cameraRef) {
        val camera = cameraRef
        val observer =
            androidx.lifecycle.Observer<ZoomState> { zs ->
                hardwareMaxZoom = zs.maxZoomRatio
            }
        camera?.cameraInfo?.zoomState?.observeForever(observer)
        onDispose { camera?.cameraInfo?.zoomState?.removeObserver(observer) }
    }

    val sliderMax = maxOf(hardwareMaxZoom, EXTENDED_MAX_ZOOM)
    val opticalZoom = minOf(zoomRatio, hardwareMaxZoom)
    val digitalZoom = (zoomRatio / opticalZoom).coerceAtLeast(1f)

    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
        title = { Text("Scan QR") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QrCameraPreview(
                    lifecycleOwner = lifecycleOwner,
                    digitalZoom = digitalZoom,
                    onQrScanned = onQrScanned,
                    onError = onError,
                    onCameraBound = { camera -> cameraRef = camera },
                    onPinchZoom = { scaleFactor ->
                        val newZoom = (zoomRatio * scaleFactor).coerceIn(1f, sliderMax)
                        zoomRatio = newZoom
                        cameraRef?.cameraControl?.setZoomRatio(minOf(newZoom, hardwareMaxZoom))
                    },
                )
                ZoomSlider(
                    zoom = zoomRatio,
                    range = 1f..sliderMax,
                    onZoomChange = { value ->
                        zoomRatio = value
                        cameraRef?.cameraControl?.setZoomRatio(minOf(value, hardwareMaxZoom))
                    },
                )
            }
        },
    )
}

@Suppress("LongMethod")
@Composable
private fun ZoomSlider(
    zoom: Float,
    range: ClosedFloatingPointRange<Float>,
    onZoomChange: (Float) -> Unit,
) {
    val span = range.endInclusive - range.start
    val fraction = ((zoom - range.start) / span).coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val activeColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "${String.format(Locale.US, "%.1f", zoom)}x",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = labelColor,
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(28.dp)
                    .pointerInput(range) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                            onZoomChange(range.start + frac * span)
                        }
                    }
                    .pointerInput(range) {
                        detectTapGestures { offset ->
                            val frac = (offset.x / size.width).coerceIn(0f, 1f)
                            onZoomChange(range.start + frac * span)
                        }
                    },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(trackColor),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction.coerceAtLeast(0.01f))
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(activeColor),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction.coerceAtLeast(0.01f)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(activeColor),
                )
            }
        }
    }
}
