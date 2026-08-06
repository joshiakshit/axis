package com.ash.axis.ui.planner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.domain.usecase.AttendanceTone
import com.ash.axis.domain.usecase.DaySafety
import com.ash.axis.domain.usecase.ForecastRow
import com.ash.axis.domain.usecase.PlannerSubject
import com.ash.axis.domain.usecase.TomorrowClass
import com.ash.core.ui.theme.AppDimens
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.SubjectColors
import com.ash.core.ui.theme.cardColor
import kotlinx.collections.immutable.ImmutableList
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun TomorrowSection(slots: ImmutableList<TomorrowClass>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = cardColor(),
    ) {
        Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
            Text(
                "TOMORROW",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            slots.forEachIndexed { index, slot ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                TomorrowSlotRow(slot)
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun TomorrowSlotRow(slot: TomorrowClass) {
    val skipColor =
        if (slot.canSkip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                slot.subjectName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    slot.time,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "·",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    slot.subCode,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            val budgetLabel =
                if (slot.canSkip) {
                    "${slot.skipsLeft} skip${if (slot.skipsLeft != 1) "s" else ""} left"
                } else {
                    "Can't skip"
                }
            Text(
                budgetLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = skipColor,
            )
            if (slot.canSkip) {
                val afterText =
                    remember(slot.percentAfterSkip) {
                        "→ ${String.format(Locale.US, "%.1f", slot.percentAfterSkip)}%"
                    }
                Text(
                    afterText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = plannerToneColor(slot.toneAfterSkip),
                )
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
internal fun SubjectBudgetSection(
    data: PlannerUiState,
    forecast: ImmutableList<ForecastRow>,
) {
    var expanded by remember { mutableStateOf(false) }
    val atRiskCount = data.subjects.count { it.tone != AttendanceTone.OK }
    val forecastMap =
        remember(forecast) {
            forecast.associateBy { "${it.subCode.uppercase()}_${it.lecType.uppercase()}" }
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = cardColor(),
    ) {
        Column(modifier = Modifier.animateContentSize().padding(AppDimens.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(AppShapes.medium).clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "SUBJECT BUDGET",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val summary =
                        buildString {
                            append("${data.totalSpare} spare")
                            if (atRiskCount > 0) append(" · $atRiskCount at risk")
                        }
                    Text(
                        summary,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    DaySafetyRow(data.daySafety)
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.medium,
                        color = cardColor(),
                    ) {
                        Column {
                            data.subjects.forEachIndexed { index, subject ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                                    )
                                }
                                val key = "${subject.code.uppercase()}_${subject.lecType.uppercase()}"
                                SubjectBudgetRow(subject, forecastMap[key])
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun SubjectBudgetRow(
    subject: PlannerSubject,
    forecast: ForecastRow?,
) {
    val toneColor = plannerToneColor(subject.tone)
    val budget = if (subject.bunkable > 0) subject.bunkable else subject.need
    val budgetText = if (subject.bunkable > 0) "$budget\nSPARE" else "$budget\nNEED"
    val budgetColor = if (subject.bunkable > 0) MaterialTheme.colorScheme.primary else toneColor
    val typeBadge =
        when {
            subject.lecType.equals("PP+PR", ignoreCase = true) -> "PP+PR"
            subject.lecType.equals("PR", ignoreCase = true) -> "PR"
            else -> "PP"
        }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(SubjectColors.accent(subject.code)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    subject.code,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = AppShapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Text(
                        typeBadge,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                subject.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (forecast != null) {
                val recoveryLabel =
                    remember(forecast.reachDate, forecast.maxReachable) {
                        when {
                            forecast.reachDate != null ->
                                "Reach by ${forecast.reachDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
                            forecast.maxReachable != null ->
                                "Max: ${String.format(Locale.US, "%.1f", forecast.maxReachable)}%"
                            else -> "Recovery unlikely"
                        }
                    }
                val recoveryColor =
                    if (forecast.reachDate != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                Text(
                    recoveryLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = recoveryColor,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .clip(AppShapes.small)
                    .background(budgetColor.copy(alpha = 0.14f))
                    .border(1.dp, budgetColor.copy(alpha = 0.42f), AppShapes.small)
                    .padding(horizontal = 13.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                budgetText,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = budgetColor,
            )
        }
        val pctText =
            remember(subject.percent) {
                "${String.format(Locale.US, "%.2f", subject.percent)}%"
            }
        Text(
            pctText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = toneColor,
        )
    }
}

@Composable
internal fun plannerToneColor(tone: AttendanceTone): Color =
    when (tone) {
        AttendanceTone.OK -> MaterialTheme.colorScheme.primary
        AttendanceTone.WARN -> MaterialTheme.colorScheme.tertiary
        AttendanceTone.BAD -> MaterialTheme.colorScheme.error
    }

@Composable
internal fun DaySafetyRow(days: ImmutableList<DaySafety>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { day ->
            val color =
                when {
                    !day.hasClasses -> MaterialTheme.colorScheme.surfaceVariant
                    day.safe -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer
                }
            val textColor =
                when {
                    !day.hasClasses -> MaterialTheme.colorScheme.onSurfaceVariant
                    day.safe -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onErrorContainer
                }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(AppShapes.medium)
                        .background(color)
                        .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    day.day.take(3),
                    fontSize = 11.sp,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        !day.hasClasses -> "-"
                        day.safe -> "${day.slotCount} ok"
                        else -> "${day.riskySubjects.size} risk"
                    },
                    fontSize = 10.sp,
                    color = textColor,
                )
            }
        }
    }
}
