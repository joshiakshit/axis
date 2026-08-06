package com.ash.axis.ui.timetable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
internal fun CoroutineScope.launchPage(
    pagerState: PagerState,
    page: Int,
) {
    launch { pagerState.animateScrollToPage(page) }
}
