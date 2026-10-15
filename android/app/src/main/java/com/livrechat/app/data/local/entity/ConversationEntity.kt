package com.livrechat.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val otherUserId: String,
    val otherUsername: String,
    val otherDisplayName: String,
    val otherAvatarUrl: String? = null,
    val otherPublicKey: String? = null,
    val lastMessageContent: String? = null,
    val lastMessageTimestamp: Long = 0L,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val isGroup: Boolean = false,
    val groupTitle: String? = null,
    val groupAvatar: String? = null,
    val adminOnlyMessage: Boolean = false,
    val adminOnlyEditInfo: Boolean = false,
    val adminOnlyAddMembers: Boolean = false
)
