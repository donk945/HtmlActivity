package com.hfad.htmlactivity.data.repository

import com.hfad.htmlactivity.data.local.SessionManager
import com.hfad.htmlactivity.data.model.UserSettings
import com.hfad.htmlactivity.data.remote.PocketBaseApi

/**
 * 模型配置仓库：读写当前用户的 user_settings 记录
 */
class SettingsRepository(
    private val api: PocketBaseApi,
    private val sessionManager: SessionManager
) {

    suspend fun getSettings(): Result<UserSettings?> {
        return try {
            val userId = sessionManager.currentUserId ?: return Result.success(null)
            val resp = api.getSettings("user=\"$userId\"")
            Result.success(resp.items.firstOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveSettings(
        baseUrl: String,
        model: String,
        apiKey: String
    ): Result<UserSettings> = try {
        val userId = sessionManager.currentUserId ?: throw Exception("未登录")
        val existing = getSettings().getOrNull()
        if (existing != null) {
            Result.success(
                api.updateSettings(
                    existing.id,
                    mapOf(
                        "base_url" to baseUrl,
                        "model" to model,
                        "api_key" to apiKey
                    )
                )
            )
        } else {
            Result.success(
                api.createSettings(
                    UserSettings(
                        userId = userId,
                        baseUrl = baseUrl,
                        model = model,
                        apiKey = apiKey
                    )
                )
            )
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
