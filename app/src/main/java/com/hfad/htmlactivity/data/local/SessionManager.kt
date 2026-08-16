package com.hfad.htmlactivity.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * 登录态管理：token + 用户信息持久化到 DataStore
 * currentToken 等为内存镜像，供 OkHttp 拦截器同步读取
 */
class SessionManager(private val context: Context) {

    @Volatile
    var currentToken: String? = null
        private set

    @Volatile
    var currentUserId: String? = null
        private set

    @Volatile
    var currentEmail: String? = null
        private set

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
    }

    /** 进程启动时从 DataStore 恢复内存镜像 */
    suspend fun restore() {
        val prefs = context.sessionDataStore.data.first()
        currentToken = prefs[Keys.TOKEN]
        currentUserId = prefs[Keys.USER_ID]
        currentEmail = prefs[Keys.EMAIL]
    }

    suspend fun saveSession(token: String, userId: String, email: String) {
        currentToken = token
        currentUserId = userId
        currentEmail = email
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.TOKEN] = token
            prefs[Keys.USER_ID] = userId
            prefs[Keys.EMAIL] = email
        }
    }

    suspend fun isLoggedIn(): Boolean = currentToken != null

    suspend fun clear() {
        currentToken = null
        currentUserId = null
        currentEmail = null
        context.sessionDataStore.edit { it.clear() }
    }
}
