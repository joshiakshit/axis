package com.ash.axis.ui.qr

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun QrResultDialog(
    success: Boolean,
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
        title = {
            Text(if (success) "Attendance marked" else "QR attendance")
        },
        text = { Text(message) },
    )
}
