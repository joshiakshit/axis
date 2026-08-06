package com.ash.axis.ui.timetable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.core.ui.components.EmptyState
import com.ash.core.ui.components.LoadingStateContainer
import com.ash.core.ui.components.PullToRefreshContainer
import com.ash.core.util.Result

@Composable
fun TimetableScreen(
    modifier: Modifier = Modifier,
    viewModel: TimetableViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val result: Result<TimetableUiState> =
        when {
            state.isLoading -> Result.Loading
            state.error != null && state.days.isEmpty() -> Result.Error(Exception(state.error), state.error)
            else -> Result.Success(state)
        }

    LoadingStateContainer(result = result, modifier = modifier, onRetry = viewModel::refresh) { data ->
        PullToRefreshContainer(isRefreshing = data.isRefreshing, onRefresh = viewModel::refresh) {
            if (data.days.isEmpty()) {
                EmptyState(title = "No timetable data for this week")
            } else {
                TimetableContent(data, viewModel)
            }
        }
    }
}
