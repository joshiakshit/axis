package com.ash.axis.data.academic

import com.ash.core.storage.CacheFreshness
import com.ash.core.storage.CachedResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AcademicSnapshot<T>(
    val data: T? = null,
    val updatedAtMillis: Long? = null,
    val refreshing: Boolean = false,
    val error: Throwable? = null,
    val freshness: CacheFreshness? = null,
)

@Suppress("TooGenericExceptionCaught", "ReturnCount")
class AcademicResource<K, T>(
    private val scope: CoroutineScope,
    private val read: suspend (K) -> CachedResult<T>?,
    private val fetch: suspend (K) -> T,
    private val write: suspend (K, T) -> Long,
) {
    private class Entry<T> {
        val state = MutableStateFlow(AcademicSnapshot<T>())
        var job: Job? = null
        var generation = 0L
        var forceAfterLoad = false
        var jobForced = false
    }

    private val mutex = Mutex()
    private val entries = mutableMapOf<K, Entry<T>>()

    suspend fun observe(key: K): StateFlow<AcademicSnapshot<T>> =
        mutex.withLock {
            entries.getOrPut(key) { Entry() }.state
        }

    suspend fun request(
        key: K,
        force: Boolean = false,
    ): StateFlow<AcademicSnapshot<T>> {
        val state = observe(key)
        val observedGeneration = mutex.withLock { entries[key]?.generation }
        val cached = read(key)
        mutex.withLock {
            val entry = entries[key] ?: return state
            if (entry.generation != observedGeneration) return state
            if (entry.state.value.data == null && cached != null) {
                entry.state.value = AcademicSnapshot(cached.data, cached.cachedAtMillis, freshness = cached.freshness)
            }
            if (entry.job?.isActive == true) {
                if (force && !entry.jobForced) entry.forceAfterLoad = true
                return state
            }
            if (!force && entry.state.value.freshness == CacheFreshness.FRESH) return state
            launch(key, entry, force)
        }
        return state
    }

    suspend fun invalidate(
        key: K,
        reload: Boolean = true,
        clear: suspend () -> Unit = {},
    ) {
        mutex.withLock {
            val entry = entries[key]
            if (entry == null) {
                clear()
                return
            }
            entry.generation++
            entry.job?.cancel()
            entry.job = null
            entry.forceAfterLoad = false
            clear()
            entry.state.value = entry.state.value.copy(refreshing = false, freshness = CacheFreshness.STALE)
            if (reload) launch(key, entry, forced = true)
        }
    }

    suspend fun deactivate() {
        mutex.withLock {
            entries.values.forEach {
                it.generation++
                it.job?.cancel()
                it.state.value = AcademicSnapshot()
            }
            entries.clear()
        }
    }

    suspend fun invalidateAll(clear: suspend () -> Unit) {
        mutex.withLock {
            entries.values.forEach {
                it.generation++
                it.job?.cancel()
                it.job = null
                it.forceAfterLoad = false
                it.state.value = it.state.value.copy(refreshing = false, freshness = CacheFreshness.STALE)
            }
            clear()
            entries.forEach { (key, entry) -> launch(key, entry, forced = true) }
        }
    }

    suspend fun clearCache(clear: suspend () -> Unit) {
        mutex.withLock {
            entries.values.forEach {
                it.generation++
                it.job?.cancel()
                it.state.value = AcademicSnapshot()
            }
            entries.clear()
            clear()
        }
    }

    private fun launch(
        key: K,
        entry: Entry<T>,
        forced: Boolean,
    ) {
        val generation = ++entry.generation
        entry.jobForced = forced
        entry.state.value = entry.state.value.copy(refreshing = true, error = null)
        entry.job =
            scope.launch {
                try {
                    val data = fetch(key)
                    mutex.withLock {
                        if (entries[key] !== entry || entry.generation != generation) return@withLock
                        val updatedAt = write(key, data)
                        entry.state.value = AcademicSnapshot(data, updatedAt, freshness = CacheFreshness.FRESH)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    mutex.withLock {
                        if (entries[key] === entry && entry.generation == generation) {
                            entry.state.value = entry.state.value.copy(refreshing = false, error = error)
                        }
                    }
                } finally {
                    mutex.withLock {
                        if (entries[key] === entry && entry.generation == generation) {
                            entry.job = null
                            if (entry.forceAfterLoad) {
                                entry.forceAfterLoad = false
                                launch(key, entry, forced = true)
                            } else {
                                entry.state.value = entry.state.value.copy(refreshing = false)
                            }
                        }
                    }
                }
            }
    }
}
