package com.ash.axis.ui.grades

import com.ash.axis.domain.model.CourseMarks
import com.ash.axis.domain.model.LectureMark

internal data class ParsedMark(
    val obtained: Double,
    val max: Double?,
)

internal data class InsightScore(
    val label: String,
    val percent: Double,
)

internal fun List<CourseMarks>.toSubjectScores(): List<InsightScore> =
    mapNotNull { course ->
        val marks =
            course.lectures.mapNotNull { lecture ->
                val mark = lecture.markValue()
                if (mark?.max != null && mark.max > 0) mark.obtained to mark.max else null
            }
        val obtained = marks.sumOf { it.first }
        val max = marks.sumOf { it.second }
        if (max <= 0) {
            null
        } else {
            InsightScore(
                label = course.subName.ifBlank { course.subShort.ifBlank { course.subCode } },
                percent = (obtained / max).coerceIn(0.0, 1.0),
            )
        }
    }.sortedByDescending { it.percent }

internal fun List<CourseMarks>.toComponentScores(): List<InsightScore> =
    flatMap { it.lectures }
        .mapNotNull { lecture ->
            val mark = lecture.markValue()
            if (mark?.max != null && mark.max > 0) {
                val label = lecture.lecType.ifBlank { lecture.examType.ifBlank { "Component" } }
                label to (mark.obtained / mark.max).coerceIn(0.0, 1.0)
            } else {
                null
            }
        }
        .groupBy({ it.first }, { it.second })
        .map { (label, percentages) ->
            InsightScore(
                label = label,
                percent = percentages.average().coerceIn(0.0, 1.0),
            )
        }
        .sortedByDescending { it.percent }

internal fun LectureMark.markValue(): ParsedMark? {
    val display = obtainedMarks.trim()
    if (display.isBlank() || display == "-") return null
    val parts = display.split("/")
    val obtained = parts.firstOrNull()?.trim()?.toDoubleOrNull() ?: return null
    val max = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
    return ParsedMark(obtained = obtained, max = max)
}

internal fun Double.cleanNumber(): String =
    if (this % 1.0 == 0.0) {
        toInt().toString()
    } else {
        "%.2f".format(this).trimEnd('0').trimEnd('.')
    }

internal fun relativeTime(deltaMs: Long): String {
    val seconds = (deltaMs / 1000).coerceAtLeast(0)
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        seconds < 60 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}
