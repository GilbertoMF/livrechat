package com.livrechat.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val contactUserId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val publicKey: String?,
    val bio: String = "",
    val addedAt: Long = System.currentTimeMillis()
)
