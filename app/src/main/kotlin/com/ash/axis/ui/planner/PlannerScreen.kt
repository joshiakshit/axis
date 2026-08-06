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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ash.axis.ui.AppFooter
import com.ash.core.ui.components.LoadingStateContainer
import com.ash.core.ui.components.OfflineBanner
import com.ash.core.ui.components.PullToRefreshContainer
import com.ash.core.ui.theme.AppDimens
import com.ash.core.util.Result
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
                    SimulatorGrid(
                        month = data.simulatorMonth,
                        selectedDates = data.selectedDates,
                        holidays = data.holidays,
                        anchorDate = data.anchorDate,
                        timetable = data.timetable,
                        onPreview = viewModel::previewDate,
                        onMarkAbsent = viewModel::markAbsent,
                        onShiftMonth = viewModel::shiftSimulatorMonth,
                    )
                }

                item(key = "marking_mode", contentType = "marking_mode") {
                    MarkingModeRow(
                        holidayMode = data.holidayMode,
                        onToggle = viewModel::toggleHolidayMode,
                    )
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
