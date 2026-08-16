package com.hfad.htmlactivity.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hfad.htmlactivity.data.model.Conversation
import com.hfad.htmlactivity.data.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地缓存结构：会话列表 + 各会话的消息（写穿缓存，云端为准）
 */
data class ChatCacheData(
    val conversations: List<Conversation> = emptyList(),
    val messages: Map<String, List<Message>> = emptyMap()
)

/**
 * 本地 JSON 缓存：离线只读 + 写穿时同步更新
 */
class ChatCache(private val context: Context) {

    private val gson = Gson()

    private fun file(): File = File(context.filesDir, "chat_cache.json")

    suspend fun load(): ChatCacheData = withContext(Dispatchers.IO) {
        try {
            val f = file()
            if (!f.exists()) return@withContext ChatCacheData()
            val type = object : TypeToken<ChatCacheData>() {}.type
            gson.fromJson(f.readText(), type) ?: ChatCacheData()
        } catch (e: Exception) {
            ChatCacheData()
        }
    }

    suspend fun save(data: ChatCacheData) = withContext(Dispatchers.IO) {
        try {
            file().writeText(gson.toJson(data))
        } catch (_: Exception) {
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            file().delete()
        } catch (_: Exception) {
        }
    }
}
