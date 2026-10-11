package com.livrechat.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.livrechat.app.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE isCurrentUser = 1 LIMIT 1")
    fun getCurrentUserFlow(): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE isCurrentUser = 1 LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(user: UserEntity)

    @Query("DELETE FROM users WHERE isCurrentUser = 1")
    suspend fun clearCurrentUser()
}
