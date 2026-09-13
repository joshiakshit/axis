package com.ash.axis.ui.notifications

import android.text.style.URLSpan
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.text.HtmlCompat
import com.ash.axis.domain.model.AppNotification
import java.net.URI

@Composable
internal fun NotificationDetails(
    notification: AppNotification,
    readError: String?,
    onMarkAsRead: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val message =
        remember(notification.message) {
            HtmlCompat.fromHtml(notification.message.replace("\n", "<br>"), HtmlCompat.FROM_HTML_MODE_LEGACY)
        }
    val links =
        remember(notification) {
            (
                listOf(notification.url) +
                    message.getSpans(0, message.length, URLSpan::class.java).map { it.url } +
                    Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).findAll(message).map { it.value }.toList()
            )
                .filter(::isNotificationLink).distinct()
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(notification.title.ifBlank { "Notification" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(notification.date, style = MaterialTheme.typography.labelSmall)
                SelectionContainer { Text(message.toString()) }
                links.forEach { url ->
                    TextButton(onClick = { uriHandler.openUri(url) }) { Text(url) }
                }
                if (readError != null) {
                    Text(readError, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onMarkAsRead) { Text("Mark as read") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

internal fun isNotificationLink(url: String): Boolean =
    runCatching { URI(url).let { it.scheme?.lowercase() in setOf("https", "http") && !it.host.isNullOrBlank() } }.getOrDefault(false)
