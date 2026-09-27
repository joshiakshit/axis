package com.ash.axis.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.domain.usecase.TodayAttendance
import com.ash.core.ui.components.AppCard

@Composable
internal fun TodayAttendanceCard(
    classCount: Int,
    attendance: TodayAttendance?,
    onSelect: (TodayAttendance) -> Unit,
) {
    AppCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today · $classCount scheduled classes", style = MaterialTheme.typography.titleSmall)
                PlannerInfoButton(
                    "If today's classes are already in your totals, choose Counted. Otherwise, choose Attend all or Miss all.",
                    "About today's attendance",
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TodayAttendance.entries.forEach { choice ->
                    FilterChip(selected = attendance == choice, onClick = { onSelect(choice) }, modifier = Modifier.weight(1f), label = {
                        Text(
                            when (choice) {
                                TodayAttendance.ATTENDED -> "Attend all"
                                TodayAttendance.MISSED -> "Miss all"
                                TodayAttendance.ALREADY_INCLUDED -> "Counted"
                            },
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    })
                }
            }
        }
    }
}
