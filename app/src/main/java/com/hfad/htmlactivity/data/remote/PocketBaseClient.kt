package com.hfad.htmlactivity.data.remote

import com.hfad.htmlactivity.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * 构建 PocketBase Retrofit 客户端
 * tokenProvider 从 SessionManager 提供当前登录 token，由 OkHttp 拦截器统一注入 Authorization
 */
object PocketBaseClient {

    fun create(tokenProvider: () -> String?): PocketBaseApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = tokenProvider()
                val request = if (token.isNullOrEmpty()) {
                    chain.request()
                } else {
                    chain.request().newBuilder()
                        .addHeader("Authorization", token)
                        .build()
                }
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizeUrl(BuildConfig.POCKETBASE_URL))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PocketBaseApi::class.java)
    }

    private fun normalizeUrl(url: String): String =
        if (url.endsWith("/")) url else "$url/"
}
