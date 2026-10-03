package com.burton.groupme.di

import android.content.Context
import coil.ImageLoader
import com.burton.groupme.data.groupme.TokenHolder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun okHttpClient(tokenHolder: TokenHolder): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(groupMeAuthInterceptor(tokenHolder))
            .build()

    @Provides
    @Singleton
    fun imageLoader(
        @ApplicationContext context: Context,
        client: OkHttpClient,
    ): ImageLoader =
        ImageLoader.Builder(context)
            .okHttpClient(client)
            .crossfade(true)
            .build()

    private fun groupMeAuthInterceptor(tokenHolder: TokenHolder): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val host = request.url.host
        val token = tokenHolder.token
        val needsAuth = token.isNotBlank() &&
            hostNeedsAuth(host) &&
            request.header("X-Access-Token").isNullOrBlank() &&
            request.header("Authorization").isNullOrBlank()
        val next = if (needsAuth) {
            request.newBuilder().header("X-Access-Token", token).build()
        } else {
            request
        }
        chain.proceed(next)
    }

    private fun hostNeedsAuth(host: String): Boolean =
        host == "api.groupme.com" || host.endsWith(".groupme.com")
}
