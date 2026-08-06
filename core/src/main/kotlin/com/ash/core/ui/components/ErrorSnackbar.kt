package com.ash.core.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

suspend fun SnackbarHostState.showRetryableError(
    message: String,
    onRetry: () -> Unit,
) {
    val result =
        showSnackbar(
            message = message,
            actionLabel = "Retry",
            duration = SnackbarDuration.Long,
        )
    if (result == SnackbarResult.ActionPerformed) {
        onRetry()
    }
}
