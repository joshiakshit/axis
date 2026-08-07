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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.ash.axis.domain.model.TimetableSlot
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.cardColor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
@Composable
internal fun SimulatorGrid(
    month: LocalDate,
    selectedDates: ImmutableSet<LocalDate>,
    holidays: ImmutableSet<LocalDate>,
    anchorDate: LocalDate?,
    dateTimetable: ImmutableMap<LocalDate, ImmutableList<TimetableSlot>>,
    onPreview: (LocalDate) -> Unit,
    onMarkAbsent: (LocalDate) -> Unit,
    onShiftMonth: (Int) -> Unit,
) {
    val today = LocalDate.now()
    val monthStart = month.withDayOfMonth(1)
    val monthTitle = monthStart.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
    val cellDates =
        buildList {
            val firstOffset = monthStart.dayOfWeek.value - 1
            repeat(firstOffset) { add(null) }
            for (day in 1..monthStart.lengthOfMonth()) {
                add(monthStart.withDayOfMonth(day))
            }
            while (size % 7 != 0) add(null)
        }
    val weeks = cellDates.chunked(7)

    val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")
    val dateLabelFormatter = DateTimeFormatter.ofPattern("EEEE, dd MMM")

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
                IconButton(onClick = { onShiftMonth(1) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month",
                    )
                }
            }
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
                                anchorDate = anchorDate,
                                dateTimetable = dateTimetable,
                                dateLabelFormatter = dateLabelFormatter,
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

@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod", "ComplexCondition")
@Composable
private fun SimulatorDayCell(
    date: LocalDate,
    today: LocalDate,
    selectedDates: ImmutableSet<LocalDate>,
    holidays: ImmutableSet<LocalDate>,
    anchorDate: LocalDate?,
    dateTimetable: ImmutableMap<LocalDate, ImmutableList<TimetableSlot>>,
    dateLabelFormatter: DateTimeFormatter,
    onPreview: (LocalDate) -> Unit,
    onMarkAbsent: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val isPast = date < today
    val isToday = date == today
    val isSelected = date in selectedDates
    val isHoliday = date in holidays
    val isAnchor = !isSelected && !isHoliday && date == anchorDate
    // A day is selectable only if the real (future) timetable has at least one class that day.
    val hasClasses = dateTimetable[date]?.isNotEmpty() == true

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
    val label =
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
            if (hasClasses) append(", has classes") else append(", no classes")
            if (!isPast && hasClasses) append(", tap to mark, long press to preview")
        }

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(AppShapes.small)
                .background(bgColor)
                .border(1.5.dp, borderColor, AppShapes.small)
                .then(
                    if (!isPast && hasClasses) {
                        Modifier
                            .combinedClickable(
                                role = Role.Button,
                                onClick = { onMarkAbsent(date) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPreview(date)
                                },
                            )
                            .semantics { contentDescription = label }
                    } else {
                        Modifier.semantics { contentDescription = label }
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
            if (hasClasses && !isPast && !isSelected && !isHoliday && !isAnchor) {
                Box(
                    modifier =
                        Modifier.size(3.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
