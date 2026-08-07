package com.ash.axis.ui.timetable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow

// Emits the pager's page only once it has settled, so callers don't react to every intermediate
// page that flashes past during an animated jump.
@OptIn(ExperimentalFoundationApi::class)
internal fun snapshotSettledPages(pagerState: PagerState): Flow<Int> = snapshotFlow { pagerState.settledPage }
