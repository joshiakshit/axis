package com.ash.axis.ui.grades

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.core.ui.components.LoadingStateContainer
import com.ash.core.ui.components.PullToRefreshContainer
import com.ash.core.ui.theme.AppShapes
import com.ash.core.util.Result
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Suppress("LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GradesScreen(
    modifier: Modifier = Modifier,
    viewModel: GradesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tabs = GradeTab.entries
    val pagerState = rememberPagerState(initialPage = state.selectedTab.ordinal) { tabs.size }
    val scope = rememberCoroutineScope()
    val performanceListState = rememberLazyListState()
    val resultListState = rememberLazyListState()

    val currentTabFromPager by remember {
        derivedStateOf {
            val offset = pagerState.currentPageOffsetFraction
            if (offset > 0.5f) {
                tabs.getOrNull(pagerState.currentPage + 1) ?: tabs[pagerState.currentPage]
            } else if (offset < -0.5f) {
                tabs.getOrNull(pagerState.currentPage - 1) ?: tabs[pagerState.currentPage]
            } else {
                tabs[pagerState.currentPage]
            }
        }
    }

    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.settledPage }.collectLatest { page ->
            val tab = tabs.getOrNull(page) ?: return@collectLatest
            if (state.selectedTab != tab) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.selectTab(tab)
            }
        }
    }

    if (state.pdfFile != null) {
        Dialog(
            onDismissRequest = viewModel::dismissPdf,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            PdfViewerScreen(
                file = state.pdfFile!!,
                onBack = viewModel::dismissPdf,
                onShare = viewModel::sharePdf,
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        GradesTabBar(
            selectedTab = currentTabFromPager,
            onTabSelected = { tab ->
                scope.launch {
                    pagerState.animateScrollToPage(
                        tab.ordinal,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    )
                }
            },
        )

        PullToRefreshContainer(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.weight(1f),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                when (tabs[page]) {
                    GradeTab.PERFORMANCE -> {
                        val result: Result<GradesUiState> =
                            when {
                                state.performanceLoading && state.performanceYears.isEmpty() -> Result.Loading
                                state.performanceError != null && state.performanceYears.isEmpty() &&
                                    state.courses.isEmpty() ->
                                    Result.Error(Exception(state.performanceError), state.performanceError)
                                else -> Result.Success(state)
                            }
                        LoadingStateContainer(result = result, onRetry = viewModel::refresh) { data ->
                            PerformanceContent(
                                data = data,
                                viewModel = viewModel,
                                listState = performanceListState,
                            )
                        }
                    }
                    GradeTab.RESULT -> {
                        val result: Result<GradesUiState> =
                            when {
                                state.isLoading -> Result.Loading
                                state.error != null && state.reportCards.isEmpty() &&
                                    state.examSessions.isEmpty() && state.semesterNumbers.isEmpty() ->
                                    Result.Error(Exception(state.error), state.error)
                                else -> Result.Success(state)
                            }
                        LoadingStateContainer(result = result, onRetry = viewModel::refresh) { data ->
                            ResultContent(
                                data = data,
                                viewModel = viewModel,
                                listState = resultListState,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradesTabBar(
    selectedTab: GradeTab,
    onTabSelected: (GradeTab) -> Unit,
) {
    val tabs = GradeTab.entries
    val tabLabels = listOf("Performance", "Result")

    TabRow(
        selectedTabIndex = tabs.indexOf(selectedTab),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        indicator = { tabPositions ->
            if (selectedTab.ordinal < tabPositions.size) {
                val pos = tabPositions[selectedTab.ordinal]
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
        tabs.forEachIndexed { index, tab ->
            val selected = selectedTab == tab
            Tab(
                modifier = Modifier.clip(AppShapes.medium),
                selected = selected,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        tabLabels[index],
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 1,
                    )
                },
            )
        }
    }
}
