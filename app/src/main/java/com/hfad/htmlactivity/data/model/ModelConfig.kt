package com.hfad.htmlactivity.data.model

/**
 * 用户自选的模型配置：OpenAI 兼容接口的三个要素
 */
data class ModelConfig(
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = ""
)
