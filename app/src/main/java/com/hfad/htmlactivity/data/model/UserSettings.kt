package com.hfad.htmlactivity.data.model

import com.google.gson.annotations.SerializedName

/**
 * 用户模型配置：base_url + model + api_key，每个用户一条
 */
data class UserSettings(
    @SerializedName("id") val id: String = "",
    @SerializedName("user") val userId: String = "",
    @SerializedName("base_url") val baseUrl: String = "",
    @SerializedName("model") val model: String = "",
    @SerializedName("api_key") val apiKey: String = ""
)
