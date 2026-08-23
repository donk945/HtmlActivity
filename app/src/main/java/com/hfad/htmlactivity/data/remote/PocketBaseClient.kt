package com.hfad.htmlactivity.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * 构建 PocketBase Retrofit 客户端
 * tokenProvider 从 SessionManager 提供当前登录 token，由 OkHttp 拦截器统一注入 Authorization
 */
object PocketBaseClient {

    // PocketBase 服务器地址
    private const val BASE_URL = "http://139.196.180.58:8090/"

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
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PocketBaseApi::class.java)
    }
}
