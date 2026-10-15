package com.livrechat.app.data.remote

import com.livrechat.app.data.local.PreferencesManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient(private val preferencesManager: PreferencesManager) {

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()

        preferencesManager.accessToken?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }

        builder.header("Content-Type", "application/json")
        chain.proceed(builder.build())
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private var currentBaseUrl: String? = null
    private var cachedApiService: ApiService? = null

    val apiService: ApiService
        @Synchronized get() {
            var base = preferencesManager.serverUrl.trim()
            if (!base.endsWith("/")) {
                base += "/"
            }
            if (cachedApiService == null || currentBaseUrl != base) {
                currentBaseUrl = base
                cachedApiService = Retrofit.Builder()
                    .baseUrl(base)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService::class.java)
            }
            return cachedApiService!!
        }
}
