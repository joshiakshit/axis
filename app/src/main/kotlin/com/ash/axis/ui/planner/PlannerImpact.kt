package com.ash.axis.ui.planner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ash.axis.domain.usecase.ProjectedSubject
import com.ash.core.ui.components.AppCard
import java.util.Locale

@Composable
internal fun ImpactCard(
    row: ProjectedSubject,
    threshold: Int,
) {
    val belowTarget = row.projectedPercent < threshold
    AppCard {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${row.name} · ${row.lecType}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    String.format(Locale.US, "%.1f%% → %.1f%%", row.currentPercent, row.projectedPercent),
                    color = if (belowTarget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                "${row.currentPresent}/${row.currentTotal} → ${row.projectedPresent}/${row.projectedTotal} classes",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                when {
                    row.weeklyClasses == 0 -> "No matching classes in the weekly timetable"
                    belowTarget -> "Below target · ${row.absencesPlanned} classes missed in this forecast"
                    else -> "${row.absencesPlanned} classes missed in this forecast"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
