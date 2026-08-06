package com.ash.axis.ui.daywise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.StatusColors
import com.ash.core.ui.theme.cardColor
import com.ash.core.ui.theme.highlightColor
import java.time.LocalDate

@Composable
internal fun MonthCalendar(
    data: DaywiseUiState,
    onSelectDate: (LocalDate) -> Unit,
) {
    val daysByDate = remember(data.days) { data.days.associateBy { it.date } }
    val weeks =
        remember(data.monthStart, data.monthEnd) {
            val firstOffset = data.monthStart.dayOfWeek.value - 1
            buildList {
                repeat(firstOffset) { add(null) }
                for (day in 1..data.monthEnd.dayOfMonth) {
                    add(data.monthStart.withDayOfMonth(day))
                }
                while (size % 7 != 0) add(null)
            }.chunked(7)
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = cardColor(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            weeks.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { date ->
                        if (date == null) {
                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1.15f))
                        } else {
                            DayCell(
                                date = date,
                                day = daysByDate[date],
                                selected = date == data.selectedDate,
                                onSelect = { onSelectDate(date) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Legend()
        }
    }
}

@Composable
@Suppress("CyclomaticComplexMethod")
private fun DayCell(
    date: LocalDate,
    day: DaywiseDay?,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val present = day?.slots?.count { it.isPresent == true } ?: 0
    val absent = day?.slots?.count { it.isPresent == false } ?: 0
    val total = day?.slots?.size ?: 0
    val partial = present > 0 && absent > 0
    val hasData = total > 0

    val presentColor = StatusColors.present
    val absentColor = StatusColors.absent
    val partialColor = StatusColors.partial
    val borderColor =
        when {
            selected -> MaterialTheme.colorScheme.primary
            partial -> partialColor.copy(alpha = 0.55f)
            hasData && absent == 0 -> presentColor.copy(alpha = 0.45f)
            hasData -> absentColor.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        }
    val bgColor =
        when {
            selected -> highlightColor(alpha = 0.35f)
            partial -> partialColor.copy(alpha = 0.10f)
            hasData && absent == 0 -> presentColor.copy(alpha = 0.11f)
            hasData -> absentColor.copy(alpha = 0.08f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
        }

    Column(
        modifier =
            modifier
                .aspectRatio(1.15f)
                .clip(AppShapes.small)
                .background(bgColor)
                .border(1.5.dp, borderColor, AppShapes.small)
                .clickable(onClick = onSelect)
                .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (hasData) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                day?.slots?.take(5)?.forEach { slot ->
                    val dotColor =
                        if (slot.isPresent == true) {
                            presentColor
                        } else {
                            absentColor
                        }
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(dotColor))
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(StatusColors.present, "Present")
        Spacer(Modifier.size(18.dp))
        LegendDot(StatusColors.absent, "Absent")
        Spacer(Modifier.size(18.dp))
        LegendDot(StatusColors.partial, "Partial")
    }
}

@Composable
private fun LegendDot(
    color: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
