package com.ash.axis.ui.daywise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.domain.model.DaywiseSlot
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.StatusColors
import com.ash.core.ui.theme.cardColor
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun SelectedDayCard(data: DaywiseUiState) {
    val day = data.days.firstOrNull { it.date == data.selectedDate }
    val weekday = data.selectedDate.dayOfWeek.name.lowercase().replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    val dateLabel = data.selectedDate.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = cardColor(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                weekday.uppercase(Locale.ENGLISH),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(dateLabel, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))

            if (day?.slots.isNullOrEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No class scheduled", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Weekend, holiday, or break day",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                day?.slots?.forEachIndexed { index, slot ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                    }
                    DaywiseSlotRow(slot)
                }
            }
        }
    }
}

@Composable
private fun DaywiseSlotRow(slot: DaywiseSlot) {
    val present = slot.isPresent == true
    val statusColor = if (present) StatusColors.present else StatusColors.absent
    val code = slot.subShortname ?: slot.subCode ?: slot.subjectId ?: ""

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                code,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                slot.title.ifBlank { code },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                listOf(slot.fromTime, slot.toTime).filter { it.isNotBlank() }.joinToString(" - "),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            slot.status ?: if (present) "Present" else "Absent",
            fontSize = 12.sp,
            color = statusColor,
            fontWeight = FontWeight.Bold,
        )
    }
}
