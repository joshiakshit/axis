package com.ash.axis.ui.timetable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalFoundationApi::class)
internal fun snapshotSettledPages(pagerState: PagerState): Flow<Int> = snapshotFlow { pagerState.settledPage }
