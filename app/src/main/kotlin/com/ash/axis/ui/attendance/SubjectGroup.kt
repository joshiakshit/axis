package com.ash.axis.ui.attendance

import com.ash.axis.domain.usecase.AttendanceTone
import java.util.Locale

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
