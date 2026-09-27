package com.ash.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

object SubjectColors {
    private val palette =
        listOf(
            Color(0xFF4285F4),
            Color(0xFF7C3AED),
            Color(0xFFF59E0B),
            Color(0xFFEF4444),
            Color(0xFF10B981),
            Color(0xFF14B8A6),
            Color(0xFFEC4899),
        )

    fun accent(subjectCode: String): Color = palette[abs(subjectCode.hashCode()) % palette.size]
}

object StatusColors {
    val present = Color(0xFF35D06E)
    val absent = Color(0xFFFF4D4F)
    val partial = Color(0xFFFFAA1A)
}
