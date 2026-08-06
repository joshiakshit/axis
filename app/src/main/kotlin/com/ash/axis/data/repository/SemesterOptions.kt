package com.ash.axis.data.repository

import com.ash.axis.domain.model.AcadYear
import com.ash.axis.domain.model.ClassInfo
import com.ash.axis.domain.model.SemesterOption

internal fun ClassInfo.displayLabel(year: AcadYear): String {
    val classLabel = item.ifBlank { id }
    val yearLabel = year.item.ifBlank { year.id }
    return if (yearLabel.isBlank() || classLabel.contains(yearLabel, ignoreCase = true)) {
        classLabel
    } else {
        "$classLabel - $yearLabel"
    }
}

internal fun latestSemesterFirst(): Comparator<SemesterOption> =
    compareByDescending<SemesterOption> { it.yearScore() }
        .thenByDescending { it.semesterScore() }
        .thenByDescending { it.classId.toIntOrNull() ?: 0 }
        .thenBy { it.label }

private fun SemesterOption.yearScore(): Int {
    return (yearId.extractInts() + label.extractInts())
        .filter { it > 1000 }
        .maxOrNull()
        ?: yearId.toIntOrNull()
        ?: 0
}

private fun SemesterOption.semesterScore(): Int {
    val text = label.lowercase()
    Regex("""sem(?:ester)?\s*([0-9]+|[ivxlcdm]+)""").find(text)?.groupValues?.getOrNull(1)?.let {
        return it.toIntOrNull() ?: romanToInt(it)
    }
    Regex("""\b([0-9]+)(?:st|nd|rd|th)?\s*sem""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return it
    }
    return classId.toIntOrNull() ?: 0
}

private fun String.extractInts(): List<Int> = Regex("""\d+""").findAll(this).mapNotNull { it.value.toIntOrNull() }.toList()

private fun romanToInt(value: String): Int {
    val values =
        mapOf(
            'i' to 1,
            'v' to 5,
            'x' to 10,
            'l' to 50,
            'c' to 100,
            'd' to 500,
            'm' to 1000,
        )
    var total = 0
    var previous = 0
    value.lowercase().reversed().forEach { char ->
        val current = values[char] ?: return 0
        if (current < previous) {
            total -= current
        } else {
            total += current
            previous = current
        }
    }
    return total
}
