package com.ash.axis.ui.grades

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.domain.model.ReportCardEntry
import com.ash.axis.ui.AppFooter
import com.ash.core.ui.theme.AppDimens
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.cardColor

@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
internal fun ResultContent(
    data: GradesUiState,
    viewModel: GradesViewModel,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = AppDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        item(key = "result_freshness") {
            LastUpdatedText(lastUpdated = data.resultLastUpdated, isBusy = data.isRefreshing)
        }

        if (data.semesterNumbers.isNotEmpty()) {
            item(key = "sem_label") { SectionLabel("SEMESTER") }

            item(key = "semester_pills") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(data.semesterNumbers, key = { "semnum_$it" }) { num ->
                        SelectionPill(
                            label = "Sem $num",
                            selected = num == data.selectedSemesterNum,
                            onClick = { viewModel.selectSemesterNum(num) },
                        )
                    }
                }
            }
        }

        if (data.examSessions.isNotEmpty()) {
            item(key = "session_label") { SectionLabel("EXAM SESSION") }

            item(key = "session_pills") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(data.examSessions, key = { "session_${it.id}" }) { session ->
                        SelectionPill(
                            label = session.label,
                            selected = session.id == data.selectedSessionId,
                            onClick = { viewModel.selectSession(session.id) },
                        )
                    }
                }
            }
        }

        if (data.reportCards.isNotEmpty()) {
            item(key = "rc_label") {
                SectionLabel("GRADE CARDS · ${data.reportCards.size}")
            }

            items(
                data.reportCards,
                key = { "rc_${it.id}_${it.srno}" },
            ) { entry ->
                ReportCardEntryRow(entry = entry)
            }

            if (data.marksheetType.isNotBlank()) {
                item(key = "view_btn") {
                    Spacer(Modifier.height(4.dp))
                    ViewReportCardButton(
                        isLoading = data.isLoadingPdf,
                        onClick = viewModel::viewReportCard,
                    )
                }
            }
        }

        if (!data.isResultPublish) {
            item(key = "not_published") {
                StatusMessage(
                    text = data.noResultMsg.ifBlank { "Results not published yet" },
                )
            }
        }

        val showSelectSemester =
            data.semesterNumbers.isNotEmpty() && data.selectedSemesterNum == null &&
                !data.isLoading && data.isResultPublish
        if (showSelectSemester) {
            item(key = "select_semester") {
                StatusMessage(text = "Select a semester to see exam sessions")
            }
        }

        val showSelectSession =
            data.examSessions.isNotEmpty() && data.selectedSessionId == null &&
                data.reportCards.isEmpty() && !data.isLoading && data.isResultPublish
        if (showSelectSession) {
            item(key = "select_session") {
                StatusMessage(text = "Select an exam session to load grade cards")
            }
        }

        val showNoSessions =
            data.examSessions.isEmpty() && data.selectedSemesterNum != null &&
                !data.isLoading && data.isResultPublish
        if (showNoSessions) {
            item(key = "no_sessions") {
                StatusMessage(
                    text = data.error ?: "No exam sessions found for this semester",
                )
            }
        }

        val showError =
            data.error != null && data.reportCards.isEmpty() &&
                data.examSessions.isNotEmpty() && data.isResultPublish
        if (showError) {
            item(key = "error_msg") {
                StatusMessage(text = data.error!!, isError = true)
            }
        }

        item { AppFooter() }
    }
}

@Suppress("LongMethod")
@Composable
private fun ReportCardEntryRow(entry: ReportCardEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = cardColor(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.cardPadding, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = AppShapes.medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.semDisplayName.ifBlank { "Semester ${entry.semesterNumeric}" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (entry.subExamName.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        entry.subExamName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.classes.isNotBlank()) {
                    Text(
                        entry.classes,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (entry.acadYear.isNotBlank()) {
                Surface(
                    shape = AppShapes.small,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                ) {
                    Text(
                        entry.acadYear,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewReportCardButton(
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
        shape = AppShapes.medium,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(10.dp))
            Text("Loading Report Card...", fontWeight = FontWeight.SemiBold)
        } else {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text("View Report Card", fontWeight = FontWeight.SemiBold)
        }
    }
}
