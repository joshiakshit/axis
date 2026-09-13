package com.ash.axis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ash.axis.ui.notifications.FeedStatus
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.cardColor
import java.time.LocalDate

@Composable
internal fun CalendarSection(
    state: CalendarUiState,
    onRefresh: () -> Unit,
    date: LocalDate? = null,
) {
    val entries = state.entries.filter { date == null || it.date == date.toString() }
    Surface(shape = AppShapes.medium, color = cardColor(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp).heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Events & holidays", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onRefresh, enabled = !state.isLoading) { Text("Refresh") }
            }
            when {
                state.isLoading -> Text("Loading calendar…", style = MaterialTheme.typography.bodySmall)
                entries.isEmpty() && state.error == null ->
                    Text(
                        if (state.fromCache) {
                            "No saved entries for this ${if (date == null) "month" else "day"}"
                        } else {
                            "No events or holidays this ${if (date == null) "month" else "day"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
            }
            entries.forEach { entry ->
                Text(
                    "${if (entry.isHoliday) "Holiday" else "Event"} · ${entry.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (date == null) Text(entry.date, style = MaterialTheme.typography.labelSmall)
                if (entry.description.isNotBlank() && entry.description != entry.title) {
                    Text(entry.description, style = MaterialTheme.typography.bodySmall)
                }
            }
            FeedStatus(state.updatedAt, state.fromCache, state.error)
        }
    }
}
