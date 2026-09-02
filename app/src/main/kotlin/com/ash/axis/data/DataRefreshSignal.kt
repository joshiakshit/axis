package com.ash.axis.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class RefreshTrigger { ALL, ATTENDANCE, TIMETABLE }

@Singleton
class DataRefreshSignal
    @Inject
    constructor() {
        private val _signal = MutableSharedFlow<RefreshTrigger>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val signal: SharedFlow<RefreshTrigger> = _signal.asSharedFlow()

        fun emit(trigger: RefreshTrigger = RefreshTrigger.ALL) {
            _signal.tryEmit(trigger)
        }
    }
