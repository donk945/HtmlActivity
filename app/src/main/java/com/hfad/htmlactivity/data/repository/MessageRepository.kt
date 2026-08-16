package com.hfad.htmlactivity.data.repository

import com.hfad.htmlactivity.data.local.SessionManager
import com.hfad.htmlactivity.data.model.Message
import com.hfad.htmlactivity.data.remote.PocketBaseApi

/**
 * 消息仓库：云端 messages 记录 CRUD
 */
class MessageRepository(
    private val api: PocketBaseApi,
    private val sessionManager: SessionManager
) {

    suspend fun getMessages(conversationId: String): Result<List<Message>> = try {
        Result.success(
            api.getMessages("conversation=\"$conversationId\"", "created").items
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun createMessage(
        conversationId: String,
        content: String,
        isUser: Boolean
    ): Result<Message> = try {
        val userId = sessionManager.currentUserId ?: throw Exception("未登录")
        Result.success(
            api.createMessage(
                Message(
                    conversationId = conversationId,
                    userId = userId,
                    content = content,
                    isUser = isUser
                )
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteMessage(id: String): Result<Unit> = try {
        api.deleteMessage(id)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
