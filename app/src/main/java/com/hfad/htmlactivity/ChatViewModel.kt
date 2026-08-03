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
 */
class ChatViewModel : ViewModel() {

    private val repository = ChatRepository()

    // 聊天历史记录，用于多轮对话上下文
    private val historyList = mutableListOf<Pair<String, String>>()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /**
     * 发送用户消息并获取 AI 回复
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
            val result = repository.sendMessage(userInput, historyList.toList())

            result.fold(
                onSuccess = { aiResponse ->
                    // 记录历史
                    historyList.add("user" to userInput)
                    historyList.add("assistant" to aiResponse)

                    _uiState.update { state ->
                        val updatedMessages = state.messages.toMutableList().apply {
                            removeAt(size - 1) // 移除加载占位
                            add(Message(content = aiResponse, isUser = false))
                        }
                        state.copy(
                            messages = updatedMessages,
                            isLoading = false
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { state ->
                        val updatedMessages = state.messages.toMutableList().apply {
                            removeAt(size - 1) // 移除加载占位
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
    }
}
