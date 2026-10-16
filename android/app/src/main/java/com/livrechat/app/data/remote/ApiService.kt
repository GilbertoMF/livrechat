package com.livrechat.app.data.remote

import com.livrechat.app.data.remote.model.*
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

    @POST("api/auth/virtual-number/request")
    suspend fun requestVirtualNumberCode(@Body body: Map<String, String>): Response<com.livrechat.app.data.remote.model.VirtualNumberRequestResponse>

    @POST("api/auth/virtual-number/verify")
    suspend fun verifyVirtualNumberCode(@Body body: Map<String, String>): Response<LoginResponse>

    @GET("api/auth/sessions")
    suspend fun getSessions(): Response<List<SessionDto>>

    @DELETE("api/auth/sessions/{sessionId}")
    suspend fun revokeSession(@Path("sessionId") sessionId: String): Response<Map<String, Any>>

    @POST("api/auth/sessions/revoke-others")
    suspend fun revokeOtherSessions(): Response<Map<String, Any>>

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

    @POST("api/users/sync-contacts")
    suspend fun syncContacts(@Body request: SyncContactsRequest): Response<SyncContactsResponse>

    @GET("api/calls/ice-servers")
    suspend fun getIceServers(): Response<IceServersResponse>

    @POST("api/messages/conversations/{conversationId}/read")
    suspend fun markConversationRead(@Path("conversationId") conversationId: String): Response<BaseResponse>

    @DELETE("api/messages/conversations/{conversationId}/clear")
    suspend fun clearConversation(@Path("conversationId") conversationId: String): Response<BaseResponse>

    @DELETE("api/messages/conversations/{conversationId}")
    suspend fun deleteConversation(@Path("conversationId") conversationId: String): Response<BaseResponse>

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

    @POST("api/status/{statusId}/view")
    suspend fun recordStatusView(@Path("statusId") statusId: String): Response<BaseResponse>

    @POST("api/status/{statusId}/react")
    suspend fun reactToStatus(
        @Path("statusId") statusId: String,
        @Body body: Map<String, String>
    ): Response<BaseResponse>

    @PUT("api/users/contacts/{contactUserId}")
    suspend fun updateContact(
        @Path("contactUserId") contactUserId: String,
        @Body body: Map<String, String>
    ): Response<BaseResponse>

    @POST("api/messages/groups")
    suspend fun createGroup(
        @Body request: CreateGroupRequest
    ): Response<ConversationDto>

    @PUT("api/messages/groups/{conversationId}")
    suspend fun updateGroup(
        @Path("conversationId") conversationId: String,
        @Body body: Map<String, String>
    ): Response<BaseResponse>

    @GET("api/messages/conversations/{conversationId}/members")
    suspend fun getGroupMembers(
        @Path("conversationId") conversationId: String
    ): Response<List<GroupMemberDto>>

    @POST("api/messages/conversations/{conversationId}/members")
    suspend fun addGroupMembers(
        @Path("conversationId") conversationId: String,
        @Body request: AddMembersRequest
    ): Response<BaseResponse>

    @DELETE("api/messages/conversations/{conversationId}/members/{memberId}")
    suspend fun removeGroupMember(
        @Path("conversationId") conversationId: String,
        @Path("memberId") memberId: String
    ): Response<BaseResponse>

    @PUT("api/messages/groups/{conversationId}/admins/{userId}")
    suspend fun setGroupMemberAdmin(
        @Path("conversationId") conversationId: String,
        @Path("userId") userId: String,
        @Body body: Map<String, Boolean>
    ): Response<BaseResponse>

    @PUT("api/messages/groups/{conversationId}/permissions")
    suspend fun updateGroupPermissions(
        @Path("conversationId") conversationId: String,
        @Body body: Map<String, Boolean>
    ): Response<BaseResponse>

    @PUT("api/users/preferences")
    suspend fun updatePreferences(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<BaseResponse>

    @POST("api/users/fcm-token")
    suspend fun registerFcmToken(
        @Body body: Map<String, String>
    ): Response<BaseResponse>

    @retrofit2.http.HTTP(method = "DELETE", path = "api/users/fcm-token", hasBody = true)
    suspend fun removeFcmToken(
        @Body body: Map<String, String>
    ): Response<BaseResponse>

    @POST("api/messages/{messageId}/delivered")
    suspend fun markMessageDelivered(
        @Path("messageId") messageId: String
    ): Response<BaseResponse>

    @retrofit2.http.Multipart
    @POST("api/media/upload")
    suspend fun uploadMedia(
        @retrofit2.http.Part file: okhttp3.MultipartBody.Part,
        @retrofit2.http.Part("folder") folder: okhttp3.RequestBody? = null
    ): Response<MediaUploadResponse>

    @GET("api/versions/check")
    suspend fun checkVersion(
        @retrofit2.http.Query("versionCode") versionCode: Int
    ): Response<AppVersionCheckResponse>
}

data class AppVersionCheckResponse(
    val clientVersionCode: Int = 0,
    val latestVersion: String = "",
    val latestVersionCode: Int = 0,
    val minSupportedVersionCode: Int = 0,
    val isUpdateAvailable: Boolean = false,
    val isMandatory: Boolean = false,
    val title: String = "",
    val message: String = "",
    val apkArm64Url: String = "",
    val apkArmv7Url: String = "",
    val apkUniversalUrl: String = "",
    val downloadPortalUrl: String = ""
)


