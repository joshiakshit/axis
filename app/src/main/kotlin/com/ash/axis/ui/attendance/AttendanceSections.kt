package com.ash.axis.ui.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ash.axis.domain.usecase.AttendanceTone
import java.util.Locale

@Composable
internal fun SectionLabel(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
        )
    }
}

internal data class SubjectGroup(
    val title: String,
    val subjects: List<DecoratedSubject>,
)

internal fun subjectGroups(subjects: List<DecoratedSubject>): List<SubjectGroup> {
    val sorted =
        subjects.sortedWith(
            compareBy<DecoratedSubject> {
                when (it.tone) {
                    AttendanceTone.BAD -> 0
                    AttendanceTone.WARN -> 1
                    AttendanceTone.OK -> 2
                }
            }
                .thenBy { it.subject.lecType }
                .thenBy { it.subject.subName.lowercase(Locale.ENGLISH) },
        )
    return listOf(
        SubjectGroup("Theory", sorted.filter { it.subject.lecType.equals("PP", ignoreCase = true) }),
        SubjectGroup("Practical", sorted.filter { it.subject.lecType.equals("PR", ignoreCase = true) }),
        SubjectGroup("Combined", sorted.filter { it.subject.lecType.equals("PP+PR", ignoreCase = true) }),
        SubjectGroup(
            "Other",
            sorted.filterNot {
                it.subject.lecType.equals("PP", ignoreCase = true) ||
                    it.subject.lecType.equals("PR", ignoreCase = true) ||
                    it.subject.lecType.equals("PP+PR", ignoreCase = true)
            },
        ),
    ).filter { it.subjects.isNotEmpty() }
}
