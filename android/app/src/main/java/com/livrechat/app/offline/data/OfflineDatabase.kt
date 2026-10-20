package com.livrechat.app.offline.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineDao {
    // CONTATOS
    @Query("SELECT * FROM offline_contatos ORDER BY ultimoVisto DESC")
    fun getAllContatos(): Flow<List<Contato>>

    @Query("SELECT * FROM offline_contatos ORDER BY ultimoVisto DESC")
    suspend fun getAllContatosList(): List<Contato>

    @Query("SELECT * FROM offline_contatos WHERE id = :id LIMIT 1")
    suspend fun getContatoById(id: String): Contato?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateContato(contato: Contato)

    @Query("UPDATE offline_contatos SET endpointId = :endpointId, isConectadoDireto = :isConectado, ultimoVisto = :timestamp WHERE id = :id OR nomeDispositivo = :id")
    suspend fun updateConexaoContato(id: String, endpointId: String?, isConectado: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE offline_contatos SET isConectadoDireto = 0 WHERE endpointId = :endpointId OR id = :endpointId OR nomeDispositivo = :endpointId")
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
           OR remetenteNome LIKE '%' || :contactName || '%'
           OR destinatarioId LIKE '%' || :contactName || '%'
        ORDER BY timestamp ASC
    """)
    fun getMensagensForContato(contactId: String, contactName: String): Flow<List<Mensagem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMensagem(mensagem: Mensagem)

    @Query("UPDATE offline_mensagens SET statusEntrega = :status WHERE idMensagem = :idMensagem")
    suspend fun updateStatusMensagem(idMensagem: String, status: StatusEntrega)

    @Query("SELECT * FROM offline_mensagens ORDER BY timestamp ASC")
    suspend fun getAllMensagensList(): List<Mensagem>

    @Query("SELECT * FROM offline_mensagens WHERE destinatarioId = :destinatarioId AND statusEntrega = 'PENDENTE'")
    suspend fun getMensagensPendentes(destinatarioId: String): List<Mensagem>

    @Query("DELETE FROM offline_mensagens WHERE idMensagem = :idMensagem")
    suspend fun deleteMensagem(idMensagem: String)

    @Query("UPDATE offline_mensagens SET isBurned = 1, conteudo = '[Mensagem destruída]' WHERE idMensagem = :idMensagem")
    suspend fun markMensagemBurned(idMensagem: String)
}

@Database(
    entities = [Contato::class, Conversa::class, Mensagem::class],
    version = 4,
    exportSchema = false
)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun offlineDao(): OfflineDao

    companion object {
        @Volatile
        private var INSTANCE: OfflineDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offline_mensagens ADD COLUMN relayedVia TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offline_mensagens ADD COLUMN mediaType TEXT NOT NULL DEFAULT 'text'")
                db.execSQL("ALTER TABLE offline_mensagens ADD COLUMN mediaData TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offline_mensagens ADD COLUMN isDeadDrop INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE offline_mensagens ADD COLUMN isBurned INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): OfflineDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfflineDatabase::class.java,
                    "livrechat_offline_mesh.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                 .fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
