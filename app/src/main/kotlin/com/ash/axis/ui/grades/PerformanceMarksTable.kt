package com.ash.axis.ui.grades

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.domain.model.CourseMarks
import com.ash.axis.domain.model.LectureMark
import com.ash.core.ui.theme.AppDimens
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.cardColor

@Suppress("LongMethod")
@Composable
internal fun PerformanceSummaryCard(
    courses: List<CourseMarks>,
    onClick: () -> Unit,
) {
    val marks =
        courses.flatMap { it.lectures }.mapNotNull { lecture ->
            val mark = lecture.markValue()
            if (mark?.max != null && mark.max > 0) mark.obtained to mark.max else null
        }
    val obtained = marks.sumOf { it.first }
    val max = marks.sumOf { it.second }
    val percent = if (max > 0) (obtained / max).coerceIn(0.0, 1.0) else 0.0

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(AppShapes.medium)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
    ) {
        Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Marks overview",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${courses.size} courses · ${marks.size} components",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (max > 0) "${(percent * 100).toInt()}%" else "-",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(percent.toFloat())
                            .height(8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
internal fun CourseMarksCard(
    course: CourseMarks,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = cardColor(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        course.subName.ifBlank { course.subShort },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (course.subCode.isNotBlank()) {
                        Text(
                            course.subCode,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (course.lectures.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))

                MarksHeader()

                Spacer(Modifier.height(4.dp))

                course.lectures.forEach { lec ->
                    MarksRow(lec = lec)
                    Spacer(Modifier.height(3.dp))
                }
            }
        }
    }
}

@Composable
internal fun MarksHeader() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        HeaderCell("Component", color, Modifier.weight(1.6f))
        HeaderCell(
            "Score",
            MaterialTheme.colorScheme.primary,
            Modifier.weight(1f),
            TextAlign.End,
        )
    }
}

@Composable
internal fun HeaderCell(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
    )
}

@Composable
internal fun MarksRow(lec: LectureMark) {
    val mark = lec.markValue()
    val score =
        when {
            mark != null && mark.max != null -> "${mark.obtained.cleanNumber()}/${mark.max.cleanNumber()}"
            mark != null -> mark.obtained.cleanNumber()
            else -> lec.obtainedMarks.ifBlank { "-" }
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            lec.lecType.ifBlank { "Component" },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            score,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}
