package com.ash.axis.ui.qr

import androidx.activity.compose.BackHandler
import androidx.camera.core.ZoomState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

@Suppress("LongMethod")
@Composable
internal fun QrScanScreen(
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
        val observer = androidx.lifecycle.Observer<ZoomState> { zs -> hardwareMaxZoom = zs.maxZoomRatio }
        camera?.cameraInfo?.zoomState?.observeForever(observer)
        onDispose { camera?.cameraInfo?.zoomState?.removeObserver(observer) }
    }

    val sliderMax = maxOf(hardwareMaxZoom, EXTENDED_MAX_ZOOM)
    val opticalZoom = minOf(zoomRatio, hardwareMaxZoom)
    val digitalZoom = (zoomRatio / opticalZoom).coerceAtLeast(1f)

    BackHandler(onBack = onCancel)

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                modifier = Modifier.fillMaxSize(),
            )

            // Scan-frame reticle
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(255.dp)
                        .border(2.5.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(24.dp)),
            )

            ScrimTop()
            ScrimBottom()

            CameraTopBar(
                title = "Mark Attendance",
                step = "Step 2 of 2 · Scan QR",
                onClose = onCancel,
            )

            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Point at the classroom QR code",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                )
                Spacer24()
                ZoomSlider(
                    zoom = zoomRatio,
                    range = 1f..sliderMax,
                    onZoomChange = { value ->
                        zoomRatio = value
                        cameraRef?.cameraControl?.setZoomRatio(minOf(value, hardwareMaxZoom))
                    },
                )
            }
        }
    }
}

@Composable
private fun Spacer24() {
    Box(modifier = Modifier.height(14.dp))
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
    val trackColor = Color.White.copy(alpha = 0.25f)
    val activeColor = Color.White

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "${String.format(Locale.US, "%.1f", zoom)}x",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = 0.85f),
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
                modifier = Modifier.fillMaxWidth(fraction.coerceAtLeast(0.01f)),
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
