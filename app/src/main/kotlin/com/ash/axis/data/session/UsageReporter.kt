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

// Aggregate, privacy-safe usage counters (e.g. "qr_scan", "export"). Calls are cheap and non-blocking:
// `log()` just bumps an in-memory tally and coalesces a flush, which posts the batch through the session
// repository (which no-ops when governance is disabled or before the user has a session token). Nothing here
// can throw into a caller — it's telemetry, never on a critical path.
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

            // Event names (kept short + stable; the backend sanitizes to [a-z0-9_]).
            const val QR_SCAN = "qr_scan"
            const val QR_FAIL = "qr_fail"
            const val EXPORT = "export"
        }
    }
