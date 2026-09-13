package com.ash.axis.data.repository

import com.ash.axis.di.AppModule
import com.ash.core.network.AuthInterceptor
import com.ash.core.security.TokenManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CampusApiRoutingTest {
    @Test
    fun `campus feeds use the student token on the user client`() =
        runTest {
            val tokens = mockk<TokenManager>()
            every { tokens.getAccessToken() } returns "student-session"
            val requests = mutableListOf<Pair<String, String?>>()
            val client =
                OkHttpClient.Builder()
                    .addInterceptor(AuthInterceptor(tokens))
                    .addInterceptor { chain ->
                        val request = chain.request()
                        requests += request.url.encodedPath to request.header("authorization")
                        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                            .body("""{"result":[]}""".toResponseBody()).build()
                    }.build()
            val api = AppModule.provideUserApi(AppModule.provideQrRetrofit(client, Json))
            api.getNotifications(emptyMap()).body()?.close()
            api.getEvents(emptyMap()).body()?.close()
            api.getHolidays(emptyMap()).body()?.close()
            assertEquals(listOf("student-session", "student-session", "student-session"), requests.map { it.second })
            assertEquals(
                listOf("notifications/user/get", "calendar/getevent", "calendar/getholiday"),
                requests.map { it.first.trimStart('/') },
            )
        }
}
