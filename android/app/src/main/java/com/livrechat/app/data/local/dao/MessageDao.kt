package com.livrechat.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.livrechat.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY createdAt ASC, rowid ASC")
    fun getMessagesForConversationFlow(convId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY createdAt ASC, rowid ASC")
    suspend fun getMessagesForConversation(convId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE status = 'sending'")
    suspend fun getPendingOutboxMessages(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: String, status: String)

    @Query("UPDATE messages SET content = :newContent WHERE id = :messageId")
    suspend fun updateContent(messageId: String, newContent: String)

    @Query("UPDATE messages SET content = :newContent, isEdited = 1 WHERE id = :messageId")
    suspend fun editMessage(messageId: String, newContent: String)

    @Query("UPDATE messages SET content = 'Mensagem apagada', isDeleted = 1 WHERE id = :messageId")
    suspend fun softDeleteMessage(messageId: String)

    @Query("UPDATE messages SET reactions = :reactions WHERE id = :messageId")
    suspend fun updateReactions(messageId: String, reactions: String?)

    @Query("SELECT * FROM messages WHERE conversationId = :convId AND isOutgoing = 0 AND status != 'read'")
    suspend fun getUnreadIncomingMessages(convId: String): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :convId AND isOutgoing = 0 AND status != 'read'")
    suspend fun getUnreadCount(convId: String): Int

    @Query("UPDATE messages SET status = 'read' WHERE conversationId = :convId AND isOutgoing = 0")
    suspend fun markAllIncomingAsRead(convId: String)

    @Query("UPDATE messages SET status = 'read' WHERE conversationId = :convId AND isOutgoing = 1 AND status != 'read'")
    suspend fun markAllOutgoingAsRead(convId: String)

    @Query("UPDATE messages SET status = 'read' WHERE conversationId = :convId AND isOutgoing = 1 AND createdAt <= :timestamp AND status != 'read'")
    suspend fun markOutgoingAsReadUpTo(convId: String, timestamp: Long)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deletePermanently(messageId: String)

    @Query("DELETE FROM messages WHERE conversationId = :convId")
    suspend fun clearConversationMessages(convId: String)

    @Query("SELECT * FROM messages")
    suspend fun getAllMessages(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :convId AND createdAt > :threshold")
    suspend fun getFutureMessages(convId: String, threshold: Long): List<MessageEntity>

    @Query("UPDATE messages SET createdAt = :newCreatedAt WHERE id = :id")
    suspend fun updateCreatedAt(id: String, newCreatedAt: Long)

    @Query("UPDATE messages SET viewOnceOpened = 1, content = '[Mídia expirada]' WHERE id = :messageId")
    suspend fun markViewOnceOpened(messageId: String)

    @Query("UPDATE messages SET isPinned = :isPinned WHERE id = :messageId")
    suspend fun setPinned(messageId: String, isPinned: Boolean)

    @Query("UPDATE messages SET isPinned = 0 WHERE conversationId = :convId")
    suspend fun unpinAllForConversation(convId: String)

    @Query("SELECT * FROM messages WHERE conversationId = :convId AND isPinned = 1 ORDER BY createdAt DESC")
    suspend fun getPinnedMessages(convId: String): List<MessageEntity>
}
