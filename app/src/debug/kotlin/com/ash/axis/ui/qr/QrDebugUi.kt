package com.ash.axis.ui.qr

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
internal fun QrRecognitionButton(onClick: (() -> Unit)?) {
    if (onClick != null) {
        TextButton(onClick = onClick) {
            Text("Recognition only · no submission", color = Color.White)
        }
    }
}

internal fun recognitionOnlyMessage(decoder: String): String = "QR recognized by $decoder. No attendance was submitted."
