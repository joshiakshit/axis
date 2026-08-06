package com.ash.axis.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.core.ui.theme.AppDimens
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.SubjectColors
import com.ash.core.ui.theme.cardColor
import com.ash.core.ui.theme.highlightColor
import kotlinx.coroutines.delay

@Suppress("LongMethod")
@Composable
internal fun TimelineCard(slots: List<TodaySlotDisplay>) {
    var nowMinutes by remember { mutableIntStateOf(currentMinutes()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMinutes = currentMinutes()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color = cardColor(),
    ) {
        Column {
            slots.forEachIndexed { index, display ->
                val startMin = parseTimeMinutes(display.slot.fromTime)
                val endMin = parseTimeMinutes(display.slot.toTime)
                val isPast = nowMinutes >= endMin
                val isLive = nowMinutes in startMin until endMin
                val tagColor = SubjectColors.accent(display.subjectCode)

                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (isLive) {
                                    Modifier.background(highlightColor(alpha = 0.2f))
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.width(AppDimens.timeColumnWidth)) {
                        Text(
                            formatTimeShort(display.slot.fromTime),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color =
                                if (isLive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                        Text(
                            formatTimeShort(display.slot.toTime),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier =
                            Modifier
                                .width(AppDimens.subjectStripeWidth)
                                .height(44.dp)
                                .background(
                                    tagColor.copy(alpha = if (isPast) 0.35f else 1f),
                                    shape = AppShapes.small,
                                ),
                    )
                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                display.subjectCode,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                color = tagColor.copy(alpha = if (isPast) 0.45f else 1f),
                            )
                            if (isLive) {
                                Surface(
                                    shape = AppShapes.small,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        "LIVE",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        Text(
                            display.cleanName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color =
                                if (isPast) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                append(display.lectType)
                                if (display.room.isNotBlank()) append(" · Room ${display.room}")
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

internal fun parseTimeMinutes(time: String): Int {
    val parts = time.split(":")
    if (parts.size < 2) return 0
    return (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
}

internal fun formatTimeShort(time: String): String {
    val parts = time.split(":")
    if (parts.size < 2) return time
    val h = parts[0].toIntOrNull() ?: return time
    val m = parts[1].toIntOrNull() ?: 0
    return if (m == 0) h.toString() else "$h:${m.toString().padStart(2, '0')}"
}
