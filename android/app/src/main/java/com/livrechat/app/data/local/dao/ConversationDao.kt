package com.livrechat.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.livrechat.app.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    fun getAllConversationsFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE otherUserId = :otherUserId LIMIT 1")
    suspend fun getConversationByUserId(otherUserId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<ConversationEntity>)

    @Query("UPDATE conversations SET lastMessageContent = :content, lastMessageTimestamp = :timestamp WHERE id = :convId")
    suspend fun updateLastMessage(convId: String, content: String, timestamp: Long)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :convId")
    suspend fun clearUnreadCount(convId: String)

    @Query("UPDATE conversations SET unreadCount = unreadCount + 1 WHERE id = :convId")
    suspend fun incrementUnreadCount(convId: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)
}
