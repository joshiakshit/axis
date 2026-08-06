package com.ash.axis.ui.daywise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.axis.ui.AppFooter
import com.ash.core.ui.components.LoadingStateContainer
import com.ash.core.ui.components.OfflineBanner
import com.ash.core.ui.theme.AppDimens
import com.ash.core.util.Result

@Composable
fun DaywiseScreen(
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    viewModel: DaywiseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(isVisible) {
        if (isVisible) viewModel.onPageVisible()
    }

    val result: Result<DaywiseUiState> =
        when {
            state.isLoading -> Result.Loading
            state.error != null && state.days.isEmpty() -> Result.Error(Exception(state.error), state.error)
            else -> Result.Success(state)
        }

    LoadingStateContainer(result = result, modifier = modifier, onRetry = viewModel::refresh) { data ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = AppDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.sectionGap),
        ) {
            if (data.isOffline) {
                item { OfflineBanner(visible = true) }
            }
            item { Spacer(Modifier.height(14.dp)) }
            item {
                DaywiseHeader(
                    isBusy = data.isRefreshing,
                    lastUpdated = data.lastUpdated,
                    onReload = viewModel::refresh,
                )
            }
            item {
                MonthNavigator(
                    label = data.monthLabel,
                    onPrev = { viewModel.shiftMonth(-1) },
                    onNext = { viewModel.shiftMonth(1) },
                )
            }
            item {
                MonthCalendar(
                    data = data,
                    onSelectDate = viewModel::selectDate,
                )
            }
            item { SelectedDayCard(data) }
            item { AppFooter() }
        }
    }
}
