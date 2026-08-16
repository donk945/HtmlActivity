package com.hfad.htmlactivity.data.model

import com.google.gson.annotations.SerializedName

/**
 * 聊天消息：既是 UI 领域模型，也直接映射 PocketBase 记录
 * id 使用服务端生成的记录 id，修复旧版静态计数器在进程重启后归零导致的 DiffUtil 判同问题
 */
data class Message(
    @SerializedName("id") val id: String = "",
    @SerializedName("conversation") val conversationId: String = "",
    @SerializedName("user") val userId: String = "",
    @SerializedName("content") val content: String = "",
    @SerializedName("is_user") val isUser: Boolean = false,
    @SerializedName("created") val created: String = ""
)
