package com.hfad.htmlactivity.data.repository

import com.hfad.htmlactivity.data.local.SessionManager
import com.hfad.htmlactivity.data.remote.PocketBaseApi

/**
 * 认证仓库：登录 / 注册 / 登出
 */
class AuthRepository(
    private val api: PocketBaseApi,
    private val sessionManager: SessionManager
) {

    suspend fun login(email: String, password: String): Result<String> = try {
        val resp = api.authWithPassword(
            mapOf("identity" to email, "password" to password)
        )
        val user = resp.record ?: throw Exception("登录响应缺少用户信息")
        sessionManager.saveSession(resp.token, user.id, user.email)
        Result.success(user.id)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun register(email: String, password: String, confirm: String): Result<String> = try {
        api.register(
            mapOf(
                "email" to email,
                "password" to password,
                "passwordConfirm" to confirm
            )
        )
        Result.success(email)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun logout() {
        sessionManager.clear()
    }
}
