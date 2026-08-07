package com.ash.axis.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.core.ui.theme.AppDimens
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.highlightColor
import kotlinx.collections.immutable.ImmutableMap
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val headerDateFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.ENGLISH)

@Suppress("LongMethod")
@Composable
internal fun TimetableHeader(
    currentDate: LocalDate,
    onToday: () -> Unit,
    onJump: (LocalDate) -> Unit,
) {
    val isToday = currentDate == LocalDate.now()
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.screenPadding)
                .padding(top = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "Timetable",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                currentDate.format(headerDateFormatter),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!isToday) {
                Surface(
                    onClick = onToday,
                    shape = AppShapes.small,
                    color = highlightColor(alpha = 0.3f),
                ) {
                    Text(
                        "Today",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = { showPicker = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.DateRange,
                    contentDescription = "Jump to date",
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }

    if (showPicker) {
        JumpDatePicker(
            initialDate = currentDate,
            onConfirm = {
                onJump(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JumpDatePicker(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    } else {
                        onDismiss()
                    }
                },
            ) { Text("Go") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
internal fun DayStrip(
    anchor: LocalDate,
    currentDate: LocalDate,
    dayCache: ImmutableMap<LocalDate, TimetableDay>,
    onSelect: (LocalDate) -> Unit,
) {
    val listState = rememberLazyListState()
    val currentIndex = pageForDate(anchor, currentDate)

    // Keep the selected day comfortably in view (a couple of days of lead-in), following jumps/swipes.
    LaunchedEffect(currentIndex) {
        listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = AppDimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(TimetablePaging.PAGE_COUNT) { index ->
            val date = dateForPage(anchor, index)
            DayChip(
                date = date,
                selected = date == currentDate,
                hasClasses = dayCache[date]?.slots?.isNotEmpty() == true,
                isPast = date < LocalDate.now(),
                onClick = { onSelect(date) },
            )
        }
    }
}

private val dayInitialFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

@Composable
private fun DayChip(
    date: LocalDate,
    selected: Boolean,
    hasClasses: Boolean,
    isPast: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        when {
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        }
    val numberColor =
        when {
            selected -> MaterialTheme.colorScheme.onPrimary
            isPast -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.onSurface
        }
    val labelColor =
        when {
            selected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
            isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        modifier = Modifier.clip(AppShapes.full).clickable(onClick = onClick),
        shape = AppShapes.full,
        color = containerColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${date.dayOfMonth}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = numberColor,
            )
            Text(
                date.format(dayInitialFormatter),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = labelColor,
            )
            // Fixed-size slot keeps every chip the same height whether or not it shows a dot.
            Box(modifier = Modifier.padding(top = 3.dp).size(4.dp)) {
                if (hasClasses) {
                    Box(
                        modifier =
                            Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                ),
                    )
                }
            }
        }
    }
}
