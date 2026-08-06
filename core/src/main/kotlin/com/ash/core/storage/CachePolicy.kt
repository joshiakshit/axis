package com.ash.core.storage

import java.time.Duration
import java.time.Instant

enum class CacheFreshness { FRESH, STALE, EXPIRED }

data class CachePolicy(
    val freshDuration: Duration,
    val staleDuration: Duration = freshDuration.multipliedBy(2),
) {
    fun evaluate(cachedAtMillis: Long): CacheFreshness {
        val age = Duration.between(Instant.ofEpochMilli(cachedAtMillis), Instant.now())
        return when {
            age <= freshDuration -> CacheFreshness.FRESH
            age <= staleDuration -> CacheFreshness.STALE
            else -> CacheFreshness.EXPIRED
        }
    }

    companion object {
        val DASHBOARD = CachePolicy(Duration.ofMinutes(30))
        val ATTENDANCE = CachePolicy(Duration.ofHours(24))
        val TIMETABLE = CachePolicy(Duration.ofHours(1))
        val DAYWISE = CachePolicy(Duration.ofHours(24))
    }
}

data class CachedResult<T>(
    val data: T,
    val freshness: CacheFreshness,
    val cachedAtMillis: Long,
) {
    val isStale: Boolean = freshness != CacheFreshness.FRESH
}
