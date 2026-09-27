package com.ash.axis.ui.attendance

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.domain.usecase.AttendanceTone
import com.ash.core.ui.theme.AppDimens
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.cardColor
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun OverallSummaryCard(
    data: AttendanceUiState,
    onThresholdChange: (Int) -> Unit,
    onCombinedAttendanceChange: (Boolean) -> Unit,
) {
    var draggingTarget by remember { mutableStateOf<Int?>(null) }
    val shownTarget = draggingTarget ?: data.threshold
    LaunchedEffect(data.threshold) {
        if (draggingTarget == data.threshold) draggingTarget = null
    }
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
            OverallSummaryHeader(data, toneColor, onCombinedAttendanceChange)
            Spacer(Modifier.height(20.dp))
            OverallProgressBar(shownTarget, animatedProgress, toneColor, onThresholdChange) { draggingTarget = it }
        }
    }
}

@Composable
private fun OverallSummaryHeader(
    data: AttendanceUiState,
    toneColor: Color,
    onCombinedAttendanceChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = { onCombinedAttendanceChange(!data.combinedAttendance) },
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            if (data.combinedAttendance) "Separate lecture and practical" else "Combine lecture and practical"
                    },
                shape = AppShapes.full,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    if (data.combinedAttendance) "Separate" else "Combine",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
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
}

@Composable
private fun OverallProgressBar(
    threshold: Int,
    animatedProgress: Float,
    toneColor: Color,
    onThresholdChange: (Int) -> Unit,
    onDragTargetChange: (Int?) -> Unit,
) {
    var dragGoal by remember { mutableIntStateOf(threshold) }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(onThresholdChange) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragGoal = goalAtPosition(it.x, size.width.toFloat())
                            onDragTargetChange(dragGoal)
                        },
                        onDragEnd = {
                            onThresholdChange(dragGoal)
                            if (dragGoal == threshold) onDragTargetChange(null)
                        },
                        onDragCancel = { onDragTargetChange(null) },
                    ) { change, _ ->
                        dragGoal = goalAtPosition(change.position.x, size.width.toFloat())
                        onDragTargetChange(dragGoal)
                    }
                }.semantics {
                    contentDescription = "Attendance goal"
                    progressBarRangeInfo = ProgressBarRangeInfo(threshold.toFloat(), 50f..95f, 8)
                    setProgress {
                        onThresholdChange(goalAtPosition(it, 100f))
                        true
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
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

internal fun goalAtPosition(
    position: Float,
    width: Float,
): Int = ((position / width * 100f / 5f).roundToInt() * 5).coerceIn(50, 95)

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
