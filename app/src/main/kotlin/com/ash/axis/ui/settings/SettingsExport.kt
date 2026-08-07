package com.ash.axis.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    onShareAttendance: () -> Unit,
    onDownloadAttendance: () -> Unit,
    onShareTimetable: () -> Unit,
    onDownloadTimetable: () -> Unit,
) {
    SettingsCard {
        Column {
            AnimatedVisibility(visible = isExporting) {
                Column {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(AppShapes.full))
                    Spacer(Modifier.height(8.dp))
                }
            }
            ExportRow(
                label = "Export attendance",
                subtitle = "Share CSV · download PDF",
                enabled = !isExporting,
                onShare = onShareAttendance,
                onDownload = onDownloadAttendance,
            )
            ExportRow(
                label = "Export timetable",
                subtitle = "The week you're viewing · share ICS · download PDF",
                enabled = !isExporting,
                onShare = onShareTimetable,
                onDownload = onDownloadTimetable,
            )
        }
    }
}

@Composable
private fun ExportRow(
    label: String,
    subtitle: String,
    enabled: Boolean,
    onShare: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
        IconButton(onClick = onShare, enabled = enabled) {
            Icon(
                Icons.Filled.Share,
                contentDescription = "Share",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onDownload, enabled = enabled) {
            Icon(
                Icons.Filled.Download,
                contentDescription = "Download PDF",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
