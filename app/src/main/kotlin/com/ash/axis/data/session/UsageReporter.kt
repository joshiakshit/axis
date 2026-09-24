package com.ash.axis.data.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

// Batch counters in memory. Reporting failures must not interrupt the caller.
@Singleton
class UsageReporter
    @Inject
    constructor(
        private val session: AxisSessionRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mutex = Mutex()
        private val counts = mutableMapOf<String, Int>()
        private var flushJob: Job? = null

        fun log(
            name: String,
            count: Int = 1,
        ) {
            scope.launch {
                mutex.withLock {
                    counts[name] = (counts[name] ?: 0) + count
                    if (flushJob?.isActive != true) flushJob = scope.launch { delayThenFlush() }
                }
            }
        }

        private suspend fun delayThenFlush() {
            delay(FLUSH_DELAY_MS)
            flush()
        }

        suspend fun flush() {
            val batch =
                mutex.withLock {
                    if (counts.isEmpty()) return
                    counts.map { UsageEvent(it.key, it.value) }.also { counts.clear() }
                }
            session.logEvents(batch)
        }

        companion object {
            const val FLUSH_DELAY_MS = 3_000L

            // The backend accepts [a-z0-9_] event names.
            const val QR_SCAN = "qr_scan"
            const val QR_FAIL = "qr_fail"
            const val EXPORT = "export"
        }
    }
