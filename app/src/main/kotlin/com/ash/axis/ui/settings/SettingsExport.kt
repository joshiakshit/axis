package com.ash.axis.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.core.ui.theme.AppShapes

@Composable
internal fun ExportSettings(
    isExporting: Boolean,
    onExportAttendance: () -> Unit,
    onExportTimetable: () -> Unit,
) {
    SettingsCard {
        Column {
            AnimatedVisibility(visible = isExporting) {
                Column {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().clip(AppShapes.full),
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            ExportRow(
                label = "Export attendance",
                subtitle = "Per-subject CSV spreadsheet",
                enabled = !isExporting,
                onClick = onExportAttendance,
            )
            ExportRow(
                label = "Export timetable",
                subtitle = "The week you're viewing, as a calendar (.ics)",
                enabled = !isExporting,
                onClick = onExportTimetable,
            )
        }
    }
}

@Composable
private fun ExportRow(
    label: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(AppShapes.small)
                .clickable(enabled = enabled) { onClick() }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    },
            )
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.Filled.Share,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.6f else 0.25f),
            modifier = Modifier.size(18.dp),
        )
    }
}
