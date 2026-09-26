package com.ash.axis.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ash.axis.domain.usecase.TodayAttendance
import com.ash.core.ui.components.AppCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayAttendanceCard(
    classCount: Int,
    attendance: TodayAttendance?,
    onSelect: (TodayAttendance) -> Unit,
) {
    AppCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Today · $classCount scheduled classes", style = MaterialTheme.typography.titleSmall)
            Text(
                "If today's attendance is already in your totals, choose Already counted. Otherwise, apply an outcome for today.",
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TodayAttendance.entries.forEach { choice ->
                    FilterChip(selected = attendance == choice, onClick = { onSelect(choice) }, label = {
                        Text(
                            when (choice) {
                                TodayAttendance.ATTENDED -> "Attend all"
                                TodayAttendance.MISSED -> "Miss all"
                                TodayAttendance.ALREADY_INCLUDED -> "Already counted"
                            },
                        )
                    })
                }
            }
        }
    }
}
