package com.livrechat.app.data.remote.model

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    val username: String,
    val password: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val publicKey: String? = null,
    val deviceName: String? = "Android Device"
)

data class RegisterResponse(
    val user: UserDto,
    val recoveryKey: String,
    val tokens: TokensDto
)

data class LoginRequest(
    val username: String,
    val password: String,
    val deviceName: String? = "Android Device"
)

data class LoginResponse(
    val user: UserDto,
    val tokens: TokensDto
)

data class RecoverRequest(
    val username: String,
    val recoveryKey: String,
    val newPassword: String
)

data class BaseResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)

data class VirtualNumberRequestResponse(
    val success: Boolean,
    val virtualNumber: String,
    val code: String,
    val message: String? = null
)

data class UserDto(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val publicKey: String? = null,
    val bio: String? = null,
    val phoneNumber: String? = null,
    val createdAt: String? = null
)

data class TokensDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: String
)

data class ConversationDto(
    val conversationId: String,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val isGroup: Boolean = false,
    val title: String? = null,
    val avatarUrl: String? = null,
    val otherUser: UserDto? = null,
    val lastMessage: MessageDto? = null,
    val unreadCount: Int = 0,
    val updatedAt: String? = null
)

data class MessageDto(
    val id: String,
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("sender_id") val senderId: String,
    val content: String,
    val iv: String? = null,
    @SerializedName("message_type") val messageType: String = "text",
    @SerializedName("reply_to_id") val replyToId: String? = null,
    @SerializedName("is_deleted") val isDeleted: Boolean = false,
    @SerializedName("is_edited") val isEdited: Boolean = false,
    @SerializedName("created_at") val createdAt: String,
    val status: String? = null,
    val receipts: List<ReceiptDto>? = null
)

data class ReceiptDto(
    val messageId: String,
    val userId: String,
    val status: String,
    val timestamp: String
)

data class SendMessageRequest(
    val conversationId: String,
    val content: String,
    val iv: String? = null,
    val messageType: String = "text",
    val replyToId: String? = null,
    val clientMessageId: String? = null,
    val targetUserId: String? = null
)

data class IceServersResponse(
    val iceServers: List<IceServerDto>
)

data class IceServerDto(
    val urls: Any, // Can be String or List<String>
    val username: String? = null,
    val credential: String? = null
)

data class CallHistoryDto(
    val id: String,
    val callerId: String,
    val calleeId: String,
    val isOutgoing: Boolean,
    val status: String,
    val startedAt: String,
    val endedAt: String? = null,
    val durationSeconds: Int = 0,
    val contact: UserDto
)

data class AddContactRequest(
    val contactUserId: String,
    val nickname: String? = null
)

data class ContactDto(
    val id: String,
    val contactUserId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val publicKey: String? = null,
    val bio: String = ""
)

data class DeviceContactPayload(
    val name: String? = null,
    val phones: List<String> = emptyList()
)

data class SyncContactsRequest(
    val contacts: List<DeviceContactPayload>
)

data class MatchedContactDto(
    val contactUserId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val publicKey: String? = null,
    val bio: String = "",
    val phoneNumber: String? = null,
    val matchedPhone: String? = null
)

data class SyncContactsResponse(
    val matchedCount: Int,
    val contacts: List<MatchedContactDto>
)

data class StatusReactionDto(
    @SerializedName("user_id") val userId: String,
    val username: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    val emoji: String,
    @SerializedName("created_at") val createdAt: String? = null
)

data class StatusViewDto(
    @SerializedName("user_id") val userId: String,
    val username: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("viewed_at") val viewedAt: String? = null
)

data class StatusDto(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("display_name") val displayName: String,
    val username: String,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("content_type") val contentType: String,
    val content: String,
    val caption: String? = null,
    @SerializedName("background_color") val backgroundColor: String? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("expires_at") val expiresAt: String? = null,
    val reactions: List<StatusReactionDto>? = null,
    val views: List<StatusViewDto>? = null
)

data class CreateGroupRequest(
    val title: String,
    val memberIds: List<String>
)

data class GroupMemberDto(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val isAdmin: Boolean = false
)

data class AddMembersRequest(
    val memberIds: List<String>
)

data class SessionDto(
    val id: String = "",
    @SerializedName("user_id", alternate = ["userId"]) val userId: String = "",
    @SerializedName("device_name", alternate = ["deviceName"]) val deviceName: String = "Aparelho Conectado",
    @SerializedName("ip_address", alternate = ["ipAddress"]) val ipAddress: String = "",
    @SerializedName("last_active", alternate = ["lastActive"]) val lastActive: String = "",
    @SerializedName("created_at", alternate = ["createdAt"]) val createdAt: String = "",
    @SerializedName("is_current", alternate = ["isCurrent"]) val isCurrent: Boolean = false
)
