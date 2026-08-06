package com.ash.axis.ui.timetable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ash.core.ui.components.OfflineBanner

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TimetableContent(
    data: TimetableUiState,
    viewModel: TimetableViewModel,
) {
    val initialPage = data.todayIndex.coerceIn(0, data.days.lastIndex)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { data.days.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        if (data.isOffline) {
            OfflineBanner(visible = true)
        }
        TimetableHeader(
            weekLabel = data.weekLabel,
            isCurrentWeek = data.isCurrentWeek,
            onPrev = { viewModel.shiftWeek(-1) },
            onNext = { viewModel.shiftWeek(1) },
            onReset = viewModel::resetToCurrentWeek,
        )
        DaySelectorRow(
            days = data.days,
            currentPage = pagerState.currentPage,
            onSelect = { page -> scope.launchPage(pagerState, page) },
        )

        Spacer(Modifier.height(8.dp))

        TimetableDayPager(
            days = data.days,
            pagerState = pagerState,
        )
    }
}
