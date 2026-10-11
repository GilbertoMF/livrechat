package com.livrechat.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val publicKey: String?,
    val recoveryKey: String? = null,
    val isCurrentUser: Boolean = false
)
