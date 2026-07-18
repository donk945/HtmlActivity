package com.hfad.htmlactivity

data class Message(
    val content: String,
    val isUser: Boolean  // true=用户消息，false=AI消息
)