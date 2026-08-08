package com.hfad.htmlactivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 聊天页面的 ViewModel
 * 持有 UI 状态（StateFlow<ChatUiState>），管理聊天历史，调用 Repository
 * 新增意图识别：先本地关键词匹配分类，再路由到 CHAT 或 HTML_GENERATE 分支
 */
class ChatViewModel : ViewModel() {

    private val repository = ChatRepository()

    // 聊天历史记录，用于多轮对话上下文
    private val historyList = mutableListOf<Pair<String, String>>()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /**
     * 发送用户消息，先做意图分类再路由到不同分支
     * 分类策略：本地关键词匹配 → 未命中则 AI 兜底分类 → 仍失败则默认 CHAT
     */
    fun sendMessage(userInput: String) {
        // 追加用户消息 + 加载占位消息
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
            // 第一层：本地关键词匹配
            val keywordIntent = classifyIntent(userInput)
            val intent = if (keywordIntent != null) {
                keywordIntent
            } else {
                // 第二层：AI 兜底分类
                classifyIntentByAi(userInput)
            }

            when (intent) {
                IntentType.CHAT -> handleChat(userInput)
                IntentType.HTML_GENERATE -> handleHtmlGenerate(userInput)
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
                // 提取 HTML 代码（去除可能的 markdown 包裹）
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
     * Fragment 导航到 WebView 后调用，清除 intentType 避免重复导航
     */
    fun onHtmlConsumed() {
        _uiState.update { it.copy(intentType = null, htmlContent = null) }
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
        return if (htmlKeywords.any { userInput.contains(it) }) {
            IntentType.HTML_GENERATE
        } else {
            null  // 本地无法判断，交给 AI 兜底
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
                    else -> IntentType.CHAT
                }
            },
            onFailure = {
                // AI 分类失败，默认走聊天
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
