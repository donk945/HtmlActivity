package com.hfad.htmlactivity

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository()

    // 聊天历史记录，用于多轮对话上下文
    private val historyList = mutableListOf<Pair<String, String>>()

    // VLM 分支：用户附带的待识别图片
    private var pendingImageBitmap: Bitmap? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val messages = repository.loadMessages(getApplication())
            _uiState.update { it.copy(messages = messages) }
        }
    }

    /**
     * VLM 分支：用户附件图片，供 Fragment 在拍照/选图后调用
     */
    fun attachImage(bitmap: Bitmap) {
        pendingImageBitmap = bitmap
        _uiState.update { it.copy(hasPendingImage = true) }
    }

    fun clearPendingImage() {
        pendingImageBitmap = null
        _uiState.update { it.copy(hasPendingImage = false) }
    }

    /**
     * 发送用户消息，先做意图分类再路由到不同分支
     * 分类策略：有未处理图片 → VLM_VISION，否则本地关键词匹配 → 未命中则 AI 兜底 → 仍失败默认 CHAT
     */
    fun sendMessage(userInput: String) {
        val userMessage = Message(content = userInput, isUser = true)
        val loadingMessage = Message(content = "正在思考...", isUser = false)

        _uiState.update { state ->
            state.copy(
                messages = state.messages + listOf(userMessage, loadingMessage),
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            val intent = if (pendingImageBitmap != null) {
                IntentType.VLM_VISION
            } else {
                val keywordIntent = classifyIntent(userInput)
                keywordIntent ?: classifyIntentByAi(userInput)
            }

            when (intent) {
                IntentType.CHAT -> handleChat(userInput)
                IntentType.HTML_GENERATE -> handleHtmlGenerate(userInput)
                IntentType.VLM_VISION -> handleVlmVision(userInput)
            }
        }
    }

    /**
     * CHAT 分支：纯文字对话
     */
    private suspend fun handleChat(userInput: String) {
        val result = repository.sendChatRequest(userInput, historyList.toList())

        result.fold(
            onSuccess = { aiResponse ->
                historyList.add("user" to userInput)
                historyList.add("assistant" to aiResponse)

                _uiState.update { state ->
                    val updatedMessages = state.messages.toMutableList().apply {
                        removeAt(size - 1)
                        add(Message(content = aiResponse, isUser = false))
                    }
                    state.copy(
                        messages = updatedMessages,
                        isLoading = false,
                        intentType = IntentType.CHAT
                    )
                }
                repository.saveMessages(getApplication(), _uiState.value.messages)
            },
            onFailure = { e ->
                _uiState.update { state ->
                    val updatedMessages = state.messages.toMutableList().apply {
                        removeAt(size - 1)
                        add(Message(content = "错误: ${e.message}", isUser = false))
                    }
                    state.copy(
                        messages = updatedMessages,
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        )
    }

    /**
     * HTML_GENERATE 分支：AI 生成 HTML → 设置 htmlContent 供 Fragment 导航到 WebView
     */
    private suspend fun handleHtmlGenerate(userInput: String) {
        // HTML 生成不需要历史对话上下文，每次独立生成
        val result = repository.sendHtmlGenerateRequest(userInput, emptyList())

        result.fold(
            onSuccess = { htmlCode ->
                val cleanHtml = extractHtml(htmlCode)

                _uiState.update { state ->
                    val updatedMessages = state.messages.toMutableList().apply {
                        removeAt(size - 1)
                        add(Message(content = "已生成页面，正在打开...", isUser = false))
                    }
                    state.copy(
                        messages = updatedMessages,
                        isLoading = false,
                        intentType = IntentType.HTML_GENERATE,
                        htmlContent = cleanHtml
                    )
                }
                repository.saveMessages(getApplication(), _uiState.value.messages)
            },
            onFailure = { e ->
                _uiState.update { state ->
                    val updatedMessages = state.messages.toMutableList().apply {
                        removeAt(size - 1)
                        add(Message(content = "页面生成失败: ${e.message}", isUser = false))
                    }
                    state.copy(
                        messages = updatedMessages,
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        )
    }

    /**
     * VLM_VISION 分支：图片 → Base64 → 多模态 API → 返回识别结果
     */
    private suspend fun handleVlmVision(userInput: String) {
        val bitmap = pendingImageBitmap
        if (bitmap == null) {
            _uiState.update { state ->
                val updated = state.messages.toMutableList().apply {
                    removeAt(size - 1)
                    add(Message(content = "请先选择一张图片", isUser = false))
                }
                state.copy(messages = updated, isLoading = false)
            }
            return
        }

        val prompt = userInput.ifBlank { "请描述这张图片" }

        val imageBase64 = withContext(Dispatchers.IO) {
            ImageHelper.bitmapToBase64(bitmap)
        }

        val result = repository.sendVlmRequest(imageBase64, prompt)

        // 无论成功还是失败都清除待处理图片
        pendingImageBitmap = null

        result.fold(
            onSuccess = { aiResponse ->
                historyList.add("user" to prompt)
                historyList.add("assistant" to aiResponse)

                _uiState.update { state ->
                    val updated = state.messages.toMutableList().apply {
                        removeAt(size - 1)
                        add(Message(content = aiResponse, isUser = false))
                    }
                    state.copy(
                        messages = updated,
                        isLoading = false,
                        intentType = IntentType.VLM_VISION,
                        hasPendingImage = false
                    )
                }
                repository.saveMessages(getApplication(), _uiState.value.messages)
            },
            onFailure = { e ->
                _uiState.update { state ->
                    val updated = state.messages.toMutableList().apply {
                        removeAt(size - 1)
                        add(Message(content = "图片识别失败: ${e.message}", isUser = false))
                    }
                    state.copy(
                        messages = updated,
                        isLoading = false,
                        error = e.message,
                        hasPendingImage = false
                    )
                }
            }
        )
    }

    /**
     * Fragment 导航到 WebView 后调用，清除 intentType 避免重复导航
     */
    fun onHtmlConsumed() {
        _uiState.update { it.copy(intentType = null, htmlContent = null) }
    }

    /**
     * 清空所有对话（UI + 持久化存储 + 历史上下文）
     */
    fun clearConversation() {
        historyList.clear()
        _uiState.update { it.copy(messages = emptyList()) }
        viewModelScope.launch {
            repository.clearMessages(getApplication())
        }
    }

    /**
     * 第一层：本地关键词匹配进行意图分类
     * @return IntentType.HTML_GENERATE 命中关键词，null 表示未匹配（需 AI 兜底）
     */
    private fun classifyIntent(userInput: String): IntentType? {
        val htmlKeywords = listOf(
            "生成", "创建", "做一个", "做个", "写一个", "写个",
            "html", "HTML", "页面", "网页", "网站",
            "app", "APP", "应用",
            "计算器", "时钟", "日历", "游戏", "工具",
            "图片", "图像", "画", "绘制",
            "代码", "前端"
        )
        val vlmKeywords = listOf(
            "识别图片", "识别图像", "识别这张", "图片里", "图中",
            "看图", "这是什么图", "照片里", "图片是什么"
        )
        return when {
            htmlKeywords.any { userInput.contains(it) } -> IntentType.HTML_GENERATE
            vlmKeywords.any { userInput.contains(it) } -> IntentType.VLM_VISION
            else -> null
        }
    }

    /**
     * 第二层：AI 兜底意图分类
     * 当本地关键词匹配失败时调用，让 DeepSeek 判断用户意图
     * 如果 AI 调用也失败，默认走 CHAT（安全兜底）
     */
    private suspend fun classifyIntentByAi(userInput: String): IntentType {
        val result = repository.classifyIntent(userInput)
        return result.fold(
            onSuccess = { aiOutput ->
                when {
                    aiOutput.contains("HTML_GENERATE") -> IntentType.HTML_GENERATE
                    aiOutput.contains("VLM_VISION") -> IntentType.VLM_VISION
                    else -> IntentType.CHAT
                }
            },
            onFailure = {
                IntentType.CHAT
            }
        )
    }

    /**
     * 从 AI 回复中提取纯 HTML 代码
     * DeepSeek 可能会用 ```html ... ``` 包裹，需要去掉
     */
    private fun extractHtml(raw: String): String {
        val codeBlockRegex = Regex("```html\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val match = codeBlockRegex.find(raw)
        return if (match != null) {
            match.groupValues[1].trim()
        } else if (raw.trimStart().startsWith("<!DOCTYPE", ignoreCase = true) ||
                   raw.trimStart().startsWith("<html", ignoreCase = true)) {
            raw.trim()
        } else {
            raw
        }
    }
}
