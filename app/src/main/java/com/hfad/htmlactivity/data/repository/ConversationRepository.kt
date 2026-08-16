package com.hfad.htmlactivity.data.repository

import android.util.Log
import com.hfad.htmlactivity.data.local.SessionManager
import com.hfad.htmlactivity.data.model.Conversation
import com.hfad.htmlactivity.data.remote.PocketBaseApi
import retrofit2.HttpException

/**
 * 会话仓库：云端 conversations 记录 CRUD
 */
class ConversationRepository(
    private val api: PocketBaseApi,
    private val sessionManager: SessionManager
) {

    suspend fun getConversations(): Result<List<Conversation>> = try {
        val userId = sessionManager.currentUserId ?: throw Exception("未登录")
        Result.success(api.getConversations("user=\"$userId\"", "-created").items)
    } catch (e: Exception) {
        Log.w(TAG, "获取会话列表失败: ${e.message}", e)
        Result.failure(e)
    }

    suspend fun createConversation(title: String): Result<Conversation> = try {
        val userId = sessionManager.currentUserId ?: throw Exception("未登录")
        Result.success(api.createConversation(Conversation(userId = userId, title = title)))
    } catch (e: Exception) {
        Log.w(TAG, "创建会话失败: ${e.message}", e)
        Result.failure(e)
    }

    suspend fun updateTitle(id: String, title: String): Result<Conversation> = try {
        Result.success(api.updateConversation(id, mapOf("title" to title)))
    } catch (e: Exception) {
        Log.w(TAG, "更新会话标题失败: ${e.message}", e)
        Result.failure(e)
    }

    suspend fun deleteConversation(id: String): Result<Unit> = try {
        api.deleteConversation(id)
        Result.success(Unit)
    } catch (e: HttpException) {
        // 404 表示云端记录已不存在，同样视为删除成功
        if (e.code() == 404) {
            Result.success(Unit)
        } else {
            Log.w(TAG, "删除会话失败: HTTP ${e.code()} ${e.message}", e)
            Result.failure(e)
        }
    } catch (e: Exception) {
        Log.w(TAG, "删除会话失败: ${e.message}", e)
        Result.failure(e)
    }

    companion object {
        private const val TAG = "ConversationRepository"
    }
}
