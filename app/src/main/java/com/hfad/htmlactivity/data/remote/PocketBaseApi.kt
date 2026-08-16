package com.hfad.htmlactivity.data.remote

import com.hfad.htmlactivity.data.model.Conversation
import com.hfad.htmlactivity.data.model.Message
import com.hfad.htmlactivity.data.model.PocketBaseAuthResponse
import com.hfad.htmlactivity.data.model.PocketBaseUser
import com.hfad.htmlactivity.data.model.UserSettings
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * PocketBase REST 接口（Retrofit）
 * 认证相关用 Map body 保持灵活，业务记录读写用强类型模型
 */
interface PocketBaseApi {

    // ---- 认证 ----

    @POST("api/collections/users/auth-with-password")
    suspend fun authWithPassword(@Body body: Map<String, String>): PocketBaseAuthResponse

    @POST("api/collections/users/records")
    suspend fun register(@Body body: Map<String, String>): PocketBaseUser

    @POST("api/collections/users/refresh")
    suspend fun refreshToken(@Body body: Map<String, String>): PocketBaseAuthResponse

    // ---- 模型配置 user_settings ----

    @GET("api/collections/user_settings/records")
    suspend fun getSettings(@Query("filter") filter: String): RecordListResponse<UserSettings>

    @POST("api/collections/user_settings/records")
    suspend fun createSettings(@Body body: UserSettings): UserSettings

    @PATCH("api/collections/user_settings/records/{id}")
    suspend fun updateSettings(
        @Path("id") id: String,
        @Body body: Map<String, String>
    ): UserSettings

    // ---- 会话 conversations ----

    @GET("api/collections/conversations/records")
    suspend fun getConversations(
        @Query("filter") filter: String,
        @Query("sort") sort: String
    ): RecordListResponse<Conversation>

    @POST("api/collections/conversations/records")
    suspend fun createConversation(@Body body: Conversation): Conversation

    @PATCH("api/collections/conversations/records/{id}")
    suspend fun updateConversation(
        @Path("id") id: String,
        @Body body: Map<String, String>
    ): Conversation

    @DELETE("api/collections/conversations/records/{id}")
    suspend fun deleteConversation(@Path("id") id: String)

    // ---- 消息 messages ----

    @GET("api/collections/messages/records")
    suspend fun getMessages(
        @Query("filter") filter: String,
        @Query("sort") sort: String
    ): RecordListResponse<Message>

    @POST("api/collections/messages/records")
    suspend fun createMessage(@Body body: Message): Message

    @DELETE("api/collections/messages/records/{id}")
    suspend fun deleteMessage(@Path("id") id: String)
}
