package com.ash.core.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen

fun <T> Flow<T>.asResult(): Flow<Result<T>> =
    map<T, Result<T>> { Result.Success(it) }
        .onStart { emit(Result.Loading) }
        .catch { emit(Result.Error(it)) }

fun <T> Flow<T>.retryWithBackoff(
    maxRetries: Long = 3,
    initialDelayMs: Long = 1000,
): Flow<T> =
    retryWhen { _, attempt ->
        if (attempt < maxRetries) {
            delay(initialDelayMs * (1L shl attempt.toInt()))
            true
        } else {
            false
        }
    }
