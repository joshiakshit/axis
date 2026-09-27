package com.ash.axis.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.axis.ui.BottomSpacer
import com.ash.core.ui.components.AppSectionLabel
import com.ash.core.ui.components.LoadingStateContainer
import com.ash.core.ui.components.OfflineBanner
import com.ash.core.ui.components.PullToRefreshContainer
import com.ash.core.ui.theme.AppDimens
import com.ash.core.util.Result

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.onVisible() }

    val result: Result<DashboardUiState> =
        when {
            state.needsFullScreenLoading() -> Result.Loading
            state.needsFullScreenError() -> {
                val message = state.attendanceError ?: state.timetableError ?: ""
                Result.Error(Exception(message), message)
            }
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
                data.attendanceError?.let { message ->
                    item(contentType = "attendance_error") { Text("Attendance: $message") }
                }
                data.timetableError?.let { message ->
                    item(contentType = "timetable_error") { Text("Schedule: $message") }
                }
                data.nextClass?.let { next ->
                    item(contentType = "next_class") { NextClassCard(next) }
                }
                if (data.subjectCount > 0) {
                    item(contentType = "stats_row") { StatsRow(data) }
                } else if (!data.hasAttendance && data.attendanceError == null) {
                    item(contentType = "attendance_loading") { Text("Loading attendance…") }
                }

                item(contentType = "section_label") {
                    AppSectionLabel(
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
                } else if (data.hasTimetable) {
                    item(contentType = "empty_day") { NoClassesToday() }
                } else if (data.timetableError == null) {
                    item(contentType = "schedule_loading") { Text("Loading schedule…") }
                }

                item(contentType = "footer") { BottomSpacer() }
            }
        }
    }
}
