package com.ash.axis.ui.planner

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ash.axis.domain.usecase.AbsenceRange
import com.ash.core.ui.components.AxisCalendar
import com.ash.core.ui.components.AxisDialog
import com.ash.core.ui.components.DateRangeSelection
import com.ash.core.ui.components.toggle
import java.time.LocalDate

@Composable
internal fun AbsenceRow(
    range: AbsenceRange,
    onRemove: () -> Unit,
) {
    val label = markerDateSummary(range.start, range.end)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = "Remove absence $label") }
    }
}

@Composable
internal fun AbsenceDialog(
    end: LocalDate?,
    onAdd: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var selection by remember { mutableStateOf(DateRangeSelection()) }
    val start = selection.start
    AxisDialog(onDismiss = onDismiss, footer = {
        TextButton(onClick = onDismiss) { Text("Cancel") }
        TextButton(enabled = start != null, onClick = { start?.let { onAdd(it, selection.end ?: it) } }) { Text("Add") }
    }) {
        Text("Planned absence")
        Text("Choose one date, or two dates for a range.")
        AxisCalendar(
            selection = selection,
            onDateClick = { selection = selection.toggle(it) },
            minDate = LocalDate.now().plusDays(1),
            maxDate = end,
        )
        Text(markerDateSummary(start, selection.end))
    }
}
