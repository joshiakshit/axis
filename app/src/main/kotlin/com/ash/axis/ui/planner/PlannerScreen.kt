package com.ash.axis.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.axis.ui.BottomSpacer
import com.ash.core.ui.components.AppCard
import com.ash.core.ui.components.AxisDatePickerDialog
import com.ash.core.ui.components.OfflineBanner
import com.ash.core.ui.components.PullToRefreshContainer
import com.ash.core.ui.theme.AppDimens
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val forecastDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun PlannerScreen(
    modifier: Modifier = Modifier,
    viewModel: PlannerViewModel = hiltViewModel(),
) {
    val data by viewModel.state.collectAsStateWithLifecycle()
    var showEndPicker by remember { mutableStateOf(false) }
    var showAbsencePicker by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    PullToRefreshContainer(isRefreshing = data.isRefreshing, onRefresh = viewModel::refresh, modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = AppDimens.screenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Planner", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showCalendar = true }) { Text("Academic calendar") }
                }
            }
            if (data.isOffline) item { OfflineBanner(visible = true) }
            if (data.isLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            data.error?.let { error ->
                item {
                    Column {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::refresh) { Text("Retry") }
                    }
                }
            }
            item {
                AppCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Forecast until", style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { showEndPicker = true }) {
                            Text(data.forecastEnd?.format(forecastDateFormat) ?: "Choose a date")
                        }
                        Text(
                            "Estimate based on your weekly timetable. Assumes you attend except on " +
                                "planned absences and saved no-class dates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (data.todayClassCount > 0) {
                item { TodayAttendanceCard(data.todayClassCount, data.todayAttendance, viewModel::setTodayAttendance) }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Planned absences", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (data.absences.isNotEmpty()) TextButton(onClick = viewModel::clearAbsences) { Text("Clear") }
                    TextButton(onClick = { showAbsencePicker = true }, enabled = data.forecastEnd?.isAfter(LocalDate.now()) == true) {
                        Text("Add")
                    }
                }
                if (data.absences.isEmpty()) Text("No planned absences.", style = MaterialTheme.typography.bodySmall)
            }
            items(data.absences, key = { "${it.start}_${it.end}" }) { range ->
                AbsenceRow(range, onRemove = { viewModel.removeAbsence(range) })
            }
            if (data.projected.isNotEmpty()) {
                item { Text("Estimated attendance · target ${data.threshold}%", style = MaterialTheme.typography.titleSmall) }
                items(data.projected, key = { "${it.code}_${it.lecType}" }) { row ->
                    ImpactCard(row, data.threshold)
                }
            } else if (!data.isLoading && data.error == null) {
                item {
                    Text(
                        when {
                            data.forecastEnd == null -> "Choose a forecast end date to start."
                            data.todayAttendance == null -> "Confirm today's attendance to see the forecast."
                            else -> "No attendance subjects available."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                Text(
                    "Attendance updated: ${sourceTime(
                        data.attendanceUpdatedAt,
                    )}\nTimetable updated: ${sourceTime(data.timetableUpdatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Only dates through the forecast end count. Schedule changes can affect the result.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { BottomSpacer() }
        }
    }
    if (showEndPicker) {
        AxisDatePickerDialog(
            title = "Forecast until",
            initialDate = data.forecastEnd,
            minDate = LocalDate.now(),
            onConfirm = {
                viewModel.setForecastEnd(it)
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false },
        )
    }
    if (showAbsencePicker) {
        AbsenceDialog(
            end = data.forecastEnd,
            onAdd = { start, end ->
                viewModel.addAbsence(start, end)
                showAbsencePicker = false
            },
            onDismiss = { showAbsencePicker = false },
        )
    }
    if (showCalendar) {
        ModalBottomSheet(onDismissRequest = { showCalendar = false }) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                PlannerMarkersSection(data.markers, viewModel::addMarker, viewModel::deleteMarker)
            }
        }
    }
}

private fun sourceTime(millis: Long?): String =
    millis?.let {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
    } ?: "Not available"
