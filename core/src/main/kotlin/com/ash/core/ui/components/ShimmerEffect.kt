package com.ash.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.cardColor

@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "shimmer-translate",
    )
    val baseColor = cardColor()
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHigh
    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim - 200f, 0f),
        end = Offset(translateAnim + 200f, 0f),
    )
}

@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
) {
    val brush = shimmerBrush()
    Box(
        modifier =
            modifier
                .height(height)
                .background(brush, shape = AppShapes.small),
    )
}

@Composable
fun ShimmerCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = cardColor(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBlock(modifier = Modifier.fillMaxWidth(0.4f), height = 10.dp)
            ShimmerBlock(modifier = Modifier.fillMaxWidth(0.7f), height = 14.dp)
            ShimmerBlock(modifier = Modifier.fillMaxWidth(), height = 6.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBlock(modifier = Modifier.width(60.dp), height = 12.dp)
                ShimmerBlock(modifier = Modifier.width(80.dp), height = 12.dp)
            }
        }
    }
}

@Composable
fun SkeletonLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ShimmerBlock(modifier = Modifier.fillMaxWidth(0.5f), height = 24.dp)
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerCard(modifier = Modifier.weight(1f))
            ShimmerCard(modifier = Modifier.weight(1f))
            ShimmerCard(modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        ShimmerBlock(modifier = Modifier.fillMaxWidth(0.3f), height = 10.dp)
        ShimmerCard()
        ShimmerCard()
        ShimmerCard()
    }
}
