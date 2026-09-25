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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

data class AcademicSnapshot<T>(
    val data: T? = null,
    val updatedAtMillis: Long? = null,
    val refreshing: Boolean = false,
    val error: Throwable? = null,
    val freshness: CacheFreshness? = null,
)

@Suppress("TooGenericExceptionCaught", "ReturnCount", "CyclomaticComplexMethod", "ComplexCondition")
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
        var started = false
        var speculative = false
    }

    private val mutex = Mutex()
    private val permits = Semaphore(2)
    private val entries = mutableMapOf<K, Entry<T>>()
    private var invalidationEpoch = 0L

    suspend fun observe(key: K): StateFlow<AcademicSnapshot<T>> =
        mutex.withLock {
            entries.getOrPut(key) { Entry() }.state
        }

    suspend fun request(
        key: K,
        force: Boolean = false,
        speculative: Boolean = false,
    ): StateFlow<AcademicSnapshot<T>> {
        val state = observe(key)
        val observed = mutex.withLock { entries[key] to invalidationEpoch }
        val cached =
            try {
                read(key)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutex.withLock {
                    val entry = entries[key]
                    if (entry != null && entry === observed.first && invalidationEpoch == observed.second) {
                        entry.state.value = entry.state.value.copy(refreshing = false, error = error)
                    }
                }
                return state
            }
        mutex.withLock {
            val entry = entries[key] ?: return state
            if (entry !== observed.first || invalidationEpoch != observed.second) return state
            if (cached != null && (entry.state.value.updatedAtMillis ?: Long.MIN_VALUE) <= cached.cachedAtMillis) {
                entry.state.value =
                    entry.state.value.copy(
                        data = cached.data,
                        updatedAtMillis = cached.cachedAtMillis,
                        freshness = cached.freshness,
                    )
            } else if (cached == null && entry.state.value.data != null) {
                entry.state.value = entry.state.value.copy(freshness = CacheFreshness.STALE)
            }
            if (entry.job?.isActive == true) {
                if (!speculative) entry.speculative = false
                if (force && !entry.jobForced) entry.forceAfterLoad = true
                return state
            }
            if (!force && cached?.freshness == CacheFreshness.FRESH) return state
            entry.speculative = speculative
            launch(key, entry, force)
        }
        return state
    }

    suspend fun prioritize(key: K) {
        mutex.withLock {
            entries.forEach { (queuedKey, entry) ->
                if (queuedKey != key && entry.speculative && entry.job?.isActive == true && !entry.started) {
                    entry.generation++
                    entry.job?.cancel()
                    entry.job = null
                    entry.forceAfterLoad = false
                    entry.state.value = entry.state.value.copy(refreshing = false)
                }
            }
        }
    }

    suspend fun invalidate(
        key: K,
        reload: Boolean = true,
        clear: suspend () -> Unit = {},
    ) {
        mutex.withLock {
            invalidationEpoch++
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
            invalidationEpoch++
            entries.values.forEach {
                it.generation++
                it.job?.cancel()
                it.state.value = AcademicSnapshot()
            }
            entries.clear()
        }
    }

    suspend fun invalidateAll(
        clear: suspend () -> Unit,
        reloadKey: K? = null,
    ) {
        mutex.withLock {
            invalidationEpoch++
            entries.values.forEach {
                it.generation++
                it.job?.cancel()
                it.job = null
                it.forceAfterLoad = false
                it.state.value = it.state.value.copy(refreshing = false, freshness = CacheFreshness.STALE)
            }
            clear()
            reloadKey?.let { key -> entries[key]?.let { launch(key, it, forced = true) } }
        }
    }

    suspend fun clearCache(clear: suspend () -> Unit) {
        mutex.withLock {
            invalidationEpoch++
            entries.values.forEach {
                it.generation++
                it.job?.cancel()
                it.state.value = AcademicSnapshot()
            }
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
        entry.started = false
        entry.state.value = entry.state.value.copy(refreshing = true, error = null)
        entry.job =
            scope.launch {
                try {
                    permits.withPermit {
                        mutex.withLock {
                            if (entries[key] !== entry || entry.generation != generation) return@withPermit
                            entry.started = true
                        }
                        val data = fetch(key)
                        mutex.withLock {
                            if (entries[key] !== entry || entry.generation != generation) return@withLock
                            val updatedAt = write(key, data)
                            entry.state.value = AcademicSnapshot(data, updatedAt, freshness = CacheFreshness.FRESH)
                        }
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
