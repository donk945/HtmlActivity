package com.hfad.htmlactivity.ui.chat

import com.hfad.htmlactivity.IntentType
import com.hfad.htmlactivity.data.model.Conversation
import com.hfad.htmlactivity.data.model.Message

/**
 * 聊天页面的 UI 状态
 */
data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val currentConversationId: String? = null,
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val intentType: IntentType? = null,
    val htmlContent: String? = null,
    val hasPendingImage: Boolean = false,
    val hasSettings: Boolean = false   // 是否已配置模型
)
