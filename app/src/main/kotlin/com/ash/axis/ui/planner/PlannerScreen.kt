package com.ash.axis.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.ash.axis.ui.BottomSpacer
import com.ash.axis.ui.CalendarSection
import com.ash.core.ui.components.AppCard
import com.ash.core.ui.components.AppSectionLabel
import com.ash.core.ui.components.AxisDatePickerDialog
import com.ash.core.ui.components.LoadingStateContainer
import com.ash.core.ui.components.OfflineBanner
import com.ash.core.ui.components.PullToRefreshContainer
import com.ash.core.ui.theme.AppDimens
import com.ash.core.util.Result
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val impactDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

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
            state.error != null && state.subjects.isEmpty() && state.timetable.isEmpty() ->
                Result.Error(Exception(state.error), state.error)
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
                if (data.error != null) {
                    item(key = "refresh_error", contentType = "status") {
                        Text(data.error, color = MaterialTheme.colorScheme.error)
                    }
                }

                item(key = "header", contentType = "header") {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Planner",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    )
                }

                item(key = "markers", contentType = "markers") {
                    PlannerMarkersSection(
                        markers = data.markers,
                        onAdd = viewModel::addMarker,
                        onDelete = viewModel::deleteMarker,
                    )
                }

                item(key = "university_calendar", contentType = "calendar") {
                    CalendarSection(data.calendar, onRefresh = { viewModel.loadCalendar(true) })
                }

                item(key = "simulator_grid", contentType = "simulator") {
                    if (data.semesterEndSet) {
                        if (data.todayHasClasses) {
                            TodayAttendanceCard(
                                classCount = data.todayClassCount,
                                attendance = data.todayAttendance,
                                onSelect = viewModel::setTodayAttendance,
                            )
                            Spacer(Modifier.height(AppDimens.itemSpacing))
                        }
                        SimulatorGrid(
                            month = data.simulatorMonth,
                            selectedDates = data.selectedDates,
                            holidays = data.holidays,
                            markers = data.markers,
                            holidayMode = data.holidayMode,
                            anchorDate = data.anchorDate,
                            dateTimetable = data.dateTimetable,
                            coveredDates = data.coveredDates,
                            estimatedDates = data.estimatedDates,
                            semesterEndDate = data.semesterEndDate,
                            interactionEnabled = data.todayAttendance != null && LocalDate.now() in data.coveredDates,
                            waitingForSchedule = LocalDate.now() !in data.coveredDates,
                            onPreview = viewModel::previewDate,
                            onMarkAbsent = viewModel::markAbsent,
                            onShiftMonth = viewModel::shiftSimulatorMonth,
                            onToggleHolidayMode = viewModel::toggleHolidayMode,
                            onClear = viewModel::clearDates,
                        )
                    } else {
                        SemesterEndGate(onSetDate = viewModel::setSemesterEndDate)
                    }
                }

                item(key = "projected_header", contentType = "projected_header") {
                    if (data.projected.isNotEmpty()) {
                        AppSectionLabel(
                            if (data.projectionCoverage == ProjectionCoverage.ESTIMATE) {
                                "WEEKLY SCHEDULE ESTIMATE"
                            } else if (data.anchorDate != null) {
                                "PROJECTED ON ${data.anchorDate.format(impactDateFormatter).uppercase()}"
                            } else {
                                "BEST POSSIBLE BY SEMESTER END"
                            },
                        )
                    }
                }

                if (data.projectionCoverage != null && data.projectionCoverage != ProjectionCoverage.COMPLETE) {
                    item(key = "projection_coverage", contentType = "status") {
                        Text(
                            when (data.projectionCoverage) {
                                ProjectionCoverage.LOADING -> "Loading schedule coverage for this projection."
                                ProjectionCoverage.FAILED -> "Some schedule weeks could not load. Retry to complete the projection."
                                ProjectionCoverage.ESTIMATE -> "This projection uses weekly schedule estimates."
                                ProjectionCoverage.LIMITED -> "This range exceeds planner coverage. Choose a nearer date."
                                else -> ""
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(
                    data.projected,
                    key = { "${it.code}_${it.lecType}" },
                    contentType = { "impact_card" },
                ) { row ->
                    ImpactCard(
                        row = row,
                        threshold = data.threshold,
                        showProjection = data.anchorDate != null,
                        modifier = Modifier.animateItem(),
                    )
                }

                item(key = "bottom_spacer", contentType = "footer") { BottomSpacer() }
            }
        }
    }
}

@Composable
private fun SemesterEndGate(onSetDate: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    AppCard {
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
        AxisDatePickerDialog(
            title = "Semester end date",
            onConfirm = {
                onSetDate(it.toString())
                showPicker = false
            },
            onDismiss = { showPicker = false },
            minDate = LocalDate.now(),
        )
    }
}
