package com.ash.axis.ui.notifications

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationLinksTest {
    @Test
    fun `only web links with a host can be opened`() {
        assertTrue(isNotificationLink("https://college.example/notice?id=1"))
        assertTrue(isNotificationLink("http://college.example/notice"))
        assertFalse(isNotificationLink("javascript:alert(1)"))
        assertFalse(isNotificationLink("file:///data/private"))
        assertFalse(isNotificationLink("intent://open"))
        assertFalse(isNotificationLink("https:///notice"))
        assertFalse(isNotificationLink("https://bad link"))
    }
}
