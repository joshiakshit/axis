package com.ash.core.util

import java.time.Duration
import java.time.Instant

object DateUtils {
    fun relativeTime(epochMillis: Long): String {
        val duration = Duration.between(Instant.ofEpochMilli(epochMillis), Instant.now())
        val minutes = duration.toMinutes()
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            else -> "${minutes / 1440}d ago"
        }
    }
}
