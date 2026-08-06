package com.ash.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val loaderIcons =
    listOf(
        Icons.Outlined.School,
        Icons.Outlined.CalendarMonth,
        Icons.Outlined.QrCodeScanner,
        Icons.Outlined.Insights,
    )

@Composable
fun AnimatedIconLoader(
    modifier: Modifier = Modifier,
    message: String = "Loading your data…",
) {
    val transition = rememberInfiniteTransition(label = "icon_loader")
    val phase =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = loaderIcons.size.toFloat(),
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "icon_loader_phase",
        )
    val tint = MaterialTheme.colorScheme.primary

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            loaderIcons.forEachIndexed { index, icon ->
                Icon(
                    icon,
                    contentDescription = null,
                    tint = tint,
                    modifier =
                        Modifier
                            .size(26.dp)
                            .graphicsLayer {
                                val cycle = loaderIcons.size.toFloat()
                                val distance = (phase.value - index + cycle) % cycle
                                val proximity = (1f - minOf(distance, cycle - distance)).coerceIn(0f, 1f)
                                val scale = 0.85f + 0.35f * proximity
                                scaleX = scale
                                scaleY = scale
                                alpha = 0.35f + 0.65f * proximity
                                translationY = -12f * proximity
                            },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
