package com.hfad.htmlactivity

/**
 * 意图枚举类
 * 限定整个系统只能识别这两种意图，方便后续做 if-else 的分支路由
 */
enum class IntentType(val label: String) {
    HTML_GENERATE("生成页面"), // 分支一：需要调 AI 生成 HTML 代码并用 WebView 展示
    CHAT("纯聊天")            // 分支二：普通的文本对话，只需要返回一句话
}