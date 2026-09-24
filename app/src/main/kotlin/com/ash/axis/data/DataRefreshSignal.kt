package com.ash.axis.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

enum class RefreshTrigger { ALL, ATTENDANCE, TIMETABLE }

data class RefreshEvent(
    val trigger: RefreshTrigger,
    val sourceId: Int,
)

@Singleton
class DataRefreshSignal
    @Inject
    constructor() {
        private val _signal =
            MutableSharedFlow<RefreshEvent>(
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val signal: SharedFlow<RefreshEvent> = _signal.asSharedFlow()

        fun emit(
            trigger: RefreshTrigger = RefreshTrigger.ALL,
            sourceId: Int = NO_SOURCE,
        ) {
            _signal.tryEmit(RefreshEvent(trigger, sourceId))
        }

        companion object {
            const val NO_SOURCE = 0

            private val counter = AtomicInteger(NO_SOURCE)

            // Event IDs prevent subscribers from reloading after their own refresh.
            fun newSourceId(): Int = counter.incrementAndGet()
        }
    }
