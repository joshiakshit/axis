package com.ash.axis.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.domain.usecase.TodayAttendance
import com.ash.core.ui.components.AppCard
import com.ash.core.ui.components.AppSectionLabel

@Composable
internal fun TodayAttendanceCard(
    classCount: Int,
    attendance: TodayAttendance?,
    onSelect: (TodayAttendance) -> Unit,
) {
    AppCard {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppSectionLabel("TODAY'S ATTENDANCE")
            Text(
                "Did you attend all $classCount ${if (classCount == 1) "class" else "classes"} today?",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = attendance == TodayAttendance.ATTENDED,
                    onClick = { onSelect(TodayAttendance.ATTENDED) },
                    label = { Text("Attended all") },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                )
                FilterChip(
                    selected = attendance == TodayAttendance.MISSED,
                    onClick = { onSelect(TodayAttendance.MISSED) },
                    label = { Text("Missed all") },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                )
            }
            Text(
                if (attendance == null) {
                    "Choose one to calculate your best possible attendance."
                } else {
                    "This answer is included once before future classes are projected."
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
