package com.ash.axis.ui.notifications

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun FeedStatus(
    updatedAt: Long?,
    fromCache: Boolean,
    error: String?,
) {
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    if (updatedAt != null) {
        val date =
            Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.getDefault()))
        Text(
            "${if (fromCache) "Saved data" else "Updated"} · $date",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
