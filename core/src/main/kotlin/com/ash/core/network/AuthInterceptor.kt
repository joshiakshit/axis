package com.ash.core.network

import com.ash.core.security.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor
    @Inject
    constructor(
        private val tokenManager: TokenManager,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            if (original.header("authorization") != null) return chain.proceed(original)
            val token = tokenManager.getAccessToken() ?: return chain.proceed(original)

            val authenticated =
                original.newBuilder()
                    .header("authorization", token)
                    .header("accept", "application/json")
                    .header("referer", original.url.encodedPath.trimStart('/'))
                    .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

            return chain.proceed(authenticated)
        }
    }
