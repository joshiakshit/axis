package com.ash.axis.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.domain.usecase.AttendanceTone
import com.ash.axis.domain.usecase.ProjectedSubject
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.cardColor
import java.util.Locale

@Suppress("LongMethod")
@Composable
internal fun ImpactCard(
    row: ProjectedSubject,
    threshold: Int,
    modifier: Modifier = Modifier,
) {
    val projColor =
        when (row.projectedTone) {
            AttendanceTone.OK -> MaterialTheme.colorScheme.primary
            AttendanceTone.WARN -> MaterialTheme.colorScheme.tertiary
            AttendanceTone.BAD -> MaterialTheme.colorScheme.error
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = cardColor(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        row.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Surface(
                        shape = AppShapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Text(
                            when {
                                row.lecType.equals("PP+PR", ignoreCase = true) -> "PP+PR"
                                row.lecType.equals("PR", ignoreCase = true) -> "PR"
                                else -> "PP"
                            },
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val ratioText =
                    remember(row.currentPresent, row.currentTotal, row.projectedPresent, row.projectedTotal) {
                        "${row.currentPresent}/${row.currentTotal} → ${row.projectedPresent}/${row.projectedTotal}"
                    }
                Text(
                    ratioText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (row.maxReachable != null) {
                    val canRecover = row.maxReachable >= threshold
                    val maxText =
                        remember(row.maxReachable, row.projectedPresent, row.projectedTotal) {
                            "Max: ${String.format(Locale.US, "%.1f", row.maxReachable)}% (${row.projectedPresent}/${row.projectedTotal})"
                        }
                    Text(
                        maxText,
                        fontSize = 11.sp,
                        color =
                            if (canRecover) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                val projText =
                    remember(row.projectedPercent) {
                        String.format(Locale.US, "%.1f%%", row.projectedPercent)
                    }
                Text(
                    projText,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    color = projColor,
                    fontWeight = FontWeight.Bold,
                )
                if (row.projectedTone != row.baselineTone) {
                    Surface(
                        shape = AppShapes.small,
                        color =
                            if (row.projectedTone == AttendanceTone.BAD) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                    ) {
                        Text(
                            if (row.projectedTone == AttendanceTone.BAD) "DANGER" else "WARN",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 9.sp,
                            color =
                                if (row.projectedTone == AttendanceTone.BAD) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
