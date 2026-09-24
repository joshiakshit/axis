package com.ash.axis.ui.timetable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FreeBreakfast
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.ash.axis.ui.BottomSpacer
import com.ash.core.ui.theme.AppDimens
import com.ash.core.ui.theme.AppShapes
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

private val emptyDayFormatter = DateTimeFormatter.ofPattern("EEEE, dd MMM", Locale.ENGLISH)

@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongParameterList")
@Composable
internal fun TimetableDayPager(
    anchor: LocalDate,
    pagerState: PagerState,
    dayCache: ImmutableMap<LocalDate, TimetableDay>,
    loadedWeeks: ImmutableSet<LocalDate>,
    failedWeeks: ImmutableSet<LocalDate>,
    onRetry: (LocalDate) -> Unit,
) {
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val pageOffset =
            ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
        val date = dateForPage(anchor, page)
        val weekStart = date.with(DayOfWeek.MONDAY)
        val isLoaded = weekStart in loadedWeeks
        val isFailed = weekStart in failedWeeks

        Box(
            modifier =
                Modifier.graphicsLayer {
                    alpha = lerp(0.5f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                },
        ) {
            when {
                isLoaded -> {
                    val day = dayCache[date]
                    if (day == null || day.items.none { it is TimetableItem.Slot }) {
                        EmptyDaySchedule(date, day?.holiday)
                    } else {
                        DaySlotList(day = day)
                    }
                }
                isFailed -> RetryDaySchedule(onRetry = { onRetry(date) })
                else -> LoadingDaySchedule()
            }
        }
    }
}

@Composable
private fun LoadingDaySchedule() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp)
    }
}

@Composable
private fun RetryDaySchedule(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Couldn't load this week",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun EmptyDaySchedule(
    date: LocalDate,
    holiday: String? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (holiday != null) "Holiday" else "No classes",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (holiday != null) {
                    "$holiday · ${date.format(emptyDayFormatter)}"
                } else {
                    "${date.format(emptyDayFormatter)} · free day"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DaySlotList(day: TimetableDay) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = AppDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.listItemSpacing),
    ) {
        item(contentType = "spacer") { Spacer(Modifier.height(4.dp)) }
        if (day.holiday != null) {
            item(contentType = "holiday_banner") {
                HolidayBanner(day.holiday)
            }
        }
        itemsIndexed(
            day.items,
            key = { index, item ->
                when (item) {
                    is TimetableItem.Slot -> "${index}_${item.display.slot.subjectId}_${item.display.slot.fromTime}"
                    is TimetableItem.Break -> "break_$index"
                }
            },
            contentType = { _, item ->
                when (item) {
                    is TimetableItem.Slot -> "slot_card"
                    is TimetableItem.Break -> "break_row"
                }
            },
        ) { _, item ->
            when (item) {
                is TimetableItem.Slot -> TimetableSlotCard(item.display, modifier = Modifier.animateItem())
                is TimetableItem.Break -> BreakRow(item)
            }
        }
        item(contentType = "footer") { BottomSpacer() }
    }
}

@Composable
private fun BreakRow(breakItem: TimetableItem.Break) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.FreeBreakfast,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                "Break",
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${breakItem.durationMinutes} min",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HolidayBanner(name: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
    ) {
        Text(
            "Holiday · $name",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}
