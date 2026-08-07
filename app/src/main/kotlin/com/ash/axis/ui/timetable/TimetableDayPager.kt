package com.ash.axis.ui.timetable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.ash.axis.ui.AppFooter
import com.ash.core.ui.theme.AppDimens
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
    loadingWeeks: ImmutableSet<LocalDate>,
    failedWeeks: ImmutableSet<LocalDate>,
    onNeedWeek: (LocalDate) -> Unit,
    onRetry: (LocalDate) -> Unit,
) {
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val pageOffset =
            ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
        val date = dateForPage(anchor, page)
        val weekStart = date.with(DayOfWeek.MONDAY)
        val isLoaded = weekStart in loadedWeeks
        val isLoading = weekStart in loadingWeeks
        val isFailed = weekStart in failedWeeks

        // Fetch on demand as pages compose (covers swipe-ahead beyond what the ViewModel prefetched).
        LaunchedEffect(weekStart, isLoaded, isLoading, isFailed) {
            if (!isLoaded && !isLoading && !isFailed) onNeedWeek(date)
        }

        Box(
            modifier =
                Modifier.graphicsLayer {
                    alpha = lerp(0.5f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                },
        ) {
            when {
                isLoaded -> {
                    val day = dayCache[date]
                    if (day == null || day.slots.isEmpty()) {
                        EmptyDaySchedule(date)
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
private fun EmptyDaySchedule(date: LocalDate) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No classes",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${date.format(emptyDayFormatter)} · free day",
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
        items(
            day.slots,
            key = { "${it.slot.subjectId}_${it.slot.fromTime}" },
            contentType = { "slot_card" },
        ) { displaySlot ->
            TimetableSlotCard(displaySlot, modifier = Modifier.animateItem())
        }
        item(contentType = "footer") { AppFooter() }
    }
}
