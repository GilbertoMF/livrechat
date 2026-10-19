package com.livrechat.app.offline.nearby

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import com.livrechat.app.offline.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.UUID

data class DiscoveredPeer(
    val endpointId: String,
    val deviceName: String,
    val serviceId: String,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false
)

data class ConnectedPeer(
    val endpointId: String,
    val deviceName: String,
    val peerDeviceId: String? = null
)

/**
 * Gerenciador Singleton de Conexões Offline P2P via Google Nearby Connections API
 * Estratégia: Strategy.P2P_CLUSTER (Suporte a malha multidirecional M-para-N)
 * Transporte: BLE passivo + Wi-Fi Direct / Wi-Fi Aware de alta velocidade sob demanda
 */
class NearbyManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "NearbyMeshManager"
        const val SERVICE_ID = "com.livrechat.offline.mesh"
        val STRATEGY: Strategy = Strategy.P2P_CLUSTER

        @Volatile
        private var instance: NearbyManager? = null

        fun getInstance(context: Context): NearbyManager {
            return instance ?: synchronized(this) {
                instance ?: NearbyManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val database = OfflineDatabase.getInstance(context)
    private val dao = database.offlineDao()
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Identificador único persistente deste dispositivo na rede P2P
    val myDeviceId: String by lazy {
        val prefs = context.getSharedPreferences("offline_p2p_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("device_uuid", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("device_uuid", id).apply()
        }
        id
    }

    val myDeviceName: String by lazy {
        "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    }

    // Estados observáveis pela interface Compose
    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<Map<String, DiscoveredPeer>>(emptyMap())
    val discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())

    private val _connectedPeers = MutableStateFlow<Map<String, ConnectedPeer>>(emptyMap())
    val connectedPeers = MutableStateFlow<List<ConnectedPeer>>(emptyList())

    // Cache de pacotes já roteados para evitar loops na malha (Deduplicação de pacotes)
    private val forwardedPacketIds = Collections.synchronizedSet(HashSet<String>())

    // Fila de Store-and-Forward: mensagens aguardando proximidade com o destinatário
    private val storeAndForwardQueue = Collections.synchronizedList(ArrayList<MeshPacket>())

    // -------------------------------------------------------------------------
    // 1. DESCOBERTA E ANÚNCIO (ADVERTISING & DISCOVERY)
    // -------------------------------------------------------------------------

    fun startMeshNetwork() {
        if (!NearbyPermissionHelper.hasNearbyPermissions(context)) {
            Log.w(TAG, "Permissões de proximidade ausentes. Não é possível iniciar a malha.")
            return
        }
        startAdvertising()
        startDiscovery()
    }

    fun stopMeshNetwork() {
        stopAdvertising()
        stopDiscovery()
        connectionsClient.stopAllEndpoints()
        _connectedPeers.value = emptyMap()
        connectedPeers.value = emptyList()
        _discoveredPeers.value = emptyMap()
        discoveredPeers.value = emptyList()
    }

    fun startAdvertising() {
        if (_isAdvertising.value) return
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startAdvertising(
            myDeviceName,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            _isAdvertising.value = true
            Log.i(TAG, "Advertising iniciado com sucesso para $myDeviceName")
        }.addOnFailureListener { e ->
            _isAdvertising.value = false
            Log.e(TAG, "Falha ao iniciar Advertising: ${e.message}")
        }
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        _isAdvertising.value = false
    }

    fun startDiscovery() {
        if (_isDiscovering.value) return
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            _isDiscovering.value = true
            Log.i(TAG, "Discovery iniciado com sucesso no cluster")
        }.addOnFailureListener { e ->
            _isDiscovering.value = false
            Log.e(TAG, "Falha ao iniciar Discovery: ${e.message}")
        }
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        _isDiscovering.value = false
    }

    // -------------------------------------------------------------------------
    // 2. CALLBACKS DE CONEXÃO E DESCOBERTA
    // -------------------------------------------------------------------------

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.i(TAG, "Dispositivo próximo encontrado: $endpointId (${info.endpointName})")
            val peer = DiscoveredPeer(
                endpointId = endpointId,
                deviceName = info.endpointName,
                serviceId = info.serviceId
            )
            val updated = _discoveredPeers.value.toMutableMap()
            updated[endpointId] = peer
            _discoveredPeers.value = updated
            discoveredPeers.value = updated.values.toList()

            // Salva ou atualiza contato no Room
            scope.launch {
                val contato = Contato(
                    id = endpointId,
                    nomeDispositivo = info.endpointName,
                    endpointId = endpointId,
                    isConectadoDireto = false,
                    ultimoVisto = System.currentTimeMillis()
                )
                dao.insertOrUpdateContato(contato)
            }

            // Conexão automática para formar a malha descentralizada transparente
            connectToPeer(endpointId, info.endpointName)
        }

        override fun onEndpointLost(endpointId: String) {
            Log.i(TAG, "Dispositivo perdeu proximidade: $endpointId")
            val updated = _discoveredPeers.value.toMutableMap()
            updated.remove(endpointId)
            _discoveredPeers.value = updated
            discoveredPeers.value = updated.values.toList()
        }
    }

    fun connectToPeer(endpointId: String, peerName: String) {
        if (_connectedPeers.value.containsKey(endpointId)) return
        Log.i(TAG, "Iniciando conexão com $peerName ($endpointId)")
        connectionsClient.requestConnection(
            myDeviceName,
            endpointId,
            connectionLifecycleCallback
        ).addOnFailureListener { e ->
            Log.w(TAG, "Falha ao requisitar conexão com $endpointId: ${e.message}")
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.i(TAG, "Conexão iniciada com ${connectionInfo.endpointName} ($endpointId). Aceitando automaticamente...")
            // Aceita conexão de maneira automática para fluidez da malha P2P
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    val peerName = _discoveredPeers.value[endpointId]?.deviceName ?: "Dispositivo Próximo"
                    Log.i(TAG, "✅ Conectado com sucesso ao nó: $peerName ($endpointId)")
                    val peer = ConnectedPeer(endpointId = endpointId, deviceName = peerName)
                    val map = _connectedPeers.value.toMutableMap()
                    map[endpointId] = peer
                    _connectedPeers.value = map
                    connectedPeers.value = map.values.toList()

                    scope.launch {
                        dao.updateConexaoContato(endpointId, endpointId, true)
                        // Tenta despachar pacotes pendentes de Store-and-Forward para este nó
                        drainStoreAndForwardQueue(endpointId)
                    }
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.w(TAG, "Conexão rejeitada por $endpointId")
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.e(TAG, "Erro na conexão com $endpointId")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.i(TAG, "Desconectado do nó: $endpointId")
            val map = _connectedPeers.value.toMutableMap()
            map.remove(endpointId)
            _connectedPeers.value = map
            connectedPeers.value = map.values.toList()

            scope.launch {
                dao.desconectarEndpoint(endpointId)
            }
        }
    }

    // -------------------------------------------------------------------------
    // 3. PAYLOAD CALLBACK E LÓGICA DE REDE EM MALHA (STORE-AND-FORWARD MESH)
    // -------------------------------------------------------------------------

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val json = String(bytes, StandardCharsets.UTF_8)
                try {
                    val packet = gson.fromJson(json, MeshPacket::class.java)
                    handleIncomingMeshPacket(endpointId, packet)
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao decodificar pacote Mesh: ${e.message}")
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Monitoramento de progresso de pacotes grandes (Wi-Fi Direct / Imagens)
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                Log.d(TAG, "Payload enviado com sucesso para $endpointId")
            }
        }
    }

    private fun handleIncomingMeshPacket(fromEndpointId: String, packet: MeshPacket) {
        // 1. Evita loops: se o pacote já foi visto por este nó, ignora
        if (forwardedPacketIds.contains(packet.packetId)) {
            Log.d(TAG, "Pacote ${packet.packetId} duplicado descartado")
            return
        }
        forwardedPacketIds.add(packet.packetId)

        // Limite de segurança de memória para o cache de IDs
        if (forwardedPacketIds.size > 2000) {
            forwardedPacketIds.clear()
        }

        if (packet.type == "ACK") {
            // Confirmação de Entrega recebida via Malha
            scope.launch {
                dao.updateStatusMensagem(packet.content, StatusEntrega.ENTREGUE)
                Log.i(TAG, "✅ Confirmação de entrega recebida para mensagem ${packet.content}")
            }
            return
        }

        // 2. O pacote é destinado a este nó?
        if (packet.recipientId == myDeviceId || packet.recipientId == myDeviceName) {
            Log.i(TAG, "📩 Mensagem entregue a este dispositivo vinda de ${packet.senderName} (Hops: ${packet.hops})")
            scope.launch {
                val msg = Mensagem(
                    idMensagem = packet.packetId,
                    remetenteId = packet.senderId,
                    remetenteNome = packet.senderName,
                    destinatarioId = myDeviceId,
                    conteudo = packet.content,
                    timestamp = packet.timestamp,
                    statusEntrega = StatusEntrega.ENTREGUE,
                    isMeshRoute = packet.hops > 0,
                    hops = packet.hops,
                    ttl = packet.ttl
                )
                dao.insertMensagem(msg)

                val conversa = Conversa(
                    id = packet.senderId,
                    contatoNome = packet.senderName,
                    ultimaMensagem = packet.content,
                    ultimoTimestamp = packet.timestamp,
                    naoLidas = 1
                )
                dao.insertOrUpdateConversa(conversa)

                // Responde com ACK de confirmação de entrega ao remetente
                sendDeliveryAck(packet.senderId, packet.packetId)
            }
        } else {
            // 3. Lógica de Roteamento em Malha: Não é para mim, sou nó intermediário!
            Log.i(TAG, "🔄 Roteando pacote ${packet.packetId} destinado a ${packet.recipientId} (Hops atuais: ${packet.hops})")

            if (packet.ttl <= 1) {
                Log.w(TAG, "Pacote descartado: TTL esgotado (TTL=0)")
                return
            }

            val relayedPacket = packet.copy(
                hops = packet.hops + 1,
                ttl = packet.ttl - 1
            )

            // Guarda na fila de Store-and-Forward local para entrega futura se encontrar o nó
            storeAndForwardQueue.add(relayedPacket)

            // Repassa imediatamente para todos os outros nós conectados
            broadcastToOtherPeers(fromEndpointId, relayedPacket)
        }
    }

    /**
     * Envia mensagem para um contato na rede offline
     */
    fun sendMessage(recipientId: String, recipientName: String, content: String): Mensagem {
        val messageId = UUID.randomUUID().toString()
        val packet = MeshPacket(
            packetId = messageId,
            senderId = myDeviceId,
            senderName = myDeviceName,
            recipientId = recipientId,
            content = content,
            timestamp = System.currentTimeMillis(),
            type = "TEXT",
            hops = 0,
            ttl = 5
        )

        val localMsg = Mensagem(
            idMensagem = messageId,
            remetenteId = myDeviceId,
            remetenteNome = myDeviceName,
            destinatarioId = recipientId,
            conteudo = content,
            timestamp = System.currentTimeMillis(),
            statusEntrega = StatusEntrega.PENDENTE,
            isMeshRoute = false,
            hops = 0,
            ttl = 5
        )

        scope.launch {
            dao.insertMensagem(localMsg)
            dao.insertOrUpdateConversa(
                Conversa(
                    id = recipientId,
                    contatoNome = recipientName,
                    ultimaMensagem = content,
                    ultimoTimestamp = localMsg.timestamp,
                    naoLidas = 0
                )
            )

            // Tenta enviar diretamente para o peer conectado ou repassar via malha
            val directEndpoint = _connectedPeers.value.keys.firstOrNull { it == recipientId }
            if (directEndpoint != null) {
                sendPacketToEndpoint(directEndpoint, packet)
                dao.updateStatusMensagem(messageId, StatusEntrega.ENVIADO)
            } else {
                // Inunda os peers conectados para busca em malha (Flooding Mesh)
                forwardedPacketIds.add(packet.packetId)
                broadcastToAllPeers(packet)
                storeAndForwardQueue.add(packet)
                dao.updateStatusMensagem(messageId, StatusEntrega.ENVIADO)
            }
        }

        return localMsg
    }

    private fun sendDeliveryAck(originalSenderId: String, deliveredPacketId: String) {
        val ack = MeshPacket(
            packetId = UUID.randomUUID().toString(),
            senderId = myDeviceId,
            senderName = myDeviceName,
            recipientId = originalSenderId,
            content = deliveredPacketId,
            timestamp = System.currentTimeMillis(),
            type = "ACK",
            hops = 0,
            ttl = 5
        )
        broadcastToAllPeers(ack)
    }

    private fun sendPacketToEndpoint(endpointId: String, packet: MeshPacket) {
        val json = gson.toJson(packet)
        val payload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))
        connectionsClient.sendPayload(endpointId, payload).addOnFailureListener { e ->
            Log.e(TAG, "Falha ao enviar payload para $endpointId: ${e.message}")
        }
    }

    private fun broadcastToOtherPeers(excludeEndpointId: String, packet: MeshPacket) {
        val peers = _connectedPeers.value.keys.filter { it != excludeEndpointId }
        if (peers.isNotEmpty()) {
            val json = gson.toJson(packet)
            val payload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))
            connectionsClient.sendPayload(peers, payload)
        }
    }

    private fun broadcastToAllPeers(packet: MeshPacket) {
        val peers = _connectedPeers.value.keys.toList()
        if (peers.isNotEmpty()) {
            val json = gson.toJson(packet)
            val payload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))
            connectionsClient.sendPayload(peers, payload)
        }
    }

    private fun drainStoreAndForwardQueue(newlyConnectedEndpointId: String) {
        val toRemove = mutableListOf<MeshPacket>()
        synchronized(storeAndForwardQueue) {
            for (packet in storeAndForwardQueue) {
                if (packet.recipientId == newlyConnectedEndpointId) {
                    sendPacketToEndpoint(newlyConnectedEndpointId, packet)
                    toRemove.add(packet)
                }
            }
            storeAndForwardQueue.removeAll(toRemove)
        }
    }
}
