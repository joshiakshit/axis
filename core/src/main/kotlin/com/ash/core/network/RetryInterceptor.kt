package com.ash.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val initialBackoffMs: Long = 1000,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastException: IOException? = null

        repeat(maxRetries) { attempt ->
            try {
                val response = chain.proceed(request)
                if (response.isSuccessful) return response
                val retryableServerError = response.code in 500..599
                if (!retryableServerError || attempt == maxRetries - 1) return response
                response.close()
                Thread.sleep(initialBackoffMs * (1L shl attempt))
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    Thread.sleep(initialBackoffMs * (1L shl attempt))
                }
            }
        }

        throw lastException ?: IOException("Request failed after $maxRetries retries")
    }
}
