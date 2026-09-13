package com.ash.axis.ui.notifications

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class NotificationDateTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun `millisecond timestamps display the local date and time`() {
        val millis = Instant.parse("2026-09-13T10:00:00Z").toEpochMilli().toString()
        assertEquals("13 Sep 2026, 3:30 PM", formatNotificationDate(millis, zone))
    }

    @Test
    fun `ISO timestamps display in the same local format`() {
        assertEquals("13 Sep 2026, 3:30 PM", formatNotificationDate("2026-09-13T10:00:00Z", zone))
    }

    @Test
    fun `plain dates do not acquire a time`() {
        assertEquals("13 Sep 2026", formatNotificationDate("2026-09-13", zone))
        assertEquals("", formatNotificationDate("", zone))
    }
}
