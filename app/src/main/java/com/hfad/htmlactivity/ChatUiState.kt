package com.hfad.htmlactivity

/**
 * 聊天页面的 UI 状态，由 ViewModel 作为单一数据源暴露给 Fragment
 */
data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val intentType: IntentType? = null,    // 当前消息的意图类型
    val htmlContent: String? = null,       // HTML_GENERATE 时携带的 HTML 代码
    val hasPendingImage: Boolean = false   // VLM_VISION 时有待发送的图片
)
