package com.ash.axis.ui.attendance

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.domain.usecase.AttendanceTone
import com.ash.core.ui.theme.AppDimens
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.cardColor
import java.util.Locale

@Composable
internal fun OverallSummaryCard(data: AttendanceUiState) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (data.overallTotal > 0) (data.overallPercent / 100.0).toFloat().coerceIn(0f, 1f) else 0f,
        animationSpec = tween(800),
        label = "overall-progress",
    )
    val toneColor by animateColorAsState(
        targetValue = data.overallTone.summaryColor(),
        animationSpec = tween(400),
        label = "overall-tone",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = cardColor(),
    ) {
        Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
            OverallSummaryHeader(data, toneColor)
            Spacer(Modifier.height(20.dp))
            OverallProgressBar(data.threshold, animatedProgress, toneColor)
        }
    }
}

@Composable
private fun OverallSummaryHeader(
    data: AttendanceUiState,
    toneColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                String.format(Locale.US, "%.1f%%", data.overallPercent),
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-2).sp,
                color = toneColor,
            )
            Text(
                "${data.overallPresent} of ${data.overallTotal} periods attended",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = AppShapes.full,
            color = toneColor.copy(alpha = 0.14f),
        ) {
            Text(
                data.overallTone.summaryLabel(),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = toneColor,
            )
        }
    }
}

@Composable
private fun OverallProgressBar(
    threshold: Int,
    animatedProgress: Float,
    toneColor: Color,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().height(AppDimens.progressBarHeight).clip(AppShapes.full),
            color = toneColor,
            trackColor = toneColor.copy(alpha = 0.1f),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(threshold / 100f)
                    .height(14.dp)
                    .padding(end = 2.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), CircleShape),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    ProgressLabels(threshold)
}

@Composable
private fun ProgressLabels(threshold: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("0%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Goal: $threshold%",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("100%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AttendanceTone.summaryColor(): Color =
    when (this) {
        AttendanceTone.OK -> MaterialTheme.colorScheme.primary
        AttendanceTone.WARN -> MaterialTheme.colorScheme.tertiary
        AttendanceTone.BAD -> MaterialTheme.colorScheme.error
    }

private fun AttendanceTone.summaryLabel(): String =
    when (this) {
        AttendanceTone.OK -> "Safe"
        AttendanceTone.WARN -> "Warning"
        AttendanceTone.BAD -> "Danger"
    }
