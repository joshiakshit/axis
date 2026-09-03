package com.ash.axis.ui.planner

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.domain.model.StudentMarker
import com.ash.axis.domain.model.StudentMarkerType
import com.ash.axis.domain.model.TimetableSlot
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.cardColor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthTitleFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val dateLabelFormatter = DateTimeFormatter.ofPattern("EEEE, dd MMM", Locale.ENGLISH)
private val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")

private fun simulatorWeeks(monthStart: LocalDate): List<List<LocalDate?>> =
    buildList<LocalDate?> {
        repeat(monthStart.dayOfWeek.value - 1) { add(null) }
        for (day in 1..monthStart.lengthOfMonth()) add(monthStart.withDayOfMonth(day))
        while (size % 7 != 0) add(null)
    }.chunked(7)

private fun markerTypesByDate(
    markers: List<StudentMarker>,
    monthStart: LocalDate,
): Map<LocalDate, Set<StudentMarkerType>> {
    val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
    val byDate = mutableMapOf<LocalDate, MutableSet<StudentMarkerType>>()
    markers.forEach { marker ->
        var date = maxOf(marker.startDate, monthStart)
        val last = minOf(marker.endDate, monthEnd)
        while (date <= last) {
            byDate.getOrPut(date) { mutableSetOf() } += marker.type
            date = date.plusDays(1)
        }
    }
    return byDate
}

@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
@Composable
internal fun SimulatorGrid(
    month: LocalDate,
    selectedDates: ImmutableSet<LocalDate>,
    holidays: ImmutableSet<LocalDate>,
    markers: ImmutableList<StudentMarker>,
    holidayMode: Boolean,
    anchorDate: LocalDate?,
    dateTimetable: ImmutableMap<LocalDate, ImmutableList<TimetableSlot>>,
    semesterEndDate: LocalDate?,
    interactionEnabled: Boolean,
    onPreview: (LocalDate) -> Unit,
    onMarkAbsent: (LocalDate) -> Unit,
    onShiftMonth: (Int) -> Unit,
    onToggleHolidayMode: () -> Unit,
    onClear: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val monthStart = month.withDayOfMonth(1)
    val monthTitle = remember(monthStart) { monthStart.format(monthTitleFormatter) }
    val weeks = remember(monthStart) { simulatorWeeks(monthStart) }
    // One pass over the markers here beats filtering the whole list inside all 42 day cells.
    val markersByDate = remember(markers, monthStart) { markerTypesByDate(markers, monthStart) }

    Surface(
        shape = AppShapes.medium,
        color = cardColor(),
    ) {
        Column(modifier = Modifier.animateContentSize().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { onShiftMonth(-1) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month",
                    )
                }
                Text(monthTitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                IconButton(
                    onClick = { onShiftMonth(1) },
                    enabled = semesterEndDate == null || monthStart.plusMonths(1) <= semesterEndDate.withDayOfMonth(1),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month",
                    )
                }
            }
            SimulatorControls(
                holidayMode = holidayMode,
                selectedCount = selectedDates.size,
                noClassCount = holidays.size,
                hasPreview = anchorDate != null,
                interactionEnabled = interactionEnabled,
                onToggleHolidayMode = onToggleHolidayMode,
                onClear = onClear,
            )
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                dayNames.forEach { name ->
                    Text(
                        name,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            weeks.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { date ->
                        if (date == null) {
                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            SimulatorDayCell(
                                date = date,
                                today = today,
                                selectedDates = selectedDates,
                                holidays = holidays,
                                markerTypes = markersByDate[date].orEmpty(),
                                anchorDate = anchorDate,
                                dateTimetable = dateTimetable,
                                semesterEndDate = semesterEndDate,
                                interactionEnabled = interactionEnabled,
                                onPreview = onPreview,
                                onMarkAbsent = onMarkAbsent,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimulatorControls(
    holidayMode: Boolean,
    selectedCount: Int,
    noClassCount: Int,
    hasPreview: Boolean,
    interactionEnabled: Boolean,
    onToggleHolidayMode: () -> Unit,
    onClear: () -> Unit,
) {
    val hasSelection = selectedCount > 0 || noClassCount > 0 || hasPreview
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = !holidayMode,
            onClick = { if (holidayMode) onToggleHolidayMode() },
            label = { Text("Skip", fontSize = 12.sp) },
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
        )
        FilterChip(
            selected = holidayMode,
            onClick = { if (!holidayMode) onToggleHolidayMode() },
            label = { Text("No class", fontSize = 12.sp) },
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
        )
        Spacer(Modifier.weight(1f))
        if (hasSelection) {
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
    Text(
        when {
            !interactionEnabled -> "Answer today's attendance to start planning."
            selectedCount > 0 || noClassCount > 0 ->
                buildString {
                    if (selectedCount > 0) append("$selectedCount skipped")
                    if (noClassCount > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("$noClassCount no-class")
                    }
                }
            hasPreview -> "Previewing attendance through the selected day"
            else -> "Tap class days to simulate. Hold a day to preview."
        },
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod", "ComplexCondition")
@Composable
private fun SimulatorDayCell(
    date: LocalDate,
    today: LocalDate,
    selectedDates: ImmutableSet<LocalDate>,
    holidays: ImmutableSet<LocalDate>,
    markerTypes: Set<StudentMarkerType>,
    anchorDate: LocalDate?,
    dateTimetable: ImmutableMap<LocalDate, ImmutableList<TimetableSlot>>,
    semesterEndDate: LocalDate?,
    interactionEnabled: Boolean,
    onPreview: (LocalDate) -> Unit,
    onMarkAbsent: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val isPast = date < today
    val isToday = date == today
    val isSelected = date in selectedDates
    val isHoliday = date in holidays
    val isMarkerHoliday = StudentMarkerType.HOLIDAY in markerTypes
    val isAnchor = !isSelected && !isHoliday && date == anchorDate
    val hasClasses = dateTimetable[date]?.isNotEmpty() == true && !isMarkerHoliday
    val isWithinSemester = semesterEndDate == null || date <= semesterEndDate
    val canInteract = interactionEnabled && !isPast && isWithinSemester && hasClasses

    val bgColor =
        when {
            isHoliday -> MaterialTheme.colorScheme.tertiaryContainer
            isSelected -> MaterialTheme.colorScheme.error
            isAnchor -> MaterialTheme.colorScheme.primaryContainer
            isToday -> MaterialTheme.colorScheme.surfaceVariant
            else -> Color.Transparent
        }
    val textColor =
        when {
            isHoliday -> MaterialTheme.colorScheme.onTertiaryContainer
            isSelected -> MaterialTheme.colorScheme.onError
            isAnchor -> MaterialTheme.colorScheme.onPrimaryContainer
            isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            isToday -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        }
    val borderColor =
        when {
            isAnchor -> MaterialTheme.colorScheme.primary
            isToday && !isSelected && !isHoliday -> MaterialTheme.colorScheme.primary
            else -> Color.Transparent
        }
    val label = {
        buildString {
            append(date.format(dateLabelFormatter))
            if (isToday) append(", today")
            if (isHoliday) {
                append(", holiday")
            } else if (isSelected) {
                append(", marked absent")
            } else if (isAnchor) {
                append(", preview selected")
            }
            markerTypes.forEach { append(", ${it.label.lowercase()}") }
            if (hasClasses) append(", has classes") else append(", no classes")
            if (canInteract) append(", tap to mark, long press to preview")
        }
    }

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(AppShapes.small)
                .background(bgColor)
                .border(1.5.dp, borderColor, AppShapes.small)
                .then(
                    if (canInteract) {
                        Modifier
                            .combinedClickable(
                                role = Role.Button,
                                onClick = {
                                    if (isToday) onPreview(date) else onMarkAbsent(date)
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPreview(date)
                                },
                            )
                            .semantics { contentDescription = label() }
                    } else {
                        Modifier.semantics { contentDescription = label() }
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${date.dayOfMonth}",
                fontSize = 12.sp,
                fontWeight = if (isToday || isSelected || isAnchor) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (hasClasses && !isPast && isWithinSemester && !isSelected && !isHoliday && !isAnchor) {
                    MarkerDot(MaterialTheme.colorScheme.primary)
                }
                markerTypes.forEach { type -> MarkerDot(markerColor(type)) }
            }
        }
    }
}

@Composable
private fun markerColor(type: StudentMarkerType): Color =
    when (type) {
        StudentMarkerType.EXAM -> MaterialTheme.colorScheme.error
        StudentMarkerType.HOLIDAY -> MaterialTheme.colorScheme.tertiary
    }

@Composable
private fun MarkerDot(color: Color) {
    Box(
        modifier =
            Modifier
                .size(3.dp)
                .clip(CircleShape)
                .background(color),
    )
}
