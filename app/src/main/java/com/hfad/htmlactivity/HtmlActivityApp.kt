package com.hfad.htmlactivity

import android.app.Application
import com.hfad.htmlactivity.data.local.ChatCache
import com.hfad.htmlactivity.data.local.SessionManager
import com.hfad.htmlactivity.data.remote.PocketBaseClient
import com.hfad.htmlactivity.data.repository.AuthRepository
import com.hfad.htmlactivity.data.repository.ChatRepository
import com.hfad.htmlactivity.data.repository.ConversationRepository
import com.hfad.htmlactivity.data.repository.MessageRepository
import com.hfad.htmlactivity.data.repository.SettingsRepository

/**
 * Application 聚合所有单例（无 DI 框架，手动依赖提供）
 */
class HtmlActivityApp : Application() {

    val sessionManager by lazy { SessionManager(this) }

    val pocketBaseApi by lazy { PocketBaseClient.create { sessionManager.currentToken } }

    val authRepository by lazy { AuthRepository(pocketBaseApi, sessionManager) }
    val settingsRepository by lazy { SettingsRepository(pocketBaseApi, sessionManager) }
    val conversationRepository by lazy { ConversationRepository(pocketBaseApi, sessionManager) }
    val messageRepository by lazy { MessageRepository(pocketBaseApi, sessionManager) }
    val chatRepository by lazy { ChatRepository() }

    val chatCache by lazy { ChatCache(this) }
}
