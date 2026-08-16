package com.hfad.htmlactivity.data.model

import com.google.gson.annotations.SerializedName

/**
 * PocketBase 认证相关 DTO
 */
data class PocketBaseAuthResponse(
    @SerializedName("token") val token: String = "",
    @SerializedName("record") val record: PocketBaseUser? = null
)

data class PocketBaseUser(
    @SerializedName("id") val id: String = "",
    @SerializedName("email") val email: String = ""
)
