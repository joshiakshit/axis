package com.ash.axis.ui.timetable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.core.ui.components.AppCard
import com.ash.core.ui.components.StatusBadge
import com.ash.core.ui.theme.AppDimens
import com.ash.core.ui.theme.AppShapes
import com.ash.core.ui.theme.SubjectColors

@Composable
internal fun TimetableSlotCard(
    displaySlot: DisplaySlot,
    modifier: Modifier = Modifier,
) {
    val slot = displaySlot.slot
    val isActive = displaySlot.progress?.let { it > 0f && it < 1f } == true
    val slotCode = (slot.subCode.takeIf { it.isNotBlank() } ?: slot.sub_shortname ?: slot.sub_short ?: slot.subjectId).uppercase()
    val accent = SubjectColors.accent(slotCode)
    val animatedProgress by animateFloatAsState(
        targetValue = displaySlot.progress ?: 0f,
        animationSpec = tween(600),
        label = "slot-progress",
    )

    AppCard(
        modifier = modifier,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(AppDimens.cardPadding)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.width(70.dp)) {
                    Text(
                        slot.fromTime,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "to ${slot.toTime}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SlotDetails(displaySlot, slotCode, accent, isActive, Modifier.weight(1f))
            }
            if (isActive) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(AppDimens.progressBarHeight).clip(AppShapes.full),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.12f),
                )
            }
        }
    }
}

@Composable
private fun SlotDetails(
    displaySlot: DisplaySlot,
    slotCode: String,
    accent: Color,
    isActive: Boolean,
    modifier: Modifier,
) {
    val slot = displaySlot.slot
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                displaySlot.displayName,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (slot.lectType.isNotBlank()) {
                StatusBadge(text = lectureTypeBadge(slot.lectType))
            }
        }
        val details = listOfNotNull(slotCode.takeIf { it.isNotBlank() }, slot.roomno.takeIf { it.isNotBlank() }?.let { "Room $it" })
        if (details.isNotEmpty()) {
            Text(details.joinToString(" · "), fontSize = 12.sp, color = accent)
        }
        val teacher = teacherLabel(displaySlot)
        if (teacher.isNotBlank()) {
            Text(teacher, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (displaySlot.isSubstitution || isActive) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (displaySlot.isSubstitution) StatusBadge(text = "SUB")
                if (isActive) StatusBadge(text = "LIVE")
            }
        }
    }
}

private fun teacherLabel(displaySlot: DisplaySlot): String {
    if (!displaySlot.isSubstitution) return displaySlot.teacherName.orEmpty()
    return listOfNotNull(
        displaySlot.substituteTeacher?.takeIf { it.isNotBlank() }?.let { "Substitute: $it" },
        displaySlot.teacherName?.takeIf { it.isNotBlank() }?.let { "for $it" },
    ).joinToString(" ")
}

private fun lectureTypeBadge(lectType: String): String =
    when {
        lectType.equals("PP+PR", ignoreCase = true) -> "LEC+LAB"
        lectType.equals("PR", ignoreCase = true) -> "LAB"
        else -> "LEC"
    }
