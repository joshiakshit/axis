package com.ash.axis.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.domain.model.StudentMarker
import com.ash.core.ui.components.AxisCalendar
import com.ash.core.ui.components.AxisDialog
import com.ash.core.ui.components.DateRangeSelection
import com.ash.core.ui.components.toggle
import com.ash.core.ui.theme.AppDimens
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.cardColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun PlannerMarkersSection(
    markers: List<StudentMarker>,
    onAdd: (LocalDate, LocalDate) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var showEditor by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val preview = remember(markers, today, expanded) { plannerMarkerPreview(markers, today, expanded) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = cardColor(),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("NO-CLASS DAYS", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                TextButton(onClick = { showEditor = true }) { Text("Mark days") }
            }
            MarkerListBody(markers, preview, onDelete)
            if (preview.hiddenCount > 0 || expanded) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Show upcoming only" else "Show all (${markers.size})")
                }
            }
        }
    }
    if (showEditor) {
        NoClassDaysDialog(
            onAdd = { startDate, endDate ->
                onAdd(startDate, endDate)
                showEditor = false
            },
            onDismiss = { showEditor = false },
        )
    }
}

@Composable
private fun MarkerListBody(
    markers: List<StudentMarker>,
    preview: PlannerMarkerPreview,
    onDelete: (Long) -> Unit,
) {
    when {
        markers.isEmpty() ->
            Text(
                "Mark days without classes to exclude them from the forecast.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        preview.visible.isEmpty() ->
            Text(
                "Nothing upcoming.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        else ->
            preview.visible.forEach { marker ->
                StudentMarkerRow(marker, onDelete)
            }
    }
}

@Composable
private fun StudentMarkerRow(
    marker: StudentMarker,
    onDelete: (Long) -> Unit,
) {
    val dateLabel = remember(marker.startDate, marker.endDate) { noClassDateLabel(marker) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(dateLabel, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { onDelete(marker.id) }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Remove no-class days $dateLabel",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoClassDaysDialog(
    onAdd: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var selection by remember { mutableStateOf(DateRangeSelection()) }
    val startDate = selection.start
    val endDate = selection.end ?: startDate

    AxisDialog(
        onDismiss = onDismiss,
        footer = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(
                enabled = startDate != null,
                onClick = { startDate?.let { onAdd(it, endDate ?: it) } },
            ) {
                Text("Save")
            }
        },
    ) {
        Text("Mark no-class days", style = MaterialTheme.typography.titleMedium)
        Text("Pick one date, or two dates for a range.", style = MaterialTheme.typography.bodySmall)
        AxisCalendar(
            selection = selection,
            onDateClick = { selection = selection.toggle(it) },
        )
        Text(
            markerDateSummary(startDate, endDate),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun noClassDateLabel(marker: StudentMarker): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    return if (marker.startDate == marker.endDate) {
        marker.startDate.format(formatter)
    } else {
        "${marker.startDate.format(formatter)} – ${marker.endDate.format(formatter)}"
    }
}

internal fun markerDateSummary(
    startDate: LocalDate?,
    endDate: LocalDate?,
): String {
    val formatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)
    return when {
        startDate == null -> "Pick a date, or tap a second one for a range"
        endDate == null || endDate == startDate -> startDate.format(formatter)
        else -> "${startDate.format(formatter)} - ${endDate.format(formatter)}"
    }
}

internal data class PlannerMarkerPreview(
    val visible: List<StudentMarker>,
    val hiddenCount: Int,
)

internal fun plannerMarkerPreview(
    markers: List<StudentMarker>,
    today: LocalDate,
    expanded: Boolean,
    collapsedLimit: Int = 3,
): PlannerMarkerPreview {
    val upcoming = markers.filter { !it.endDate.isBefore(today) }.sortedBy { it.startDate }
    val past = markers.filter { it.endDate.isBefore(today) }.sortedByDescending { it.endDate }
    val visible = if (expanded) upcoming + past else upcoming.take(collapsedLimit)
    return PlannerMarkerPreview(visible, markers.size - visible.size)
}
