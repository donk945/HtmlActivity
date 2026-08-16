package com.hfad.htmlactivity.data.remote

import com.google.gson.annotations.SerializedName

/**
 * PocketBase 记录列表的通用响应包装
 */
data class RecordListResponse<T>(
    @SerializedName("page") val page: Int = 0,
    @SerializedName("perPage") val perPage: Int = 0,
    @SerializedName("totalItems") val totalItems: Int = 0,
    @SerializedName("totalPages") val totalPages: Int = 0,
    @SerializedName("items") val items: List<T> = emptyList()
)
