package com.ash.axis.ui.qr

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
internal fun QrResultDialog(
    success: Boolean,
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        title = {
            Text(if (success) "QR attendance submitted" else "QR attendance")
        },
        text = {
            Text(message)
        },
    )
}

@Suppress("LongMethod")
@Composable
internal fun QrSuccessOverlay(onDismiss: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    val checkProgress = remember { Animatable(0f) }
    val circleProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        circleProgress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 700f))
        checkProgress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 600f))
        delay(900)
        visible = false
        delay(180)
        onDismiss()
    }

    val successColor = Color(0xFF4CAF50)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 700f)),
        exit = fadeOut(tween(160)) + scaleOut(tween(160)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.97f))
                    .clickable(onClick = {
                        visible = false
                        onDismiss()
                    }),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier =
                        Modifier
                            .size(120.dp)
                            .drawBehind {
                                val stroke = 6.dp.toPx()
                                val radius = (size.minDimension - stroke) / 2f
                                val sweep = 360f * circleProgress.value
                                drawArc(
                                    color = successColor,
                                    startAngle = -90f,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                                )
                                val cp = checkProgress.value
                                if (cp > 0f) {
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val p1 = Offset(cx - radius * 0.3f, cy + radius * 0.05f)
                                    val p2 = Offset(cx - radius * 0.05f, cy + radius * 0.3f)
                                    val p3 = Offset(cx + radius * 0.35f, cy - radius * 0.25f)
                                    val firstLeg = minOf(cp * 2f, 1f)
                                    val secondLeg = maxOf((cp - 0.5f) * 2f, 0f)
                                    if (firstLeg > 0f) {
                                        val end =
                                            Offset(
                                                p1.x + (p2.x - p1.x) * firstLeg,
                                                p1.y + (p2.y - p1.y) * firstLeg,
                                            )
                                        drawLine(successColor, p1, end, stroke, StrokeCap.Round)
                                    }
                                    if (secondLeg > 0f) {
                                        val end =
                                            Offset(
                                                p2.x + (p3.x - p2.x) * secondLeg,
                                                p2.y + (p3.y - p2.y) * secondLeg,
                                            )
                                        drawLine(successColor, p2, end, stroke, StrokeCap.Round)
                                    }
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {}
                Spacer(Modifier.height(24.dp))
                Text(
                    "Attendance Marked!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your attendance has been recorded.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }
    }
}
