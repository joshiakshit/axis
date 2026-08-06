package com.ash.core

import com.ash.core.util.DateUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DateUtilsTest {
    @Test
    fun `just now for recent timestamps`() {
        assertEquals("just now", DateUtils.relativeTime(System.currentTimeMillis()))
    }

    @Test
    fun `minutes ago`() {
        val fiveMinutesAgo = System.currentTimeMillis() - 5 * 60 * 1000
        assertEquals("5m ago", DateUtils.relativeTime(fiveMinutesAgo))
    }

    @Test
    fun `hours ago`() {
        val threeHoursAgo = System.currentTimeMillis() - 3 * 60 * 60 * 1000
        assertEquals("3h ago", DateUtils.relativeTime(threeHoursAgo))
    }

    @Test
    fun `days ago`() {
        val twoDaysAgo = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000
        assertEquals("2d ago", DateUtils.relativeTime(twoDaysAgo))
    }
}
