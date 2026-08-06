package com.ash.axis.ui.grades

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.domain.model.PerformanceOption
import com.ash.core.ui.theme.AppShapes

@Suppress("LongMethod")
@Composable
internal fun PerformanceFilterPanel(
    data: GradesUiState,
    viewModel: GradesViewModel,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "PERFORMANCE FILTERS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PerformanceDropdown(
                label = "Academic Year",
                options =
                    data.performanceYears.map {
                        PerformanceOption(id = it.academicYear, label = it.academicYear)
                    },
                selectedId = data.selectedPerformanceYear,
                onSelected = viewModel::selectPerformanceYear,
            )
            PerformanceDropdown(
                label = "Exam Session",
                options = data.performanceSessions,
                selectedId = data.selectedPerformanceSession,
                enabled = data.selectedPerformanceYear != null,
                onSelected = viewModel::selectPerformanceSession,
            )
            PerformanceDropdown(
                label = "Class",
                options = data.performanceClasses,
                selectedId = data.selectedPerformanceClass,
                enabled = data.selectedPerformanceSession != null,
                onSelected = viewModel::selectPerformanceClass,
            )
            PerformanceDropdown(
                label = "Division",
                options = data.performanceDivisions,
                selectedId = data.selectedPerformanceDivision,
                enabled = data.selectedPerformanceClass != null,
                onSelected = viewModel::selectPerformanceDivision,
            )
            PerformanceExamChips(
                label = "Exam Name",
                options = data.performanceExams,
                selectedIds = data.selectedPerformanceExams,
                enabled = data.selectedPerformanceDivision != null,
                onToggle = viewModel::togglePerformanceExam,
            )
            FilledTonalButton(
                onClick = viewModel::applyPerformanceExams,
                enabled = data.selectedPerformanceExams.isNotEmpty() && !data.performanceLoading,
                shape = RoundedCornerShape(8.dp),
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (data.selectedPerformanceExams.isEmpty()) {
                        "Select exams"
                    } else {
                        "Load ${data.selectedPerformanceExams.size} exam${if (data.selectedPerformanceExams.size == 1) "" else "s"}"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
internal fun PerformanceDropdown(
    label: String,
    options: List<PerformanceOption>,
    selectedId: String?,
    enabled: Boolean = true,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }
    CompactSelector(
        label = label,
        value = selected?.label.orEmpty(),
        placeholder = if (enabled) "Select" else "Waiting",
        enabled = enabled && options.isNotEmpty(),
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(option.id)
                    },
                )
            }
        }
    }
}

@Composable
internal fun PerformanceExamChips(
    label: String,
    options: List<PerformanceOption>,
    selectedIds: List<String>,
    enabled: Boolean = true,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (enabled && options.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(options, key = { "perf_exam_${it.id}" }) { option ->
                    ExamChoiceChip(
                        label = option.label,
                        selected = option.id in selectedIds,
                        onClick = { onToggle(option.id) },
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    if (enabled) "No exams found" else "Choose division first",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun ExamChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape,
        color =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        border =
            BorderStroke(
                1.dp,
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                },
            ),
        modifier =
            Modifier
                .height(36.dp)
                .clip(shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
