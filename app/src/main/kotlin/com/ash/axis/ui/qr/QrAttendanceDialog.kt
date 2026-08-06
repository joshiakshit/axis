package com.ash.axis.ui.qr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.core.ui.theme.AppShapes

@Composable
internal fun QrAttendanceDialog(
    selfieReady: Boolean,
    submitting: Boolean,
    onTakeSelfie: () -> Unit,
    onAttachImage: () -> Unit,
    onScanQr: () -> Unit,
    onClearSelfie: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {},
        title = { Text("QR attendance") },
        text = {
            QrAttendanceBody(
                selfieReady = selfieReady,
                submitting = submitting,
                onTakeSelfie = onTakeSelfie,
                onAttachImage = onAttachImage,
                onScanQr = onScanQr,
                onClearSelfie = onClearSelfie,
                onCancel = onCancel,
            )
        },
    )
}

@Composable
private fun QrAttendanceBody(
    selfieReady: Boolean,
    submitting: Boolean,
    onTakeSelfie: () -> Unit,
    onAttachImage: () -> Unit,
    onScanQr: () -> Unit,
    onClearSelfie: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (selfieReady) {
                "Selfie is ready. Scan the classroom QR to submit attendance."
            } else {
                "Add your selfie first, then scan the classroom QR."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        QrSelfieStatus(selfieReady = selfieReady)
        if (selfieReady) {
            QrReadyActions(submitting, onScanQr, onTakeSelfie, onAttachImage, onClearSelfie)
        } else {
            QrSelfieRequiredActions(onTakeSelfie, onAttachImage)
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@Composable
private fun QrReadyActions(
    submitting: Boolean,
    onScanQr: () -> Unit,
    onTakeSelfie: () -> Unit,
    onAttachImage: () -> Unit,
    onClearSelfie: () -> Unit,
) {
    SelfieSourceRow(
        title = if (submitting) "Submitting" else "Scan QR",
        subtitle = "Step 2: scan and submit attendance",
        icon = Icons.Default.QrCodeScanner,
        enabled = !submitting,
        onClick = onScanQr,
    )
    SelfieSourceRow(
        title = "Retake selfie",
        subtitle = "Open the front camera again",
        icon = Icons.Default.PhotoCamera,
        onClick = onTakeSelfie,
    )
    SelfieSourceRow(
        title = "Upload different image",
        subtitle = "Replace the selected selfie",
        icon = Icons.Default.Upload,
        onClick = onAttachImage,
    )
    TextButton(onClick = onClearSelfie, modifier = Modifier.fillMaxWidth()) {
        Text("Remove selfie")
    }
}

@Composable
private fun QrSelfieRequiredActions(
    onTakeSelfie: () -> Unit,
    onAttachImage: () -> Unit,
) {
    SelfieSourceRow(
        title = "Take selfie",
        subtitle = "Step 1: use the front camera",
        icon = Icons.Default.PhotoCamera,
        onClick = onTakeSelfie,
    )
    SelfieSourceRow(
        title = "Upload image",
        subtitle = "Attach an existing selfie",
        icon = Icons.Default.Upload,
        onClick = onAttachImage,
    )
}

@Composable
private fun QrSelfieStatus(selfieReady: Boolean) {
    val accent =
        if (selfieReady) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = accent.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent),
            )
            Column {
                Text(
                    if (selfieReady) "Selfie ready" else "Selfie required",
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    if (selfieReady) "Now scan the QR code." else "Take or upload before scanning.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
