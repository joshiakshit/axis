package com.ash.axis.ui.planner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.axis.ui.AppFooter
import com.ash.core.ui.components.LoadingStateContainer
import com.ash.core.ui.components.OfflineBanner
import com.ash.core.ui.components.PullToRefreshContainer
import com.ash.core.ui.theme.AppDimens
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.cardColor
import com.ash.core.util.Result
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val enterTransition = fadeIn() + expandVertically()
private val exitTransition = fadeOut() + shrinkVertically()

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun PlannerScreen(
    modifier: Modifier = Modifier,
    viewModel: PlannerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val result: Result<PlannerUiState> =
        when {
            state.isLoading -> Result.Loading
            state.error != null && state.subjects.isEmpty() -> Result.Error(Exception(state.error), state.error)
            else -> Result.Success(state)
        }

    LoadingStateContainer(result = result, modifier = modifier, onRetry = viewModel::refresh) { data ->
        PullToRefreshContainer(isRefreshing = data.isRefreshing, onRefresh = viewModel::refresh) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = AppDimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(AppDimens.itemSpacing),
            ) {
                if (data.isOffline) {
                    item(key = "offline_banner", contentType = "offline") {
                        OfflineBanner(visible = true)
                    }
                }

                item(key = "header", contentType = "header") {
                    Spacer(Modifier.height(14.dp))
                    Column {
                        Text(
                            "Planner",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        )
                        Text(
                            "${data.overallPresent}/${data.overallTotal} attended · ${data.totalSpare} skips available",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item(key = "tomorrow", contentType = "tomorrow") {
                    AnimatedVisibility(
                        visible = data.tomorrowSlots.isNotEmpty(),
                        enter = enterTransition,
                        exit = exitTransition,
                    ) {
                        TomorrowSection(data.tomorrowSlots)
                    }
                }

                item(key = "simulator_grid", contentType = "simulator") {
                    if (data.semesterEndSet) {
                        SimulatorGrid(
                            month = data.simulatorMonth,
                            selectedDates = data.selectedDates,
                            holidays = data.holidays,
                            anchorDate = data.anchorDate,
                            dateTimetable = data.dateTimetable,
                            onPreview = viewModel::previewDate,
                            onMarkAbsent = viewModel::markAbsent,
                            onShiftMonth = viewModel::shiftSimulatorMonth,
                        )
                    } else {
                        SemesterEndGate(onSetDate = viewModel::setSemesterEndDate)
                    }
                }

                item(key = "marking_mode", contentType = "marking_mode") {
                    if (data.semesterEndSet) {
                        MarkingModeRow(
                            holidayMode = data.holidayMode,
                            onToggle = viewModel::toggleHolidayMode,
                        )
                    }
                }

                item(key = "selection_summary", contentType = "summary") {
                    val hasSelection =
                        data.anchorDate != null || data.selectedDates.isNotEmpty() || data.holidays.isNotEmpty()
                    AnimatedVisibility(
                        visible = hasSelection,
                        enter = enterTransition,
                        exit = exitTransition,
                    ) {
                        val summaryLabel =
                            buildString {
                                if (data.selectedDates.isNotEmpty()) {
                                    append("${data.selectedDates.size} absent")
                                }
                                if (data.holidays.isNotEmpty()) {
                                    if (isNotEmpty()) append(" · ")
                                    append("${data.holidays.size} holiday${if (data.holidays.size != 1) "s" else ""}")
                                }
                                if (isEmpty() && data.anchorDate != null) {
                                    append(
                                        "Preview by ${data.anchorDate.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))}",
                                    )
                                }
                            }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                summaryLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            TextButton(onClick = viewModel::clearDates) { Text("Clear") }
                        }
                    }
                }

                item(key = "projected_header", contentType = "projected_header") {
                    val showProjected = data.anchorDate != null || data.selectedDates.isNotEmpty()
                    AnimatedVisibility(
                        visible = showProjected,
                        enter = enterTransition,
                        exit = exitTransition,
                    ) {
                        val headerLabel =
                            when {
                                data.projected.isEmpty() -> "No tracked classes in this range"
                                data.selectedDates.isEmpty() ->
                                    "Projected attendance by " +
                                        data.anchorDate?.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
                                else -> "Projected impact"
                            }
                        Text(
                            headerLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(
                    data.projected,
                    key = { "${it.code}_${it.lecType}" },
                    contentType = { "impact_card" },
                ) { row ->
                    ImpactCard(row, data.threshold, modifier = Modifier.animateItem())
                }

                item(key = "subject_budget", contentType = "subject_budget") {
                    SubjectBudgetSection(data, data.forecast)
                }

                item(key = "bottom_spacer", contentType = "footer") { AppFooter() }
            }
        }
    }
}

@Composable
private fun MarkingModeRow(
    holidayMode: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Marking:",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilterChip(
            selected = !holidayMode,
            onClick = { if (holidayMode) onToggle() },
            label = { Text("Absent", fontSize = 12.sp) },
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
        )
        FilterChip(
            selected = holidayMode,
            onClick = { if (!holidayMode) onToggle() },
            label = { Text("Holiday", fontSize = 12.sp) },
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
        )
    }
}

@Composable
private fun SemesterEndGate(onSetDate: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = cardColor(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.EditCalendar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
            Text("Set your semester end date", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "The planner projects your attendance up to the end of the semester. " +
                    "Set the end date to start planning skips.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = { showPicker = true }) {
                Text("Set semester end date")
            }
        }
    }
    if (showPicker) {
        SemesterEndDatePicker(
            onConfirm = {
                onSetDate(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SemesterEndDatePicker(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onConfirm(date.toString())
                    } else {
                        onDismiss()
                    }
                },
            ) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}
