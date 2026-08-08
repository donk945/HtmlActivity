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
     * CHAT 分支：纯文字对话
     */
    suspend fun sendChatRequest(
        userPrompt: String,
        history: List<Pair<String, String>>
    ): Result<String> = sendRequest(
        userPrompt = userPrompt,
        history = history,
        systemPrompt = SYSTEM_PROMPT_CHAT
    )

    /**
     * HTML_GENERATE 分支：AI 生成 HTML 页面
     */
    suspend fun sendHtmlGenerateRequest(
        userPrompt: String,
        history: List<Pair<String, String>>
    ): Result<String> = sendRequest(
        userPrompt = userPrompt,
        history = history,
        systemPrompt = SYSTEM_PROMPT_HTML
    )

    /**
     * 意图分类：当本地关键词匹配失败时，用 AI 判断用户意图
     * 使用低温 + 短回复策略，快速且确定性高
     */
    suspend fun classifyIntent(userInput: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestJson = JSONObject().apply {
                put("model", "deepseek-chat")
                put("stream", false)
                put("temperature", 0.0)
                put("max_tokens", 10)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT_CLASSIFY)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userInput)
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
            val body = response.body?.string().orEmpty()
            response.close()

            val content = JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 通用请求方法：构造 JSON → OkHttp POST → 解析 choices[0].message.content
     */
    private suspend fun sendRequest(
        userPrompt: String,
        history: List<Pair<String, String>>,
        systemPrompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestJson = JSONObject().apply {
                put("model", "deepseek-chat")
                put("stream", false)
                put("temperature", 0.7)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
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
            val body = response.body?.string().orEmpty()
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

        /** CHAT 分支 System Prompt：友好对话助手 */
        private const val SYSTEM_PROMPT_CHAT =
            "你是一个有帮助的AI助手，请用友好的方式回答用户的问题。"

        /** HTML_GENERATE 分支 System Prompt：前端专家生成完整页面 */
        private const val SYSTEM_PROMPT_HTML =
            "你是一个资深前端开发专家。根据用户需求生成一个完整的、美观的、" +
            "可独立运行的 HTML 页面（包含 CSS 和 JS，全部内联）。\n" +
            "输出要求：\n" +
            "- 只输出完整的 HTML 代码，不要任何解释文字\n" +
            "- 使用现代设计风格，配色美观\n" +
            "- 适配移动端屏幕（viewport 使用 device-width）\n" +
            "- 如果有交互功能，用内联 JS 实现\n" +
            "- 页面必须包含 <!DOCTYPE html> 声明"

        /** 意图分类 System Prompt：AI 兜底判断用户是想聊天还是生成页面 */
        private const val SYSTEM_PROMPT_CLASSIFY =
            "你是一个意图分类器。分析用户的输入，判断用户是想让你生成一个 HTML 页面，" +
            "还是只是想进行普通对话。\n" +
            "判断标准：\n" +
            "- HTML_GENERATE：用户想要生成网页、HTML 页面、前端代码、可视化工具、" +
            "计算器、游戏、图表、UI 组件等可交互的页面\n" +
            "- CHAT：用户只是提问、聊天、询问知识、请求解释、讨论问题等\n" +
            "只输出 HTML_GENERATE 或 CHAT，不要输出任何其他内容。"
    }
}
