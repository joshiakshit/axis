package com.ash.axis.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.core.ui.components.SubtleDivider

@Suppress("LongMethod", "LongParameterList")
@Composable
internal fun AttendanceSettings(
    threshold: Int,
    onThresholdChange: (Int) -> Unit,
    semesterEndDate: String,
    onSemesterEndDateChange: (String) -> Unit,
    combinedAttendance: Boolean,
    onCombinedAttendanceChange: (Boolean) -> Unit,
) {
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Attendance Goal", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Minimum attendance target",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ThresholdButton("-") { if (threshold > 50) onThresholdChange(threshold - 5) }
                Text(
                    "$threshold%",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 7.dp),
                )
                ThresholdButton("+") { if (threshold < 95) onThresholdChange(threshold + 5) }
            }
        }

        SubtleDivider(modifier = Modifier.padding(vertical = 12.dp))

        SemesterEndDateRow(semesterEndDate, onSemesterEndDateChange)

        SubtleDivider(modifier = Modifier.padding(vertical = 12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Combined PP + PR", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Merge theory & practical for attendance",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = combinedAttendance,
                onCheckedChange = onCombinedAttendanceChange,
                modifier = Modifier.scale(0.8f),
            )
        }
    }
}

@Composable
private fun ThresholdButton(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(30.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SemesterEndDateRow(
    dateStr: String,
    onChange: (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val displayText =
        if (dateStr.isNotBlank()) {
            runCatching {
                java.time.LocalDate.parse(dateStr)
                    .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
            }.getOrDefault(dateStr)
        } else {
            "Not set (default 120 days)"
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Semester End Date", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                "Forecast & planner calculation horizon",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { showPicker = true }) {
            Text(displayText, fontSize = 12.sp)
        }
    }

    if (showPicker) {
        SemesterEndDatePicker(
            currentDate = dateStr,
            onConfirm = { onChange(it) },
            onDismiss = { showPicker = false },
            onClear = {
                onChange("")
                showPicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SemesterEndDatePicker(
    currentDate: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    val initialMillis =
        if (currentDate.isNotBlank()) {
            runCatching {
                java.time.LocalDate.parse(currentDate)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
        } else {
            null
        }
    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val date =
                            java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneOffset.UTC)
                                .toLocalDate()
                        onConfirm(date.toString())
                    }
                    onDismiss()
                },
            ) { Text("Set") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}
