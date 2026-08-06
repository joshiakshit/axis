package com.ash.axis.ui.timetable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.core.ui.components.StatusBadge
import com.ash.core.ui.theme.AppDimens
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.SubjectColors
import com.ash.core.ui.theme.cardColor
import com.ash.core.ui.theme.highlightColor

@Suppress("LongMethod")
@Composable
internal fun TimetableSlotCard(
    displaySlot: DisplaySlot,
    modifier: Modifier = Modifier,
) {
    val slot = displaySlot.slot
    val isActive = displaySlot.progress != null && displaySlot.progress > 0f && displaySlot.progress < 1f
    val isSub = displaySlot.isSubstitution
    val slotCode = (slot.subCode.takeIf { it.isNotBlank() } ?: slot.sub_shortname ?: slot.sub_short ?: slot.subjectId).uppercase()
    val tagColor = SubjectColors.accent(slotCode)
    val subColor = MaterialTheme.colorScheme.tertiary
    val animatedProgress by animateFloatAsState(
        targetValue = displaySlot.progress ?: 0f,
        animationSpec = tween(600),
        label = "slot-progress",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        color =
            when {
                isSub -> subColor.copy(alpha = 0.06f)
                isActive -> highlightColor(alpha = 0.15f)
                else -> cardColor()
            },
        border = if (isSub) BorderStroke(1.dp, subColor.copy(alpha = 0.25f)) else null,
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            SubjectStripe(tagColor)
            Row(
                modifier = Modifier.weight(1f).padding(AppDimens.cardPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SlotTimeColumn(displaySlot)
                Column(modifier = Modifier.weight(1f)) {
                    SlotBadgeRow(
                        slotCode = slotCode,
                        tagColor = tagColor,
                        lectType = slot.lectType,
                        isSub = isSub,
                        isActive = isActive,
                    )
                    Text(
                        displaySlot.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SubstitutionDetail(displaySlot)
                    Text(
                        buildString {
                            if (slot.roomno.isNotBlank()) append("Room ${slot.roomno}")
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SlotProgress(displaySlot.progress, animatedProgress, tagColor)
                }
            }
        }
    }
}

@Composable
private fun SubjectStripe(tagColor: androidx.compose.ui.graphics.Color) {
    Box(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(AppDimens.subjectStripeWidth)
                .background(tagColor),
    )
}

@Composable
private fun SlotTimeColumn(displaySlot: DisplaySlot) {
    val slot = displaySlot.slot
    Column(modifier = Modifier.width(AppDimens.timeColumnWidth)) {
        Text(
            slot.fromTime,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            slot.toTime,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SlotBadgeRow(
    slotCode: String,
    tagColor: androidx.compose.ui.graphics.Color,
    lectType: String,
    isSub: Boolean,
    isActive: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            slotCode,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = tagColor,
        )
        if (lectType.isNotBlank()) {
            StatusBadge(text = lectureTypeBadge(lectType))
        }
        if (isSub) {
            val subColor = MaterialTheme.colorScheme.tertiary
            StatusBadge(
                text = "SUB",
                color = subColor,
                background = subColor.copy(alpha = 0.15f),
            )
        }
        if (isActive) {
            StatusBadge(
                text = "LIVE",
                color = MaterialTheme.colorScheme.primary,
                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            )
        }
    }
}

private fun lectureTypeBadge(lectType: String): String =
    when {
        lectType.equals("PP+PR", ignoreCase = true) -> "LEC+LAB"
        lectType.equals("PR", ignoreCase = true) -> "LAB"
        else -> "LEC"
    }

@Composable
private fun SubstitutionDetail(displaySlot: DisplaySlot) {
    if (!displaySlot.isSubstitution) return

    val subColor = MaterialTheme.colorScheme.tertiary
    val detail =
        buildString {
            val sub = displaySlot.substituteTeacher
            val orig = displaySlot.originalTeacher
            if (!sub.isNullOrBlank()) append(sub)
            if (!orig.isNullOrBlank()) {
                if (isNotEmpty()) append(" - ")
                append("for $orig")
            }
        }
    if (detail.isNotBlank()) {
        Text(
            detail,
            fontSize = 11.sp,
            color = subColor.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun SlotProgress(
    progress: Float?,
    animatedProgress: Float,
    tagColor: androidx.compose.ui.graphics.Color,
) {
    if (progress == null) return

    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier =
            Modifier
                .fillMaxWidth()
                .height(AppDimens.progressBarHeight)
                .clip(AppShapes.full),
        color = tagColor,
        trackColor = tagColor.copy(alpha = 0.12f),
    )
}
