package com.ash.axis.ui.qr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun QrDiagnosticOverlay(
    diagnostics: QrDiagnostics,
    modifier: Modifier = Modifier,
) {
    val state by diagnostics.state.collectAsStateWithLifecycle()
    Column(modifier = modifier.background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp)).padding(8.dp)) {
        DiagnosticLine("attempt ${state.attemptId.takeLast(8)}")
        DiagnosticLine("${state.mode.name.lowercase()} · ${state.stage.name.lowercase()}")
        DiagnosticLine("frames ${state.frameCount} · decode ${state.lastDecodeMs} ms")
        DiagnosticLine("winner ${state.decoder}")
        DiagnosticLine("${state.width}x${state.height} rot ${state.rotation}")
        DiagnosticLine("zoom ${state.opticalZoom}x + ${state.digitalZoom}x")
        DiagnosticLine("crop ${state.cropWidth}x${state.cropHeight}")
        DiagnosticLine("miss ML ${state.mlKitMisses}/${state.mlKitErrors} ZX ${state.zxingMisses}")
        state.httpMs?.let { DiagnosticLine("HTTP ${state.httpStatus ?: "?"} · $it ms · ${state.result}") }
    }
}

@Composable
private fun DiagnosticLine(value: String) {
    Text(value, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
}

@Composable
internal fun QrRecognitionButton(onClick: (() -> Unit)?) {
    if (onClick != null) {
        TextButton(onClick = onClick) {
            Text("Recognition only · no submission", color = Color.White)
        }
    }
}

internal fun recognitionOnlyMessage(decoder: String): String = "QR recognized by $decoder. No attendance was submitted."
