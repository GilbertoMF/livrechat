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

    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    suspend fun getAllConversationsSync(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun getConversationByIdFlow(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE otherUserId = :otherUserId LIMIT 1")
    suspend fun getConversationByUserId(otherUserId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRaw(conversation: ConversationEntity)

    suspend fun insertOrUpdate(conversation: ConversationEntity) {
        insertOrUpdateRaw(conversation.copy(lastMessageContent = sanitizePreview(conversation.lastMessageContent)))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllRaw(conversations: List<ConversationEntity>)

    suspend fun insertAll(conversations: List<ConversationEntity>) {
        insertAllRaw(conversations.map { it.copy(lastMessageContent = sanitizePreview(it.lastMessageContent)) })
    }

    @Query("UPDATE conversations SET lastMessageContent = :content, lastMessageTimestamp = :timestamp WHERE id = :convId")
    suspend fun updateLastMessageRaw(convId: String, content: String, timestamp: Long)

    suspend fun updateLastMessage(convId: String, content: String, timestamp: Long) {
        updateLastMessageRaw(convId, sanitizePreview(content) ?: "", timestamp)
    }

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :convId")
    suspend fun clearUnreadCount(convId: String)

    @Query("UPDATE conversations SET unreadCount = unreadCount + 1 WHERE id = :convId")
    suspend fun incrementUnreadCount(convId: String)

    @Query("UPDATE conversations SET unreadCount = (SELECT COUNT(*) FROM messages WHERE conversationId = :convId AND isOutgoing = 0 AND status != 'read') WHERE id = :convId")
    suspend fun recalculateUnreadCount(convId: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("UPDATE conversations SET otherDisplayName = :displayName WHERE otherUserId = :otherUserId")
    suspend fun updateOtherDisplayName(otherUserId: String, displayName: String)

    @Query("UPDATE conversations SET pinnedMessageId = :messageId, pinnedMessageSnippet = :snippet WHERE id = :convId")
    suspend fun updatePinnedMessage(convId: String, messageId: String?, snippet: String?)
}

private fun sanitizePreview(content: String?): String? {
    if (content == null) return null
    return when {
        content.startsWith("[view_once_photo]") || content == "[Foto de visualização única]" -> "① Foto (Visualização única)"
        content.startsWith("[view_once_video]") || content == "[Vídeo de visualização única]" -> "① Vídeo (Visualização única)"
        content == "[Mídia expirada]" -> "① Mídia aberta"
        content.startsWith("[video_note]") || content.contains("video_note_") -> "⭕ Mensagem de vídeo"
        content.startsWith("data:image") -> "📷 Foto"
        content.startsWith("data:audio") || content.endsWith(".m4a") || content.endsWith(".mp3") || content.contains("voice_") -> "🎵 Áudio"
        content.startsWith("data:video") || content.endsWith(".mp4") -> "🎥 Vídeo"
        content.startsWith("data:application") -> "📄 Documento"
        content.length > 150 -> content.take(150) + "..."
        else -> content
    }
}
