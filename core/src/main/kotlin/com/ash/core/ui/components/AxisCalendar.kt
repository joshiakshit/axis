package com.ash.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ash.core.ui.theme.AppDimens
import com.ash.core.ui.theme.AppShapes
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthTitleFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val cellLabelFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
private val weekdayNames = listOf("M", "T", "W", "T", "F", "S", "S")
private val monthNames =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val monthNumbers = (1..12).toList()

@Composable
fun AxisCalendar(
    selection: DateRangeSelection,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
) {
    val today = remember { LocalDate.now() }
    var month by remember {
        mutableStateOf(YearMonth.from(selection.start ?: minDate?.takeIf { it > today } ?: today))
    }
    var pickingMonth by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        CalendarHeader(
            month = month,
            pickingMonth = pickingMonth,
            canGoBack = month.minusMonths(1).isWithin(minDate, maxDate),
            canGoForward = month.plusMonths(1).isWithin(minDate, maxDate),
            onShift = { month = month.plusMonths(it.toLong()) },
            onTogglePicker = { pickingMonth = !pickingMonth },
        )
        Spacer(Modifier.height(6.dp))
        if (pickingMonth) {
            MonthYearPanel(
                month = month,
                minDate = minDate,
                maxDate = maxDate,
                onPick = {
                    month = it
                    pickingMonth = false
                },
            )
        } else {
            WeekdayHeader()
            calendarWeeks(month).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        if (date == null) {
                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            DayCell(
                                date = date,
                                selection = selection,
                                today = today,
                                enabled = date.isWithin(minDate, maxDate),
                                onClick = onDateClick,
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
private fun CalendarHeader(
    month: YearMonth,
    pickingMonth: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onShift: (Int) -> Unit,
    onTogglePicker: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { onShift(-1) }, enabled = canGoBack && !pickingMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Row(
            modifier =
                Modifier
                    .clip(AppShapes.small)
                    .clickable(onClick = onTogglePicker)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(month.format(monthTitleFormatter), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = if (pickingMonth) "Close month picker" else "Choose month and year",
            )
        }
        IconButton(onClick = { onShift(1) }, enabled = canGoForward && !pickingMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        weekdayNames.forEach { name ->
            Text(
                name,
                modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selection: DateRangeSelection,
    today: LocalDate,
    enabled: Boolean,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = bandPosition(selection, date)
    val isToday = date == today
    val style = dayCellStyle(position, isToday, enabled)

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(style.shape)
                .background(style.band)
                .then(
                    if (isToday && position == BandPosition.NONE) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, style.shape)
                    } else {
                        Modifier
                    },
                )
                .clickable(enabled = enabled) { onClick(date) }
                .semantics { contentDescription = dayCellLabel(date, position, isToday) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            fontSize = 13.sp,
            fontWeight = if (style.emphasised) FontWeight.Bold else FontWeight.Normal,
            color = style.text,
        )
    }
}

private data class DayCellStyle(
    val band: Color,
    val text: Color,
    val shape: Shape,
    val emphasised: Boolean,
)

@Composable
private fun dayCellStyle(
    position: BandPosition,
    isToday: Boolean,
    enabled: Boolean,
): DayCellStyle {
    val primary = MaterialTheme.colorScheme.primary
    val isEndpoint = position != BandPosition.NONE && position != BandPosition.MIDDLE
    val band =
        when (position) {
            BandPosition.NONE -> Color.Transparent
            BandPosition.MIDDLE -> primary.copy(alpha = 0.18f)
            else -> primary
        }
    val text =
        when {
            isEndpoint -> MaterialTheme.colorScheme.onPrimary
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            isToday -> primary
            else -> MaterialTheme.colorScheme.onSurface
        }
    return DayCellStyle(band, text, bandShape(position), isEndpoint || isToday)
}

private fun dayCellLabel(
    date: LocalDate,
    position: BandPosition,
    isToday: Boolean,
): String =
    buildString {
        append(date.format(cellLabelFormatter))
        if (isToday) append(", today")
        if (position != BandPosition.NONE) append(", selected")
    }

@Composable
private fun MonthYearPanel(
    month: YearMonth,
    minDate: LocalDate?,
    maxDate: LocalDate?,
    onPick: (YearMonth) -> Unit,
) {
    var year by remember(month.year) { mutableStateOf(month.year) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = { year -= 1 },
                enabled = monthNumbers.any { YearMonth.of(year - 1, it).isWithin(minDate, maxDate) },
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous year")
            }
            Text("$year", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            IconButton(
                onClick = { year += 1 },
                enabled = monthNumbers.any { YearMonth.of(year + 1, it).isWithin(minDate, maxDate) },
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next year")
            }
        }
        monthNumbers.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { number ->
                    val candidate = YearMonth.of(year, number)
                    MonthCell(
                        name = monthNames[number - 1],
                        selected = candidate == month,
                        enabled = candidate.isWithin(minDate, maxDate),
                        onClick = { onPick(candidate) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthCell(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .padding(3.dp)
                .clip(AppShapes.small)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color =
                when {
                    selected -> MaterialTheme.colorScheme.onPrimary
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

@Composable
fun AxisDialog(
    onDismiss: () -> Unit,
    footer: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = footer,
                )
            }
        }
    }
}

@Composable
fun AxisDatePickerDialog(
    title: String,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialDate: LocalDate? = null,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
) {
    var picked by remember { mutableStateOf(initialDate) }
    AxisDialog(
        onDismiss = onDismiss,
        modifier = modifier,
        footer = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(enabled = picked != null, onClick = { picked?.let(onConfirm) }) { Text("Set") }
        },
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        AxisCalendar(
            selection = DateRangeSelection(picked, picked),
            onDateClick = { picked = it },
            minDate = minDate,
            maxDate = maxDate,
        )
    }
}

private enum class BandPosition { NONE, SINGLE, START, MIDDLE, END }

private fun bandPosition(
    selection: DateRangeSelection,
    date: LocalDate,
): BandPosition {
    if (!selection.covers(date)) return BandPosition.NONE
    val start = selection.start ?: return BandPosition.NONE
    val end = selection.end ?: return BandPosition.SINGLE
    return when {
        start == end -> BandPosition.SINGLE
        date == start -> BandPosition.START
        date == end -> BandPosition.END
        else -> BandPosition.MIDDLE
    }
}

private fun bandShape(position: BandPosition): Shape {
    val radius = AppDimens.radiusSm
    return when (position) {
        BandPosition.SINGLE -> RoundedCornerShape(radius)
        BandPosition.START -> RoundedCornerShape(topStart = radius, bottomStart = radius)
        BandPosition.END -> RoundedCornerShape(topEnd = radius, bottomEnd = radius)
        BandPosition.MIDDLE -> RoundedCornerShape(0.dp)
        BandPosition.NONE -> RoundedCornerShape(radius)
    }
}

private fun LocalDate.isWithin(
    minDate: LocalDate?,
    maxDate: LocalDate?,
): Boolean = (minDate == null || this >= minDate) && (maxDate == null || this <= maxDate)

private fun YearMonth.isWithin(
    minDate: LocalDate?,
    maxDate: LocalDate?,
): Boolean = (minDate == null || atEndOfMonth() >= minDate) && (maxDate == null || atDay(1) <= maxDate)
