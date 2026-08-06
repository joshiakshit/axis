package com.ash.axis.ui.academics

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ash.axis.ui.attendance.AttendanceScreen
import com.ash.axis.ui.daywise.DaywiseScreen
import com.ash.axis.ui.planner.PlannerScreen
import com.ash.core.ui.theme.AppShapes
import kotlinx.coroutines.launch

private val tabs = listOf("Overall", "Day-wise", "Planner")

@Suppress("LongMethod")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AcademicsScreen(modifier: Modifier = Modifier) {
    var savedPage by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = savedPage) { tabs.size }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(pagerState.settledPage) {
        if (savedPage != pagerState.settledPage) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        savedPage = pagerState.settledPage
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                if (pagerState.currentPage < tabPositions.size) {
                    val pos = tabPositions[pagerState.currentPage]
                    val inset = (pos.right - pos.left - 32.dp) / 2
                    TabRowDefaults.SecondaryIndicator(
                        modifier =
                            Modifier
                                .tabIndicatorOffset(pos)
                                .padding(horizontal = inset.coerceAtLeast(0.dp)),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            divider = {},
            modifier = Modifier.fillMaxWidth(),
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = pagerState.currentPage == index
                Tab(
                    modifier = Modifier.clip(AppShapes.medium),
                    selected = selected,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                index,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            )
                        }
                    },
                    text = {
                        Text(
                            title,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color =
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 ->
                    AttendanceScreen(
                        modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                    )
                1 ->
                    DaywiseScreen(
                        modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                        isVisible = pagerState.settledPage == 1,
                    )
                2 ->
                    PlannerScreen(
                        modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                    )
            }
        }
    }
}
