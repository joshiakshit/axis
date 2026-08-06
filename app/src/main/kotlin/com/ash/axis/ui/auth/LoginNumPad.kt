package com.ash.axis.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val KEY_LABELS =
    mapOf(
        '1' to "",
        '2' to "ABC",
        '3' to "DEF",
        '4' to "GHI",
        '5' to "JKL",
        '6' to "MNO",
        '7' to "PQRS",
        '8' to "TUV",
        '9' to "WXYZ",
        '0' to "+",
    )

@Composable
internal fun NumPad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val keyShape = RoundedCornerShape(12.dp)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        NumPadRow(keys = listOf('1', '2', '3'), onDigit = onDigit, keyShape = keyShape)
        NumPadRow(keys = listOf('4', '5', '6'), onDigit = onDigit, keyShape = keyShape)
        NumPadRow(keys = listOf('7', '8', '9'), onDigit = onDigit, keyShape = keyShape)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Spacer(Modifier.weight(1f).height(58.dp))
            GhostKey(
                char = '0',
                onClick = { onDigit('0') },
                keyShape = keyShape,
                modifier = Modifier.weight(1f),
            )
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDelete()
                },
                modifier = Modifier.weight(1f).height(58.dp),
                shape = keyShape,
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Delete",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun NumPadRow(
    keys: List<Char>,
    onDigit: (Char) -> Unit,
    keyShape: RoundedCornerShape,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        keys.forEach { c ->
            GhostKey(
                char = c,
                onClick = { onDigit(c) },
                keyShape = keyShape,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun GhostKey(
    char: Char,
    onClick: () -> Unit,
    keyShape: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val label = KEY_LABELS[char] ?: ""

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier.height(58.dp),
        shape = keyShape,
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = char.toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (label.isNotEmpty()) {
                Spacer(Modifier.width(0.dp))
                Text(
                    text = label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    letterSpacing = 1.5.sp,
                )
            }
        }
    }
}
