package com.ash.axis.di

import android.content.Context
import androidx.room.Room
import com.ash.axis.BuildConfig
import com.ash.axis.data.api.AuthApi
import com.ash.axis.data.api.ICloudEmsApi
import com.ash.axis.data.api.QrAttendanceApi
import com.ash.axis.data.db.AppDatabase
import com.ash.axis.data.db.CacheDao
import com.ash.axis.tenant.Tenants
import com.ash.core.security.SecretProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions")
object AppModule {
    @Provides
    @Singleton
    fun provideSecretProvider(): SecretProvider =
        object : SecretProvider {
            override val apiAuthToken: String = BuildConfig.API_AUTH_TOKEN
        }

    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

    @Provides
    @Singleton
    @Named("student")
    fun provideStudentRetrofit(
        client: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(Tenants.GU.apiBase)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @Named("auth")
    fun provideAuthRetrofit(
        client: OkHttpClient,
        json: Json,
        secretProvider: SecretProvider,
    ): Retrofit {
        val authClient =
            client.newBuilder()
                .addInterceptor { chain ->
                    val request =
                        chain.request().newBuilder()
                            .header("authorization", secretProvider.apiAuthToken)
                            .header("accept", "application/json")
                            .header("referer", "api.icloudems.com")
                            .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .build()
                    chain.proceed(request)
                }
                .build()

        return Retrofit.Builder()
            .baseUrl(Tenants.GU.authApiBase)
            .client(authClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @Named("qr")
    fun provideQrRetrofit(
        client: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(Tenants.GU.authApiBase)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideICloudEmsApi(
        @Named("student") retrofit: Retrofit,
    ): ICloudEmsApi = retrofit.create(ICloudEmsApi::class.java)

    @Provides
    @Singleton
    fun provideAuthApi(
        @Named("auth") retrofit: Retrofit,
    ): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideQrAttendanceApi(
        @Named("qr") retrofit: Retrofit,
    ): QrAttendanceApi = retrofit.create(QrAttendanceApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "axis.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCacheDao(db: AppDatabase): CacheDao = db.cacheDao()
}
