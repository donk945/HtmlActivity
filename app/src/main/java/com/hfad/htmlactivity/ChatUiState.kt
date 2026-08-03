package com.hfad.htmlactivity

/**
 * 聊天页面的 UI 状态，由 ViewModel 作为单一数据源暴露给 Fragment
 */
data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
