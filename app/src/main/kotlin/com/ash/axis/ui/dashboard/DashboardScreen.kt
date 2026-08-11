package com.ash.axis.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.axis.domain.usecase.AttendanceTone
import com.ash.axis.ui.AppFooter
import com.ash.core.ui.components.LoadingStateContainer
import com.ash.core.ui.components.OfflineBanner
import com.ash.core.ui.components.PullToRefreshContainer
import com.ash.core.ui.theme.AppDimens
import com.ash.core.util.Result

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Re-read the shared cache whenever Home comes back to the foreground, so a refresh done on the Attendance
    // tab (or a QR mark) shows here instead of a stale snapshot. The ViewModel coalesces this with its initial
    // load, so the first launch doesn't double-fetch.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.syncFromCache() }

    val result: Result<DashboardUiState> =
        when {
            state.isLoading -> Result.Loading
            state.error != null && state.subjects.isEmpty() -> Result.Error(Exception(state.error), state.error)
            else -> Result.Success(state)
        }

    LoadingStateContainer(result = result, modifier = modifier, onRetry = viewModel::refresh) { data ->
        PullToRefreshContainer(
            isRefreshing = data.isRefreshing,
            onRefresh = viewModel::refresh,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = AppDimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(AppDimens.listItemSpacing),
            ) {
                if (data.isOffline) {
                    item(contentType = "offline_banner") { OfflineBanner(visible = true) }
                }
                item(contentType = "spacer") { Spacer(Modifier.height(14.dp)) }
                item(contentType = "greeting") { GreetingHeader(data.firstName) }
                data.nextClass?.let { next ->
                    item(contentType = "next_class") { NextClassCard(next) }
                }
                if (data.subjects.isNotEmpty()) {
                    item(contentType = "stats_row") { StatsRow(data) }
                }

                item(contentType = "section_label") {
                    SectionLabel(
                        buildString {
                            append("TODAY'S SCHEDULE")
                            if (data.todaySlots.isNotEmpty()) {
                                append(" · ${data.todaySlots.size}")
                            }
                        },
                    )
                }
                if (data.todaySlots.isNotEmpty()) {
                    item(contentType = "timeline") { TimelineCard(data.todaySlots) }
                } else {
                    item(contentType = "empty_day") { NoClassesToday() }
                }

                val atRiskSubjects = data.subjects.filter { it.tone != AttendanceTone.OK }
                if (atRiskSubjects.isNotEmpty()) {
                    item(contentType = "section_label") {
                        SectionLabel("AT RISK · ${atRiskSubjects.size}")
                    }
                    item(contentType = "subject_list") { SubjectSummaryList(atRiskSubjects) }
                }

                item(contentType = "footer") { AppFooter() }
            }
        }
    }
}
