package com.ash.axis.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.domain.usecase.AttendanceTone
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.SubjectColors
import com.ash.core.ui.theme.cardColor
import java.util.Locale

@Composable
internal fun SubjectSummaryList(subjects: List<DashboardSubject>) {
    val sorted = remember(subjects) { subjects.sortedBy { it.percent } }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = cardColor(),
    ) {
        Column {
            sorted.forEachIndexed { index, subject ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                SubjectMiniRow(subject)
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun SubjectMiniRow(subject: DashboardSubject) {
    val toneColor =
        when (subject.tone) {
            AttendanceTone.OK -> MaterialTheme.colorScheme.primary
            AttendanceTone.WARN -> MaterialTheme.colorScheme.tertiary
            AttendanceTone.BAD -> MaterialTheme.colorScheme.error
        }
    val tagColor = SubjectColors.accent(subject.subCode)
    val typeBadge =
        when {
            subject.lecType.equals("PP+PR", ignoreCase = true) -> "LEC+LAB"
            subject.lecType.equals("PR", ignoreCase = true) -> "LAB"
            else -> "LEC"
        }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(8.dp).clip(CircleShape).background(tagColor),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    subject.subCode,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = AppShapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Text(
                        typeBadge,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                subject.subName.ifBlank { subject.subCode },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "${subject.present}/${subject.total}",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "${String.format(Locale.US, "%.2f", subject.percent)}%",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = toneColor,
        )
    }
}
