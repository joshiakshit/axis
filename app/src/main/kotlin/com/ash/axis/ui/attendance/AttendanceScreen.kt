package com.ash.axis.ui.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun AttendanceScreen(
    modifier: Modifier = Modifier,
    viewModel: AttendanceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val result: Result<AttendanceUiState> =
        when {
            state.isLoading -> Result.Loading
            state.error != null && !state.hasData -> Result.Error(Exception(state.error), state.error)
            else -> Result.Success(state)
        }

    LoadingStateContainer(result = result, modifier = modifier, onRetry = viewModel::refresh) { data ->
        PullToRefreshContainer(isRefreshing = data.isRefreshing, onRefresh = viewModel::refresh) {
            AttendanceContent(data, viewModel::setThreshold, viewModel::setCombinedAttendance)
        }
    }
}

@Composable
private fun AttendanceContent(
    data: AttendanceUiState,
    onThresholdChange: (Int) -> Unit,
    onCombinedAttendanceChange: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = AppDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.listItemSpacing),
    ) {
        if (data.isOffline) {
            item { OfflineBanner(visible = true) }
        }
        data.error?.let { message ->
            item(contentType = "refresh_error") { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        item { Spacer(Modifier.height(14.dp)) }
        item { OverallSummaryCard(data, onThresholdChange, onCombinedAttendanceChange) }

        subjectGroups(data.subjects).forEach { group ->
            item(contentType = "section_label") { AppSectionLabel(group.title.uppercase()) }
            items(
                group.subjects,
                key = { "${it.subject.subCode}_${it.subject.lecType}" },
                contentType = { "subject_row" },
            ) { decorated ->
                SubjectRow(
                    decorated,
                    data.threshold,
                    modifier = Modifier.animateItem(),
                )
            }
        }

        item { BottomSpacer() }
    }
}
