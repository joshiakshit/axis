package com.ash.axis.ui.grades

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ash.axis.ui.AppFooter
import com.ash.core.ui.theme.AppDimens

@Composable
internal fun PerformanceContent(
    data: GradesUiState,
    viewModel: GradesViewModel,
    listState: LazyListState,
) {
    if (data.showMarksInsights) {
        BackHandler(onBack = viewModel::closeMarksInsights)
        MarksInsightsScreen(
            courses = data.courses,
            onBack = viewModel::closeMarksInsights,
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = AppDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimens.listItemSpacing),
    ) {
        item { Spacer(Modifier.height(14.dp)) }

        item(key = "perf_freshness") {
            LastUpdatedText(
                lastUpdated = data.performanceLastUpdated,
                isBusy = data.performanceLoading || data.isRefreshing,
            )
        }

        if (data.performanceYears.isNotEmpty()) {
            item(key = "perf_filters") {
                PerformanceFilterPanel(data = data, viewModel = viewModel)
            }
        }

        if (data.performanceLoading) {
            item(key = "perf_loading") {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        } else if (data.courses.isNotEmpty()) {
            item(key = "perf_summary") {
                PerformanceSummaryCard(
                    courses = data.courses,
                    onClick = viewModel::openMarksInsights,
                )
            }

            item(key = "courses_label") {
                SectionLabel("COURSES · ${data.courses.size}")
            }

            items(data.courses, key = { "course_${it.subCode}" }) { course ->
                CourseMarksCard(course = course, modifier = Modifier.animateItem())
            }
        } else if (data.performanceError != null) {
            item(key = "perf_error") {
                StatusMessage(text = data.performanceError, isError = true)
            }
        } else if (data.performanceYears.isNotEmpty()) {
            item(key = "no_courses") {
                StatusMessage(text = "Choose the filters above to load marks")
            }
        }

        item { AppFooter() }
    }
}
