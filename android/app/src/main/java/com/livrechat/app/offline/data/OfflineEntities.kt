package com.livrechat.app.offline.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class StatusEntrega {
    PENDENTE,
    ENVIADO,
    ENTREGUE
}

/**
 * Entidade Contato para a Rede P2P Local (Sem Servidor)
 * Identificado por UUID local e nome do dispositivo de proximidade
 */
@Entity(tableName = "offline_contatos")
data class Contato(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val nomeDispositivo: String,
    val endpointId: String? = null,
    val isConectadoDireto: Boolean = false,
    val ultimoVisto: Long = System.currentTimeMillis()
)

/**
 * Entidade Conversa P2P Local
 */
@Entity(tableName = "offline_conversas")
data class Conversa(
    @PrimaryKey
    val id: String, // Normalmente o id do Contato
    val contatoNome: String,
    val ultimaMensagem: String,
    val ultimoTimestamp: Long,
    val naoLidas: Int = 0
)

/**
 * Entidade Mensagem P2P com suporte a Malha (Mesh Store-and-Forward)
 */
@Entity(tableName = "offline_mensagens")
data class Mensagem(
    @PrimaryKey
    val idMensagem: String = UUID.randomUUID().toString(),
    val remetenteId: String,
    val remetenteNome: String,
    val destinatarioId: String,
    val conteudo: String,
    val timestamp: Long = System.currentTimeMillis(),
    val statusEntrega: StatusEntrega = StatusEntrega.PENDENTE,
    val isMeshRoute: Boolean = false, // Flag de Roteamento em Malha
    val hops: Int = 0,                // Saltos intermediários percorridos
    val ttl: Int = 5                  // Time-To-Live para evitar loops
)

/**
 * Pacote de Rede em Malha transmitido via Nearby Connections (Payload de Bytes)
 */
data class MeshPacket(
    val packetId: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val recipientId: String,
    val recipientName: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "TEXT", // "TEXT", "ACK", "HANDSHAKE"
    val hops: Int = 0,
    val ttl: Int = 5
)
