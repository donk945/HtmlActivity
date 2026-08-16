package com.hfad.htmlactivity.data.model

import com.google.gson.annotations.SerializedName

/**
 * 会话：一条对话的容器，用户可新建/切换/删除
 */
data class Conversation(
    @SerializedName("id") val id: String = "",
    @SerializedName("user") val userId: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("created") val created: String = "",
    @SerializedName("updated") val updated: String = ""
)
