package com.hfad.htmlactivity.ui.chat

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hfad.htmlactivity.HtmlActivityApp
import com.hfad.htmlactivity.ImageHelper
import com.hfad.htmlactivity.IntentType
import com.hfad.htmlactivity.data.local.ChatCacheData
import com.hfad.htmlactivity.data.model.Message
import com.hfad.htmlactivity.data.model.ModelConfig
import com.hfad.htmlactivity.data.repository.ChatRepository
import com.hfad.htmlactivity.data.repository.ConversationRepository
import com.hfad.htmlactivity.data.repository.MessageRepository
import com.hfad.htmlactivity.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as HtmlActivityApp
    private val conversationRepository: ConversationRepository = app.conversationRepository
    private val messageRepository: MessageRepository = app.messageRepository
    private val settingsRepository: SettingsRepository = app.settingsRepository
    private val chatRepository: ChatRepository = app.chatRepository
    private val chatCache = app.chatCache

    // 当前模型配置（内存缓存）
    private var modelConfig: ModelConfig? = null

    // 当前会话的多轮上下文
    private val historyList = mutableListOf<Pair<String, String>>()

    // VLM 分支：待识别图片
    private var pendingImageBitmap: Bitmap? = null

    // UI 临时 id 计数器（乐观显示用，非云端 id）
    private var localIdCounter = 0L

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadSettings()
            loadConversations()
        }
    }

    // ---- 加载 ----

    /** 从设置页返回后刷新模型配置 */
    fun refreshSettings() {
        viewModelScope.launch { loadSettings() }
    }

    private suspend fun loadSettings() {
        settingsRepository.getSettings().fold(
            onSuccess = { s ->
                if (s != null && s.baseUrl.isNotBlank() && s.model.isNotBlank() && s.apiKey.isNotBlank()) {
                    modelConfig = ModelConfig(s.baseUrl, s.model, s.apiKey)
                }
                _uiState.update { it.copy(hasSettings = modelConfig != null) }
            },
            onFailure = { }
        )
    }

    private suspend fun loadConversations() {
        conversationRepository.getConversations().fold(
            onSuccess = { list ->
                _uiState.update { it.copy(conversations = list) }
                if (list.isNotEmpty()) {
                    selectConversation(list.first().id)
                } else {
                    createNewConversation()
                }
            },
            onFailure = { e ->
                // 离线降级：从缓存读会话列表
                val cached = chatCache.load().conversations
                _uiState.update { it.copy(conversations = cached, error = e.message) }
                if (cached.isNotEmpty()) {
                    selectConversation(cached.first().id)
                }
            }
        )
    }

    // ---- 会话操作 ----

    fun selectConversation(id: String) {
        if (_uiState.value.currentConversationId == id) return
        historyList.clear()
        _uiState.update {
            it.copy(currentConversationId = id, messages = emptyList(), isLoading = true)
        }
        viewModelScope.launch { loadMessages(id) }
    }

    private suspend fun loadMessages(conversationId: String) {
        messageRepository.getMessages(conversationId).fold(
            onSuccess = { msgs ->
                _uiState.update { it.copy(messages = msgs, isLoading = false, error = null) }
                rebuildHistory(msgs)
                syncCache()
            },
            onFailure = { e ->
                val cached = chatCache.load().messages[conversationId]
                if (cached != null) {
                    _uiState.update { it.copy(messages = cached, isLoading = false) }
                    rebuildHistory(cached)
                } else {
                    _uiState.update {
                        it.copy(messages = emptyList(), isLoading = false, error = e.message)
                    }
                }
            }
        )
    }

    fun createNewConversation() {
        viewModelScope.launch {
            conversationRepository.createConversation("新对话").fold(
                onSuccess = { c ->
                    _uiState.update { it.copy(conversations = listOf(c) + it.conversations) }
                    selectConversation(c.id)
                },
                onFailure = { e -> _uiState.update { it.copy(error = e.message) } }
            )
        }
    }

    fun deleteConversation(id: String) {
        // 先乐观地从 UI 移除，保证点击删除立即有反馈（不等待网络）
        val remaining = _uiState.value.conversations.filter { it.id != id }
        _uiState.update { it.copy(conversations = remaining) }

        if (_uiState.value.currentConversationId == id) {
            historyList.clear()
            if (remaining.isNotEmpty()) {
                selectConversation(remaining.first().id)
            } else {
                _uiState.update {
                    it.copy(messages = emptyList(), currentConversationId = null)
                }
            }
        }

        viewModelScope.launch {
            conversationRepository.deleteConversation(id).fold(
                onSuccess = { syncCache() },
                onFailure = { e ->
                    // 云端删除失败也保留本地已移除的结果，仅提示错误
                    syncCache()
                    _uiState.update { it.copy(error = "删除失败：${e.message}") }
                }
            )
        }
    }

    // ---- VLM 图片 ----

    fun attachImage(bitmap: Bitmap) {
        pendingImageBitmap = bitmap
        _uiState.update { it.copy(hasPendingImage = true) }
    }

    fun clearPendingImage() {
        pendingImageBitmap = null
        _uiState.update { it.copy(hasPendingImage = false) }
    }

    // ---- 发送消息 ----

    fun sendMessage(userInput: String) {
        val conversationId = _uiState.value.currentConversationId
        if (conversationId == null) {
            _uiState.update { it.copy(error = "请先创建或选择一个会话") }
            return
        }
        val config = modelConfig
        if (config == null) {
            _uiState.update { it.copy(error = "请先在设置中配置模型") }
            return
        }

        val userMessage = Message(
            id = nextLocalId(),
            conversationId = conversationId,
            content = userInput,
            isUser = true
        )
        val loadingMessage = Message(
            id = nextLocalId(),
            conversationId = conversationId,
            content = "正在思考...",
            isUser = false
        )

        _uiState.update {
            it.copy(
                messages = it.messages + listOf(userMessage, loadingMessage),
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            // 写穿：用户消息先存云端（失败不阻断）
            runCatching { messageRepository.createMessage(conversationId, userInput, true) }

            val intent = if (pendingImageBitmap != null) {
                IntentType.VLM_VISION
            } else {
                val keywordIntent = classifyIntent(userInput)
                keywordIntent ?: classifyIntentByAi(config, userInput)
            }

            when (intent) {
                IntentType.CHAT -> handleChat(config, conversationId, userInput)
                IntentType.HTML_GENERATE -> handleHtmlGenerate(config, conversationId, userInput)
                IntentType.VLM_VISION -> handleVlmVision(config, conversationId, userInput)
            }
        }
    }

    private suspend fun handleChat(config: ModelConfig, conversationId: String, userInput: String) {
        chatRepository.sendChatRequest(config, userInput, historyList.toList()).fold(
            onSuccess = { aiResponse ->
                historyList.add("user" to userInput)
                historyList.add("assistant" to aiResponse)

                _uiState.update { state ->
                    val updated = state.messages.toMutableList().apply {
                        removeAt(size - 1)
                        add(Message(
                            id = nextLocalId(),
                            conversationId = conversationId,
                            content = aiResponse,
                            isUser = false
                        ))
                    }
                    state.copy(
                        messages = updated,
                        isLoading = false,
                        intentType = IntentType.CHAT
                    )
                }
                runCatching { messageRepository.createMessage(conversationId, aiResponse, false) }
                syncCache()
            },
            onFailure = { e -> onChatError(e, "错误: ${e.message}") }
        )
    }

    private suspend fun handleHtmlGenerate(config: ModelConfig, conversationId: String, userInput: String) {
        chatRepository.sendHtmlGenerateRequest(config, userInput, emptyList()).fold(
            onSuccess = { htmlCode ->
                val cleanHtml = extractHtml(htmlCode)
                _uiState.update { state ->
                    val updated = state.messages.toMutableList().apply {
                        removeAt(size - 1)
                        add(Message(
                            id = nextLocalId(),
                            conversationId = conversationId,
                            content = "已生成页面，正在打开...",
                            isUser = false
                        ))
                    }
                    state.copy(
                        messages = updated,
                        isLoading = false,
                        intentType = IntentType.HTML_GENERATE,
                        htmlContent = cleanHtml
                    )
                }
                runCatching { messageRepository.createMessage(conversationId, "已生成页面，正在打开...", false) }
                syncCache()
            },
            onFailure = { e -> onChatError(e, "页面生成失败: ${e.message}") }
        )
    }

    private suspend fun handleVlmVision(config: ModelConfig, conversationId: String, userInput: String) {
        val bitmap = pendingImageBitmap
        if (bitmap == null) {
            _uiState.update { state ->
                val updated = state.messages.toMutableList().apply {
                    removeAt(size - 1)
                    add(Message(
                        id = nextLocalId(),
                        conversationId = conversationId,
                        content = "请先选择一张图片",
                        isUser = false
                    ))
                }
                state.copy(messages = updated, isLoading = false)
            }
            return
        }

        val prompt = userInput.ifBlank { "请描述这张图片" }
        val imageBase64 = withContext(Dispatchers.IO) {
            ImageHelper.bitmapToBase64(bitmap)
        }

        pendingImageBitmap = null

        chatRepository.sendVlmRequest(config, imageBase64, prompt).fold(
            onSuccess = { aiResponse ->
                historyList.add("user" to prompt)
                historyList.add("assistant" to aiResponse)

                _uiState.update { state ->
                    val updated = state.messages.toMutableList().apply {
                        removeAt(size - 1)
                        add(Message(
                            id = nextLocalId(),
                            conversationId = conversationId,
                            content = aiResponse,
                            isUser = false
                        ))
                    }
                    state.copy(
                        messages = updated,
                        isLoading = false,
                        intentType = IntentType.VLM_VISION,
                        hasPendingImage = false
                    )
                }
                runCatching { messageRepository.createMessage(conversationId, aiResponse, false) }
                syncCache()
            },
            onFailure = { e ->
                _uiState.update { state ->
                    val updated = state.messages.toMutableList().apply {
                        removeAt(size - 1)
                        add(Message(
                            id = nextLocalId(),
                            conversationId = conversationId,
                            content = "图片识别失败：当前模型可能是纯文本模型，不支持图片识别（${e.message}）",
                            isUser = false
                        ))
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

    private fun onChatError(e: Throwable, display: String) {
        _uiState.update { state ->
            val updated = state.messages.toMutableList().apply {
                removeAt(size - 1)
                add(Message(id = nextLocalId(), content = display, isUser = false))
            }
            state.copy(messages = updated, isLoading = false, error = e.message)
        }
    }

    fun onHtmlConsumed() {
        _uiState.update { it.copy(intentType = null, htmlContent = null) }
    }

    // ---- 意图分类 ----

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

    private suspend fun classifyIntentByAi(config: ModelConfig, userInput: String): IntentType {
        return chatRepository.classifyIntent(config, userInput).fold(
            onSuccess = { aiOutput ->
                when {
                    aiOutput.contains("HTML_GENERATE") -> IntentType.HTML_GENERATE
                    aiOutput.contains("VLM_VISION") -> IntentType.VLM_VISION
                    else -> IntentType.CHAT
                }
            },
            onFailure = { IntentType.CHAT }
        )
    }

    private fun extractHtml(raw: String): String {
        val codeBlockRegex = Regex("```html\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val match = codeBlockRegex.find(raw)
        return if (match != null) {
            match.groupValues[1].trim()
        } else if (raw.trimStart().startsWith("<!DOCTYPE", ignoreCase = true) ||
            raw.trimStart().startsWith("<html", ignoreCase = true)
        ) {
            raw.trim()
        } else {
            raw
        }
    }

    // ---- 缓存与工具 ----

    private fun rebuildHistory(messages: List<Message>) {
        historyList.clear()
        messages.forEach { m ->
            if (m.isUser) historyList.add("user" to m.content)
            else historyList.add("assistant" to m.content)
        }
    }

    private suspend fun syncCache() {
        val state = _uiState.value
        val convId = state.currentConversationId
        val existing = chatCache.load()
        // 仅保留仍然存在的会话的消息，已删除会话的消息一并清理
        val validIds = state.conversations.map { it.id }.toSet()
        val messages = existing.messages
            .filterKeys { it in validIds }
            .let { pruned ->
                if (convId != null) pruned + (convId to state.messages) else pruned
            }
        chatCache.save(ChatCacheData(conversations = state.conversations, messages = messages))
    }

    private fun nextLocalId(): String = "local-${++localIdCounter}"
}
