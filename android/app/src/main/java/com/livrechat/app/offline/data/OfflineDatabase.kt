package com.livrechat.app.offline.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineDao {
    // CONTATOS
    @Query("SELECT * FROM offline_contatos ORDER BY ultimoVisto DESC")
    fun getAllContatos(): Flow<List<Contato>>

    @Query("SELECT * FROM offline_contatos WHERE id = :id LIMIT 1")
    suspend fun getContatoById(id: String): Contato?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateContato(contato: Contato)

    @Query("UPDATE offline_contatos SET endpointId = :endpointId, isConectadoDireto = :isConectado, ultimoVisto = :timestamp WHERE id = :id")
    suspend fun updateConexaoContato(id: String, endpointId: String?, isConectado: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE offline_contatos SET isConectadoDireto = 0 WHERE endpointId = :endpointId")
    suspend fun desconectarEndpoint(endpointId: String)

    // CONVERSAS
    @Query("SELECT * FROM offline_conversas ORDER BY ultimoTimestamp DESC")
    fun getAllConversas(): Flow<List<Conversa>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConversa(conversa: Conversa)

    // MENSAGENS
    @Query("""
        SELECT * FROM offline_mensagens 
        WHERE remetenteId = :contactId 
           OR destinatarioId = :contactId 
           OR remetenteNome = :contactName 
           OR remetenteId = :contactName 
           OR destinatarioId = :contactName
        ORDER BY timestamp ASC
    """)
    fun getMensagensForContato(contactId: String, contactName: String): Flow<List<Mensagem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMensagem(mensagem: Mensagem)

    @Query("UPDATE offline_mensagens SET statusEntrega = :status WHERE idMensagem = :idMensagem")
    suspend fun updateStatusMensagem(idMensagem: String, status: StatusEntrega)

    @Query("SELECT * FROM offline_mensagens WHERE destinatarioId = :destinatarioId AND statusEntrega = 'PENDENTE'")
    suspend fun getMensagensPendentes(destinatarioId: String): List<Mensagem>
}

@Database(
    entities = [Contato::class, Conversa::class, Mensagem::class],
    version = 1,
    exportSchema = false
)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun offlineDao(): OfflineDao

    companion object {
        @Volatile
        private var INSTANCE: OfflineDatabase? = null

        fun getInstance(context: Context): OfflineDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfflineDatabase::class.java,
                    "livrechat_offline_mesh.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
