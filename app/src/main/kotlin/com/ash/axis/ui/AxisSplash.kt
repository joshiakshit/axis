package com.ash.axis.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Reproduces the source CSS stroke-draw: the caret traces itself from the bottom-left leg, up to the
// apex, and down to the bottom-right leg, holds briefly, then fades out and hands off to the app.
@Composable
fun AxisSplash(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // ~0.85s ease-in-out draw, a short hold on the finished mark, then a quick fade into the app.
        progress.animateTo(1f, tween(durationMillis = 850, easing = FastOutSlowInEasing))
        delay(240L)
        fade.animateTo(0f, tween(durationMillis = 240))
        onFinished()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFF000000))
                .graphicsLayer { alpha = fade.value },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val markWidth = size.minDimension * 0.72f
            val markHeight = markWidth * 0.85f // height / width of the source caret (170 / 200)
            val cx = size.width / 2f
            val cy = size.height / 2f
            val left = Offset(cx - markWidth / 2f, cy + markHeight / 2f)
            val apex = Offset(cx, cy - markHeight / 2f)
            val right = Offset(cx + markWidth / 2f, cy + markHeight / 2f)

            val caret =
                Path().apply {
                    moveTo(left.x, left.y)
                    lineTo(apex.x, apex.y)
                    lineTo(right.x, right.y)
                }
            val measure = PathMeasure().apply { setPath(caret, false) }
            val drawn = Path()
            if (measure.getSegment(0f, progress.value * measure.length, drawn)) {
                drawPath(
                    path = drawn,
                    color = Color.White,
                    // Stroke width / mark width matches the source CSS (20 / 200).
                    style = Stroke(width = markWidth * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}
