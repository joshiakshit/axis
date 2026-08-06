package com.ash.core.util

sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>

    data class Error(val exception: Throwable, val message: String? = null) : Result<Nothing>

    data object Loading : Result<Nothing>
}
