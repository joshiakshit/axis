package com.ash.core

import com.ash.core.storage.CacheFreshness
import com.ash.core.storage.CachePolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class CachePolicyTest {
    @Test
    fun `fresh when within fresh duration`() {
        val policy = CachePolicy(Duration.ofMinutes(30))
        val fiveMinutesAgo = System.currentTimeMillis() - Duration.ofMinutes(5).toMillis()
        assertEquals(CacheFreshness.FRESH, policy.evaluate(fiveMinutesAgo))
    }

    @Test
    fun `stale when past fresh but within stale duration`() {
        val policy = CachePolicy(Duration.ofMinutes(30))
        val fortyMinutesAgo = System.currentTimeMillis() - Duration.ofMinutes(40).toMillis()
        assertEquals(CacheFreshness.STALE, policy.evaluate(fortyMinutesAgo))
    }

    @Test
    fun `expired when past stale duration`() {
        val policy = CachePolicy(Duration.ofMinutes(30))
        val twoHoursAgo = System.currentTimeMillis() - Duration.ofHours(2).toMillis()
        assertEquals(CacheFreshness.EXPIRED, policy.evaluate(twoHoursAgo))
    }

    @Test
    fun `custom stale duration respected`() {
        val policy = CachePolicy(Duration.ofMinutes(10), Duration.ofHours(1))
        val thirtyMinutesAgo = System.currentTimeMillis() - Duration.ofMinutes(30).toMillis()
        assertEquals(CacheFreshness.STALE, policy.evaluate(thirtyMinutesAgo))
    }

    @Test
    fun `preset DASHBOARD is 30 minutes fresh`() {
        val justNow = System.currentTimeMillis()
        assertEquals(CacheFreshness.FRESH, CachePolicy.DASHBOARD.evaluate(justNow))
    }

    @Test
    fun `preset ATTENDANCE is 24 hours fresh`() {
        val twelveHoursAgo = System.currentTimeMillis() - Duration.ofHours(12).toMillis()
        assertEquals(CacheFreshness.FRESH, CachePolicy.ATTENDANCE.evaluate(twelveHoursAgo))
    }

    @Test
    fun `preset TIMETABLE is 1 hour fresh`() {
        val ninetyMinutesAgo = System.currentTimeMillis() - Duration.ofMinutes(90).toMillis()
        assertEquals(CacheFreshness.STALE, CachePolicy.TIMETABLE.evaluate(ninetyMinutesAgo))
    }

    @Test
    fun `just inside fresh boundary is fresh`() {
        val policy = CachePolicy(Duration.ofMinutes(30))
        val justUnderThirty = System.currentTimeMillis() - Duration.ofMinutes(29).toMillis()
        assertEquals(CacheFreshness.FRESH, policy.evaluate(justUnderThirty))
    }
}
