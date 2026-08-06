package com.ash.axis.ui.qr

import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ash.core.ui.theme.AppShapes
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import androidx.camera.core.Preview as CameraPreview

@Composable
internal fun FrontCameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onReady: (ImageCapture) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        onDispose { unbindCamera(context) }
    }
    AndroidView(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(AppShapes.medium),
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                bindFrontCamera(
                    lifecycleOwner = lifecycleOwner,
                    previewView = this,
                    onReady = onReady,
                    onError = onError,
                )
            }
        },
    )
}

@Suppress("LongParameterList")
@Composable
internal fun QrCameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    digitalZoom: Float,
    onQrScanned: (String) -> Unit,
    onError: (String) -> Unit,
    onCameraBound: (androidx.camera.core.Camera) -> Unit = {},
    onPinchZoom: (Float) -> Unit = {},
) {
    val context = LocalContext.current
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val cropZoomRef = remember { AtomicReference(1f) }
    cropZoomRef.set(digitalZoom)
    DisposableEffect(Unit) {
        onDispose {
            unbindCamera(context)
            analyzerExecutor.shutdown()
        }
    }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(AppShapes.medium)
                .clipToBounds(),
    ) {
        AndroidView(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = digitalZoom
                        scaleY = digitalZoom
                    },
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    bindQrCamera(
                        lifecycleOwner = lifecycleOwner,
                        previewView = this,
                        analyzerExecutor = analyzerExecutor,
                        zoomProvider = { cropZoomRef.get() },
                        onQrScanned = onQrScanned,
                        onError = onError,
                        onCameraBound = onCameraBound,
                    )
                    val scaleDetector =
                        android.view.ScaleGestureDetector(
                            viewContext,
                            object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                                override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                                    onPinchZoom(detector.scaleFactor)
                                    return true
                                }
                            },
                        )
                    setOnTouchListener { _, event ->
                        scaleDetector.onTouchEvent(event)
                        true
                    }
                }
            },
        )
    }
}

internal fun hasPermission(
    context: Context,
    permission: String,
): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun bindFrontCamera(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    onReady: (ImageCapture) -> Unit,
    onError: (String) -> Unit,
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
    cameraProviderFuture.addListener(
        {
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                val cameraSelector =
                    CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build()
                if (!cameraProvider.hasCamera(cameraSelector)) {
                    error("No front camera")
                }
                val preview =
                    CameraPreview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                val capture =
                    ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    capture,
                )
                onReady(capture)
            }.onFailure {
                onError("Front camera unavailable; upload an image instead")
            }
        },
        ContextCompat.getMainExecutor(previewView.context),
    )
}

@Suppress("LongParameterList")
private fun bindQrCamera(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    analyzerExecutor: ExecutorService,
    zoomProvider: () -> Float,
    onQrScanned: (String) -> Unit,
    onError: (String) -> Unit,
    onCameraBound: (androidx.camera.core.Camera) -> Unit = {},
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
    val didScan = AtomicBoolean(false)
    cameraProviderFuture.addListener(
        {
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                val cameraSelector =
                    CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()
                val preview =
                    CameraPreview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                val analysis =
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                val reader = createQrReader()
                analysis.setAnalyzer(analyzerExecutor) { image ->
                    analyzeQrFrame(image, reader, didScan, zoomProvider()) { value ->
                        previewView.post { onQrScanned(value) }
                    }
                }
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analysis)
                onCameraBound(camera)
            }.onFailure {
                onError("QR camera unavailable. Try again after closing other camera screens.")
            }
        },
        ContextCompat.getMainExecutor(previewView.context),
    )
}

private val mlKitScanner by lazy {
    val options =
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    BarcodeScanning.getClient(options)
}

private fun createQrReader(): MultiFormatReader =
    MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

@Suppress("UnsafeOptInUsageError")
private fun analyzeQrFrame(
    image: ImageProxy,
    reader: MultiFormatReader,
    didScan: AtomicBoolean,
    digitalZoom: Float,
    onQrScanned: (String) -> Unit,
) {
    if (didScan.get()) {
        image.close()
        return
    }

    val mediaImage = image.image
    if (mediaImage != null) {
        val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
        mlKitScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                if (value != null && didScan.compareAndSet(false, true)) {
                    onQrScanned(value)
                } else if (value == null) {
                    val decoded = decodeQrImageZxing(image, reader, digitalZoom)
                    if (!decoded.isNullOrBlank() && didScan.compareAndSet(false, true)) {
                        onQrScanned(decoded)
                    }
                }
            }
            .addOnFailureListener {
                val decoded = decodeQrImageZxing(image, reader, digitalZoom)
                if (!decoded.isNullOrBlank() && didScan.compareAndSet(false, true)) {
                    onQrScanned(decoded)
                }
            }
            .addOnCompleteListener { image.close() }
    } else {
        try {
            val decoded = decodeQrImageZxing(image, reader, digitalZoom)
            if (!decoded.isNullOrBlank() && didScan.compareAndSet(false, true)) {
                onQrScanned(decoded)
            }
        } finally {
            image.close()
        }
    }
}

private fun decodeQrImageZxing(
    image: ImageProxy,
    reader: MultiFormatReader,
    digitalZoom: Float,
): String? {
    val zoom = digitalZoom.coerceAtLeast(1f)
    val cropWidth = (image.width / zoom).toInt().coerceIn(1, image.width)
    val cropHeight = (image.height / zoom).toInt().coerceIn(1, image.height)
    val left = (image.width - cropWidth) / 2
    val top = (image.height - cropHeight) / 2
    val yBytes = image.yPlaneBytes()
    val source =
        PlanarYUVLuminanceSource(
            yBytes,
            image.width,
            image.height,
            left,
            top,
            cropWidth,
            cropHeight,
            false,
        )
    return decodeQrSource(source, reader, ::HybridBinarizer)
        ?: decodeQrSource(source, reader, ::GlobalHistogramBinarizer)
        ?: if (source.isRotateSupported) {
            val rotated = source.rotateCounterClockwise()
            decodeQrSource(rotated, reader, ::HybridBinarizer)
                ?: decodeQrSource(rotated, reader, ::GlobalHistogramBinarizer)
        } else {
            null
        }
}

private fun decodeQrSource(
    source: LuminanceSource,
    reader: MultiFormatReader,
    binarizerFactory: (LuminanceSource) -> com.google.zxing.Binarizer,
): String? =
    runCatching {
        reader.decodeWithState(BinaryBitmap(binarizerFactory(source))).text
    }.also {
        reader.reset()
    }.getOrNull()

private fun ImageProxy.yPlaneBytes(): ByteArray {
    val plane = planes.first()
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val output = ByteArray(width * height)

    buffer.rewind()
    if (pixelStride == 1 && rowStride == width) {
        buffer.get(output)
        return output
    }

    val row = ByteArray(rowStride)
    for (rowIndex in 0 until height) {
        val bytesToRead = minOf(rowStride, buffer.remaining())
        buffer.get(row, 0, bytesToRead)
        for (column in 0 until width) {
            output[rowIndex * width + column] = row[column * pixelStride]
        }
    }
    return output
}

private fun unbindCamera(context: Context) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(
        {
            runCatching { cameraProviderFuture.get().unbindAll() }
        },
        ContextCompat.getMainExecutor(context),
    )
}

internal fun captureSelfie(
    context: Context,
    imageCapture: ImageCapture?,
    onCapture: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val capture = imageCapture
    if (capture == null) {
        onError("Selfie camera is still starting")
        return
    }
    val file = File.createTempFile("qr_selfie_", ".jpg", context.cacheDir)
    val output = ImageCapture.OutputFileOptions.Builder(file).build()
    capture.takePicture(
        output,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val dataUri = file.toJpegDataUri()
                file.delete()
                onCapture(dataUri)
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
                onError(exception.message ?: "Could not capture selfie")
            }
        },
    )
}
