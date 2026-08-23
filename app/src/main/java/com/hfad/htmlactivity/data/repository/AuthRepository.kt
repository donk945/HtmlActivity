package com.hfad.htmlactivity.data.repository

import android.util.Log
import com.hfad.htmlactivity.data.local.SessionManager
import com.hfad.htmlactivity.data.remote.PocketBaseApi
import org.json.JSONObject
import retrofit2.HttpException

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

    /** 网络连通检测：请求 PocketBase 健康检查接口 */
    suspend fun checkServer(): Result<Unit> = try {
        api.healthCheck()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "PocketBase 服务连通检测失败: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * 注册：向 PocketBase users 集合创建一条用户记录
     *
     * @param email    邮箱     —— PB 系统字段，【强制必填】
     * @param password 密码     —— PB Auth 内置字段，【强制必填】
     * @param confirm  确认密码 —— 注册时校验用，【必填】（须与 password 一致）
     * @param name     昵称     —— 自定义字段，【可选】（为空则不提交）
     */
    suspend fun register(
        email: String,
        password: String,
        confirm: String,
        name: String? = null
    ): Result<String> = try {
        val body = mutableMapOf<String, String>(
            "email" to email,              // 系统字段，必填
            "password" to password,        // Auth 内置，必填
            "passwordConfirm" to confirm   // 注册校验，必填
        )
        // 自定义可选字段：昵称，非空才提交
        if (!name.isNullOrBlank()) body["name"] = name
        // avatar 是单文件图片字段，JSON 注册接口不处理，注册后单独上传（可选）

        // 打印完整发送参数
        Log.d(TAG, "注册请求参数: $body")

        api.register(body)
        Result.success(email)
    } catch (e: HttpException) {
        val raw = try {
            e.response()?.errorBody()?.string()
        } catch (ex: Exception) {
            null
        }
        // 打印后端返回的完整错误对象
        Log.e(TAG, "注册失败，后端原始错误: $raw")
        Result.failure(parseRegisterError(raw, e))
    } catch (e: Exception) {
        Log.e(TAG, "注册失败: ${e.message}", e)
        Result.failure(e)
    }

    suspend fun logout() {
        sessionManager.clear()
    }

    /** 解析 PocketBase 返回的具体字段错误 code，映射成中文提示 */
    private fun parseRegisterError(rawBody: String?, e: HttpException): Exception {
        val code = try {
            val data = JSONObject(rawBody ?: "").optJSONObject("data")
            data?.keys()?.asSequence()?.firstOrNull()?.let { key ->
                data.optJSONObject(key)?.optString("code")
            }
        } catch (ex: Exception) {
            null
        }

        val message = when (code) {
            "validation_invalid_email" -> "邮箱格式不正确"
            "validation_length_out_of_range" -> "密码长度需在 8~72 位之间"
            "validation_not_unique" -> "该邮箱或用户名已被注册"
            "validation_required" -> "存在未填写的必填字段"
            "validation_invalid_username" -> "用户名格式不正确（仅限字母、数字、下划线）"
            else -> "注册失败：${rawBody ?: e.message()}"
        }
        return Exception(message)
    }

    companion object {
        private const val TAG = "AuthRepository"
    }
}
