package com.livrechat.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_records")
data class CallRecordEntity(
    @PrimaryKey val id: String,
    val contactUserId: String,
    val contactDisplayName: String,
    val contactUsername: String,
    val contactAvatarUrl: String?,
    val isOutgoing: Boolean,
    val status: String, // missed, rejected, completed, failed
    val durationSeconds: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
