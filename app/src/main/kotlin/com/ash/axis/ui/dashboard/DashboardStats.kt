package com.ash.axis.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.domain.usecase.AttendanceTone
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.cardColor
import java.util.Locale

@Composable
internal fun StatsRow(data: DashboardUiState) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        StatCard(
            label = "OVERALL",
            value = String.format(Locale.US, "%.1f%%", data.overallPercent),
            subtitle = "${data.overallPresent}/${data.overallTotal} periods",
            tone = data.overallTone,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "BUNKABLE",
            value = data.totalBunkable.toString(),
            subtitle = "spare periods",
            tone = if (data.totalBunkable > 0) AttendanceTone.OK else AttendanceTone.WARN,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "AT RISK",
            value = data.atRiskCount.toString(),
            subtitle = "/${data.subjects.size} subjects",
            tone = if (data.atRiskCount > 0) AttendanceTone.BAD else AttendanceTone.OK,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    subtitle: String,
    tone: AttendanceTone,
    modifier: Modifier = Modifier,
) {
    val toneColor =
        when (tone) {
            AttendanceTone.OK -> MaterialTheme.colorScheme.primary
            AttendanceTone.WARN -> MaterialTheme.colorScheme.tertiary
            AttendanceTone.BAD -> MaterialTheme.colorScheme.error
        }

    Surface(
        modifier = modifier,
        shape = AppShapes.medium,
        color = cardColor(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-0.5).sp,
                color = toneColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
        )
    }
}
