package com.hfad.htmlactivity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 数据层：封装 DeepSeek API 的网络请求
 * 使用 OkHttp 同步调用 + withContext(Dispatchers.IO) 实现协程友好的 suspend 函数
 */
class ChatRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 向 DeepSeek API 发送对话请求
     * @param userPrompt 用户输入
     * @param history 历史对话记录 [(role, content), ...]
     * @return Result.success(ai回复文本) 或 Result.failure(异常)
     */
    suspend fun sendMessage(
        userPrompt: String,
        history: List<Pair<String, String>>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestJson = JSONObject().apply {
                put("model", "deepseek-chat")
                put("stream", false)
                put("temperature", 0.7)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    })
                    for ((role, content) in history) {
                        put(JSONObject().apply {
                            put("role", role)
                            put("content", content)
                        })
                    }
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                })
            }

            val requestBody = requestJson.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer $API_KEY")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()

            val content = JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val BASE_URL = "https://api.deepseek.com/v1/chat/completions"
        // TODO: 将 API Key 迁移至 local.properties / BuildConfig / 服务端代理
        private const val API_KEY = "sk-37a150ff568842f08e314f3e8221c538"
        private const val SYSTEM_PROMPT = "你是一个有帮助的AI助手，请用友好的方式回答用户的问题。"
    }
}
