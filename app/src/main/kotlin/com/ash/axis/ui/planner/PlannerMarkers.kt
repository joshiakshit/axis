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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.domain.model.StudentMarker
import com.ash.axis.domain.model.StudentMarkerType
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
    onAdd: (String, StudentMarkerType, LocalDate, LocalDate) -> Unit,
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
                Text("EXAMS & HOLIDAYS", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                TextButton(onClick = { showEditor = true }) { Text("Add") }
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
        StudentMarkerDialog(
            onAdd = { title, type, startDate, endDate ->
                onAdd(title, type, startDate, endDate)
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
                "Add exam and holiday dates you already know.",
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
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH) }
    val dateLabel =
        remember(marker.startDate, marker.endDate) {
            if (marker.startDate == marker.endDate) {
                marker.startDate.format(formatter)
            } else {
                "${marker.startDate.format(formatter)} – ${marker.endDate.format(formatter)}"
            }
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(marker.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$dateLabel · ${marker.type.label}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onDelete(marker.id) }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Remove ${marker.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StudentMarkerDialog(
    onAdd: (String, StudentMarkerType, LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(StudentMarkerType.EXAM) }
    var examTitle by remember { mutableStateOf("") }
    var holidayTitle by remember { mutableStateOf("") }
    // Each tab keeps its own dates, so switching back and forth does not wipe what you already picked.
    var examDates by remember { mutableStateOf(DateRangeSelection()) }
    var holidayDates by remember { mutableStateOf(DateRangeSelection()) }

    val isExam = type == StudentMarkerType.EXAM
    val title = if (isExam) examTitle else holidayTitle
    val selection = if (isExam) examDates else holidayDates
    val startDate = selection.start
    val endDate = selection.end ?: startDate

    AxisDialog(
        onDismiss = onDismiss,
        footer = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(
                enabled = title.isNotBlank() && startDate != null && endDate != null,
                onClick = { onAdd(title, type, startDate!!, endDate!!) },
            ) {
                Text("Save")
            }
        },
    ) {
        MarkerTypeTabs(selected = type, onSelect = { type = it })
        OutlinedTextField(
            value = title,
            onValueChange = { if (isExam) examTitle = it else holidayTitle = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (isExam) "Exam name" else "Holiday name") },
            singleLine = true,
        )
        AxisCalendar(
            selection = selection,
            onDateClick = { date ->
                if (isExam) examDates = examDates.toggle(date) else holidayDates = holidayDates.toggle(date)
            },
        )
        Text(
            markerDateSummary(startDate, endDate),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Marked days count as no-class days in your attendance forecast.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MarkerTypeTabs(
    selected: StudentMarkerType,
    onSelect: (StudentMarkerType) -> Unit,
) {
    val types = StudentMarkerType.entries
    TabRow(
        selectedTabIndex = types.indexOf(selected),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        indicator = { tabPositions ->
            if (selected.ordinal < tabPositions.size) {
                val pos = tabPositions[selected.ordinal]
                val inset = (pos.right - pos.left - 32.dp) / 2
                TabRowDefaults.SecondaryIndicator(
                    modifier =
                        Modifier
                            .tabIndicatorOffset(pos)
                            .padding(horizontal = inset.coerceAtLeast(0.dp)),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        divider = {},
        modifier = Modifier.fillMaxWidth(),
    ) {
        types.forEach { option ->
            val isSelected = option == selected
            Tab(
                modifier = Modifier.clip(AppShapes.medium),
                selected = isSelected,
                onClick = { onSelect(option) },
                text = {
                    Text(
                        if (option == StudentMarkerType.EXAM) "Exams" else "Holidays",
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 1,
                    )
                },
            )
        }
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
