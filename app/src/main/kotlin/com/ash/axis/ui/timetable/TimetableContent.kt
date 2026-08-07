package com.ash.axis.ui.timetable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ash.core.ui.components.OfflineBanner
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import java.time.LocalDate
import kotlin.math.absoluteValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TimetableContent(
    data: TimetableUiState,
    viewModel: TimetableViewModel,
) {
    val anchor = data.anchorDate
    val pagerState =
        rememberPagerState(
            initialPage = pageForDate(anchor, data.currentDate),
            pageCount = { TimetablePaging.PAGE_COUNT },
        )

    // Report the settled page back so the header relabels and the week is fetched. Using settledPage
    // (not currentPage) avoids firing a fetch for every intermediate page during a long jump.
    LaunchedEffect(pagerState, anchor) {
        snapshotSettledPages(pagerState)
            .drop(1)
            .distinctUntilChanged()
            .collect { page -> viewModel.onDateShown(dateForPage(anchor, page)) }
    }

    // Honour a jump request: animate for nearby dates, snap instantly for far teleports.
    LaunchedEffect(data.jumpTarget) {
        val target = data.jumpTarget ?: return@LaunchedEffect
        val page = pageForDate(anchor, target)
        if (page != pagerState.currentPage) {
            if ((page - pagerState.currentPage).absoluteValue > TimetablePaging.NEARBY_PAGE_THRESHOLD) {
                pagerState.scrollToPage(page)
            } else {
                pagerState.animateScrollToPage(page)
            }
        }
        viewModel.consumeJump()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (data.isOffline) {
            OfflineBanner(visible = true)
        }
        TimetableHeader(
            currentDate = data.currentDate,
            onToday = { viewModel.jumpTo(LocalDate.now()) },
            onJump = viewModel::jumpTo,
        )
        DayStrip(
            anchor = anchor,
            currentDate = data.currentDate,
            dayCache = data.dayCache,
            onSelect = viewModel::jumpTo,
        )
        Spacer(Modifier.height(8.dp))
        TimetableDayPager(
            anchor = anchor,
            pagerState = pagerState,
            dayCache = data.dayCache,
            loadedWeeks = data.loadedWeeks,
            loadingWeeks = data.loadingWeeks,
            failedWeeks = data.failedWeeks,
            onNeedWeek = viewModel::ensureWeek,
            onRetry = viewModel::retryWeek,
        )
    }
}
