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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
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

@Suppress("LongMethod")
@Composable
fun NotificationsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<AppNotification?>(null) }
    selected?.let { notification ->
        NotificationDetails(
            notification = notification,
            readError = state.readError,
            onMarkAsRead = { viewModel.markAsRead(notification) },
            onDismiss = { selected = null },
        )
    }

    val result: Result<NotificationsUiState> =
        when {
            state.isLoading -> Result.Loading
            state.error != null && state.updatedAt == null -> Result.Error(Exception(state.error), state.error)
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = AppDimens.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.listItemSpacing),
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    item {
                        FeedStatus(data.updatedAt, data.fromCache, data.error)
                        Text("Read status is saved in Axis on this device.", style = MaterialTheme.typography.bodySmall)
                        if (data.error != null) TextButton(onClick = viewModel::refresh) { Text("Retry") }
                    }
                    if (data.notifications.isEmpty()) {
                        item {
                            EmptyState(
                                title = if (data.fromCache) "No saved notifications" else "No notifications",
                                subtitle = if (data.error != null) "Refresh to check for new notices" else "You're all caught up",
                            )
                        }
                    }
                    itemsIndexed(
                        data.notifications,
                        key = { index, n -> "${index}_${n.id}" },
                    ) { _, notification ->
                        NotificationCard(notification) {
                            selected = notification
                            viewModel.markAsRead(notification)
                        }
                    }
                    item { BottomSpacer() }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: AppNotification,
    onClick: () -> Unit,
) {
    val title =
        remember(notification.title) {
            HtmlCompat.fromHtml(notification.title, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        }
    val message =
        remember(notification.message) {
            HtmlCompat.fromHtml(notification.message.replace("\n", "<br>"), HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = cardColor(),
    ) {
        Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
            if (!notification.read) {
                Text("Unread", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
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
                title.ifBlank { "Notification" },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (notification.message.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    message,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (notification.date.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    formatNotificationDate(notification.date),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}
