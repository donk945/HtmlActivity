package com.hfad.htmlactivity

data class Message(
    val id: Long = ++counter,
    val content: String,
    val isUser: Boolean  // true=用户消息，false=AI消息
) {
    companion object {
        private var counter = 0L
    }
}