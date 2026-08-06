package com.ash.axis.ui.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.domain.usecase.AttendanceTone
import com.ash.core.ui.components.StatusBadge
import com.ash.core.ui.theme.AppDimens
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.SubjectColors
import com.ash.core.ui.theme.cardColor
import java.util.Locale

@Composable
internal fun SubjectRow(
    decorated: DecoratedSubject,
    threshold: Int,
    modifier: Modifier = Modifier,
) {
    val toneColor = decorated.tone.subjectToneColor()
    val progress = decorated.subject.progressFraction()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = cardColor(),
    ) {
        Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
            SubjectRowHeader(decorated, toneColor)
            Spacer(Modifier.height(8.dp))
            Text(
                decorated.subject.subName.ifBlank { decorated.subject.subCode },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(AppDimens.progressBarHeight).clip(AppShapes.full),
                color = toneColor,
                trackColor = toneColor.copy(alpha = 0.1f),
            )
            Spacer(Modifier.height(8.dp))
            SubjectRowFooter(decorated, threshold)
        }
    }
}

@Composable
private fun SubjectRowHeader(
    decorated: DecoratedSubject,
    toneColor: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(SubjectColors.accent(decorated.subject.subCode)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            decorated.subject.subCode,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        StatusBadge(decorated.tone.toneBadge(), color = toneColor, background = toneColor.copy(alpha = 0.15f))
        Spacer(Modifier.width(4.dp))
        StatusBadge(decorated.subject.lecType.typeBadge())
        Spacer(Modifier.weight(1f))
        val pctText = remember(decorated.subject.percent) { "${String.format(Locale.US, "%.2f", decorated.subject.percent)}%" }
        Text(
            pctText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = toneColor,
        )
    }
}

@Composable
private fun SubjectRowFooter(
    decorated: DecoratedSubject,
    threshold: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${decorated.subject.present}/${decorated.subject.total} periods",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SubjectActionText(decorated, threshold)
    }
}

@Composable
private fun SubjectActionText(
    decorated: DecoratedSubject,
    threshold: Int,
) {
    when {
        decorated.need > 0 ->
            Text(
                "Need ${decorated.need} more to reach $threshold%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
            )
        decorated.bunkable > 0 ->
            Text(
                "Can skip ${decorated.bunkable} more",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
    }
}

@Composable
private fun AttendanceTone.subjectToneColor(): Color =
    when (this) {
        AttendanceTone.OK -> MaterialTheme.colorScheme.primary
        AttendanceTone.WARN -> MaterialTheme.colorScheme.tertiary
        AttendanceTone.BAD -> MaterialTheme.colorScheme.error
    }

private fun AttendanceTone.toneBadge(): String =
    when (this) {
        AttendanceTone.OK -> "SAFE"
        AttendanceTone.WARN -> "WARN"
        AttendanceTone.BAD -> "DANGER"
    }

private fun String.typeBadge(): String =
    when {
        equals("PP+PR", ignoreCase = true) -> "LEC+LAB"
        equals("PR", ignoreCase = true) -> "LAB"
        else -> "LEC"
    }

private fun com.ash.axis.domain.usecase.SubjectAttendance.progressFraction(): Float =
    if (total > 0) {
        (percent / 100.0).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
