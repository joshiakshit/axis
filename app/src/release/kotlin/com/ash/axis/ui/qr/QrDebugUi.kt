package com.ash.axis.ui.qr

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun QrDiagnosticOverlay(
    diagnostics: QrDiagnostics,
    modifier: Modifier = Modifier,
) = Unit

@Composable
internal fun QrRecognitionButton(onClick: (() -> Unit)?) = Unit

internal fun recognitionOnlyMessage(decoder: String): String = ""
