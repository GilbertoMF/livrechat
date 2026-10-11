package com.livrechat.app.data.remote

import com.livrechat.app.data.remote.model.AddContactRequest
import com.livrechat.app.data.remote.model.BaseResponse
import com.livrechat.app.data.remote.model.CallHistoryDto
import com.livrechat.app.data.remote.model.ContactDto
import com.livrechat.app.data.remote.model.ConversationDto
import com.livrechat.app.data.remote.model.IceServersResponse
import com.livrechat.app.data.remote.model.LoginRequest
import com.livrechat.app.data.remote.model.LoginResponse
import com.livrechat.app.data.remote.model.MessageDto
import com.livrechat.app.data.remote.model.RecoverRequest
import com.livrechat.app.data.remote.model.RegisterRequest
import com.livrechat.app.data.remote.model.RegisterResponse
import com.livrechat.app.data.remote.model.SendMessageRequest
import com.livrechat.app.data.remote.model.StatusDto
import com.livrechat.app.data.remote.model.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/auth/recover")
    suspend fun recover(@Body request: RecoverRequest): Response<BaseResponse>

    @GET("api/messages/conversations")
    suspend fun getConversations(): Response<List<ConversationDto>>

    @POST("api/messages/conversations/direct")
    suspend fun getOrCreateConversation(@Body body: Map<String, String>): Response<ConversationDto>

    @GET("api/messages/{conversationId}")
    suspend fun getMessages(
        @Path("conversationId") conversationId: String,
        @Query("limit") limit: Int = 50,
        @Query("before") before: String? = null
    ): Response<List<MessageDto>>

    @POST("api/messages")
    suspend fun sendMessage(@Body request: SendMessageRequest): Response<MessageDto>

    @PUT("api/messages/{messageId}")
    suspend fun editMessage(
        @Path("messageId") messageId: String,
        @Body body: Map<String, String>
    ): Response<MessageDto>

    @DELETE("api/messages/{messageId}")
    suspend fun deleteMessage(@Path("messageId") messageId: String): Response<BaseResponse>

    @POST("api/messages/{messageId}/read")
    suspend fun markMessageRead(@Path("messageId") messageId: String): Response<BaseResponse>

    @GET("api/users/profile/{userId}")
    suspend fun getUserProfile(@Path("userId") userId: String): Response<UserDto>

    @PUT("api/users/profile")
    suspend fun updateProfile(@Body body: Map<String, String>): Response<UserDto>

    @GET("api/users/blocked")
    suspend fun getBlockedUsers(): Response<List<UserDto>>

    @DELETE("api/users/unblock/{blockedUserId}")
    suspend fun unblockUser(@Path("blockedUserId") blockedUserId: String): Response<BaseResponse>

    @GET("api/users/search")
    suspend fun searchUsers(@Query("q") query: String): Response<List<UserDto>>

    @GET("api/users/contacts")
    suspend fun getContacts(): Response<List<ContactDto>>

    @POST("api/users/contacts")
    suspend fun addContact(@Body request: AddContactRequest): Response<ContactDto>

    @GET("api/calls/ice-servers")
    suspend fun getIceServers(): Response<IceServersResponse>

    @POST("api/messages/conversations/{conversationId}/read")
    suspend fun markConversationRead(@Path("conversationId") conversationId: String): Response<BaseResponse>

    @DELETE("api/messages/conversations/{conversationId}/clear")
    suspend fun clearConversation(@Path("conversationId") conversationId: String): Response<BaseResponse>

    @DELETE("api/calls/history")
    suspend fun clearCallHistory(): Response<BaseResponse>

    @DELETE("api/users/contacts/{contactUserId}")
    suspend fun removeContact(@Path("contactUserId") contactUserId: String): Response<BaseResponse>

    @POST("api/users/block")
    suspend fun blockUser(@Body body: Map<String, String>): Response<BaseResponse>

    @GET("api/calls/history")
    suspend fun getCallHistory(): Response<List<CallHistoryDto>>

    @GET("api/status")
    suspend fun getStatuses(): Response<List<StatusDto>>

    @POST("api/status")
    suspend fun createStatus(@Body body: Map<String, String>): Response<StatusDto>

    @DELETE("api/status/{statusId}")
    suspend fun deleteStatus(@Path("statusId") statusId: String): Response<BaseResponse>
}

