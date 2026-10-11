package com.livrechat.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["createdAt"])
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val iv: String? = null,
    val messageType: String = "text",
    val replyToId: String? = null,
    val replyToContent: String? = null,
    val reactions: String? = null,
    val status: String = "sent", // sending, sent, delivered, read, failed
    val isOutgoing: Boolean,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
