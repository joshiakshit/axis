package com.ash.axis.ui.notifications

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun formatNotificationDate(
    raw: String,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val instant =
        raw.toLongOrNull()?.let { runCatching { Instant.ofEpochMilli(it) }.getOrNull() }
            ?: runCatching { Instant.parse(raw) }.getOrNull()
    if (instant != null) {
        return instant.atZone(zone).format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH))
    }
    return runCatching { LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) }.getOrDefault(raw)
}
