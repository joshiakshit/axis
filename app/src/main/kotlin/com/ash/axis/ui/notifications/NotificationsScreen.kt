package com.ash.axis.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.axis.domain.model.AppNotification
import com.ash.axis.ui.BottomSpacer
import com.ash.core.ui.components.EmptyState
import com.ash.core.ui.components.LoadingStateContainer
import com.ash.core.ui.components.PullToRefreshContainer
import com.ash.core.ui.theme.AppDimens
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.cardColor
import com.ash.core.util.Result

@Composable
fun NotificationsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val result: Result<NotificationsUiState> =
        when {
            state.isLoading -> Result.Loading
            state.error != null && state.notifications.isEmpty() -> Result.Error(Exception(state.error), state.error)
            else -> Result.Success(state)
        }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                "Notifications",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        LoadingStateContainer(result = result, onRetry = viewModel::refresh) { data ->
            PullToRefreshContainer(isRefreshing = data.isRefreshing, onRefresh = viewModel::refresh) {
                if (data.notifications.isEmpty()) {
                    EmptyState(title = "No notifications", subtitle = "You're all caught up")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = AppDimens.screenPadding),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.listItemSpacing),
                    ) {
                        item { Spacer(Modifier.height(4.dp)) }
                        itemsIndexed(
                            data.notifications,
                            key = { index, n -> "${index}_${n.id}" },
                        ) { _, notification ->
                            NotificationCard(notification)
                        }
                        item { BottomSpacer() }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(notification: AppNotification) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = cardColor(),
    ) {
        Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
            if (notification.type.isNotBlank()) {
                Text(
                    notification.type.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                notification.title.ifBlank { "Notification" },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (notification.message.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    notification.message,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (notification.date.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    notification.date,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}
