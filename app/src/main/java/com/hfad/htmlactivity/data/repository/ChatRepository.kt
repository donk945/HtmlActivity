package com.hfad.htmlactivity.data.repository

import com.hfad.htmlactivity.data.model.ModelConfig
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
 * 数据层：封装 OpenAI 兼容 API 的网络请求（DeepSeek / OpenAI / 通义 / Kimi 等）
 * baseUrl / model / apiKey 全部由用户配置动态传入，不再硬编码
 */
class ChatRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * VLM_VISION 分支：多模态图像识别
     */
    suspend fun sendVlmRequest(
        config: ModelConfig,
        imageBase64: String,
        userPrompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestJson = JSONObject().apply {
                put("model", config.model)
                put("stream", false)
                put("temperature", 0.7)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT_VLM)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", userPrompt)
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$imageBase64")
                                })
                            })
                        })
                    })
                })
            }

            val requestBody = requestJson.toString()
                .toRequestBody("application/json".toMediaType())

            val request = buildRequest(config, requestBody)

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

    /**
     * CHAT 分支：纯文字对话
     */
    suspend fun sendChatRequest(
        config: ModelConfig,
        userPrompt: String,
        history: List<Pair<String, String>>
    ): Result<String> = sendRequest(
        config = config,
        userPrompt = userPrompt,
        history = history,
        systemPrompt = SYSTEM_PROMPT_CHAT
    )

    /**
     * HTML_GENERATE 分支：AI 生成 HTML 页面
     */
    suspend fun sendHtmlGenerateRequest(
        config: ModelConfig,
        userPrompt: String,
        history: List<Pair<String, String>>
    ): Result<String> = sendRequest(
        config = config,
        userPrompt = userPrompt,
        history = history,
        systemPrompt = SYSTEM_PROMPT_HTML
    )

    /**
     * 意图分类：本地关键词匹配失败时，用 AI 兜底判断
     */
    suspend fun classifyIntent(config: ModelConfig, userInput: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val requestJson = JSONObject().apply {
                    put("model", config.model)
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

                val request = buildRequest(config, requestBody)

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
     * 通用请求方法
     */
    private suspend fun sendRequest(
        config: ModelConfig,
        userPrompt: String,
        history: List<Pair<String, String>>,
        systemPrompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestJson = JSONObject().apply {
                put("model", config.model)
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

            val request = buildRequest(config, requestBody)

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

    /**
     * 构造请求：动态 baseUrl + apiKey
     */
    private fun buildRequest(config: ModelConfig, requestBody: okhttp3.RequestBody): Request {
        val url = chatCompletionsUrl(config.baseUrl)
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
    }

    companion object {
        /** 把用户填的 baseUrl 规范化成 /chat/completions 端点 */
        private fun chatCompletionsUrl(baseUrl: String): String {
            val base = baseUrl.trimEnd('/')
            return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        }

        private const val SYSTEM_PROMPT_CHAT =
            "你是一个有帮助的AI助手，请用友好的方式回答用户的问题。"

        private const val SYSTEM_PROMPT_HTML =
            "你是一个资深前端开发专家。根据用户需求生成一个完整的、美观的、" +
            "可独立运行的 HTML 页面（包含 CSS 和 JS，全部内联）。\n" +
            "输出要求：\n" +
            "- 只输出完整的 HTML 代码，不要任何解释文字\n" +
            "- 使用现代设计风格，配色美观\n" +
            "- 适配移动端屏幕（viewport 使用 device-width）\n" +
            "- 如果有交互功能，用内联 JS 实现\n" +
            "- 页面必须包含 <!DOCTYPE html> 声明"

        private const val SYSTEM_PROMPT_VLM =
            "你是一个视觉识别助手。用户会提供一张图片和一个问题，" +
            "请仔细观察图片内容并回答用户的问题。\n" +
            "如果用户只是要求描述图片，请详细描述图片中的场景、物体、文字、人物等信息。"

        private const val SYSTEM_PROMPT_CLASSIFY =
            "你是一个意图分类器。分析用户的输入，判断用户是想让你生成一个 HTML 页面，" +
            "还是只是想进行普通对话，或者是想让你识别/分析图片。\n" +
            "判断标准：\n" +
            "- HTML_GENERATE：用户想要生成网页、HTML 页面、前端代码、可视化工具、" +
            "计算器、游戏、图表、UI 组件等可交互的页面\n" +
            "- VLM_VISION：用户提到了图片、照片、图像，想让你看/识别/分析图片内容\n" +
            "- CHAT：用户只是提问、聊天、询问知识、请求解释、讨论问题等\n" +
            "只输出 HTML_GENERATE、VLM_VISION 或 CHAT，不要输出任何其他内容。"
    }
}
