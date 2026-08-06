package com.ash.core.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object SecureHttpClient {
    fun build(authInterceptor: AuthInterceptor): OkHttpClient {
        val builder =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .cookieJar(InMemoryCookieJar())
                .addInterceptor(authInterceptor)
                .addInterceptor(RetryInterceptor())

        return builder.build()
    }
}

// Session-scoped cookie store so the PHPSESSID set by the first corecampus call is carried on later ones,
// matching the official app. Auth is still the Bearer token; the session cookie is supplementary.
private class InMemoryCookieJar : CookieJar {
    private val store = mutableMapOf<String, MutableMap<String, Cookie>>()

    @Synchronized
    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        val host = store.getOrPut(url.host) { mutableMapOf() }
        cookies.forEach { host[it.name] = it }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val host = store[url.host] ?: return emptyList()
        host.entries.removeAll { it.value.expiresAt < now }
        return host.values.filter { it.matches(url) }
    }
}
