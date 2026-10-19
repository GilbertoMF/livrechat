package com.livrechat.app.offline.nearby

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.google.android.gms.common.api.ApiException
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
    val serviceId: String = NearbyManager.SERVICE_ID,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val isMeshRelayed: Boolean = false,
    val relayedVia: String? = null,
    val hops: Int = 0
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
 * Protocolo Gossip: Anúncio e propagação automática de nós conhecidos na malha
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
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
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

    // Rastreamento de conexões pendentes para desempate determinístico (evita colisão de handshake)
    private val pendingConnectionPeers = Collections.synchronizedSet(HashSet<String>())
    private val initiatedOrIncomingNames = Collections.synchronizedMap(HashMap<String, String>())

    // Tabela de Roteamento Gossip Mesh: Nome do Destinatário -> EndpointId do intermediário (Gateway)
    val meshRoutingTable = Collections.synchronizedMap(HashMap<String, String>())
    private var gossipJob: Job? = null

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
        if (_connectedPeers.value.isNotEmpty()) {
            startGossipLoop()
        }
    }

    fun restartMeshNetwork() {
        Log.i(TAG, "Reiniciando malha Nearby Connections...")
        stopMeshNetwork()
        scope.launch {
            delay(1000)
            startMeshNetwork()
        }
    }

    fun stopMeshNetwork() {
        stopAdvertising()
        stopDiscovery()
        stopGossipLoop()
        try {
            connectionsClient.stopAllEndpoints()
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao parar endpoints: ${e.message}")
        }
        pendingConnectionPeers.clear()
        initiatedOrIncomingNames.clear()
        meshRoutingTable.clear()
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
            if (e is ApiException && e.statusCode == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
                _isAdvertising.value = true
                Log.d(TAG, "Já estava em Advertising")
            } else {
                _isAdvertising.value = false
                Log.e(TAG, "Falha ao iniciar Advertising: ${e.message}")
            }
        }
    }

    fun stopAdvertising() {
        try {
            connectionsClient.stopAdvertising()
        } catch (e: Exception) {}
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
            if (e is ApiException && e.statusCode == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                _isDiscovering.value = true
                Log.d(TAG, "Já estava em Discovery")
            } else {
                _isDiscovering.value = false
                Log.e(TAG, "Falha ao iniciar Discovery: ${e.message}")
            }
        }
    }

    fun stopDiscovery() {
        try {
            connectionsClient.stopDiscovery()
        } catch (e: Exception) {}
        _isDiscovering.value = false
    }

    // -------------------------------------------------------------------------
    // 2. CALLBACKS DE CONEXÃO E DESCOBERTA (COM TIE-BREAKER DETERMINÍSTICO)
    // -------------------------------------------------------------------------

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val peerName = info.endpointName.ifBlank { "Dispositivo Próximo" }
            Log.i(TAG, "Dispositivo próximo encontrado via BLE/WiFi: $endpointId ($peerName)")

            val peer = DiscoveredPeer(
                endpointId = endpointId,
                deviceName = peerName,
                serviceId = info.serviceId,
                isMeshRelayed = false
            )
            val updated = _discoveredPeers.value.toMutableMap()
            updated[endpointId] = peer
            _discoveredPeers.value = updated
            discoveredPeers.value = updated.values.toList()

            // Salva ou atualiza contato no Room usando o nome do dispositivo como ID estável
            scope.launch {
                val contato = Contato(
                    id = peerName,
                    nomeDispositivo = peerName,
                    endpointId = endpointId,
                    isConectadoDireto = _connectedPeers.value.containsKey(endpointId),
                    ultimoVisto = System.currentTimeMillis()
                )
                dao.insertOrUpdateContato(contato)
            }

            // Verifica se já temos conexão ativa com esse dispositivo (pelo nome ou endpoint)
            val alreadyConnected = _connectedPeers.value.values.any {
                it.deviceName.equals(peerName, ignoreCase = true) || it.endpointId == endpointId
            }
            if (alreadyConnected) {
                Log.d(TAG, "Já conectado a $peerName ($endpointId)")
                return
            }

            // Verifica se já há uma conexão em andamento para este peer
            if (pendingConnectionPeers.contains(peerName)) {
                Log.d(TAG, "Conexão com $peerName já está em andamento")
                return
            }

            // TIE-BREAKER DETERMINÍSTICO:
            // Para evitar colisões de requisições simultâneas (dois nós ligando requestConnection ao mesmo tempo),
            // apenas o dispositivo cujo nome for lexicograficamente MAIOR toma a iniciativa de conectar.
            // O outro dispositivo simplesmente aguarda e aceita automaticamente.
            val shouldInitiate = myDeviceName.compareTo(peerName, ignoreCase = true) > 0

            if (shouldInitiate) {
                Log.i(TAG, "🤖 [Tie-Breaker: Iniciador] Meu nome ($myDeviceName) > ($peerName). Conectando a $endpointId...")
                connectToPeer(endpointId, peerName)
            } else {
                Log.i(TAG, "🎧 [Tie-Breaker: Ouvinte] Meu nome ($myDeviceName) < ($peerName). Aguardando conexão de $peerName...")
                // Fallback de segurança: se o outro nó não iniciar a conexão em 4 segundos, tentamos
                scope.launch {
                    delay(4000)
                    val stillDisconnected = !_connectedPeers.value.values.any {
                        it.deviceName.equals(peerName, ignoreCase = true)
                    }
                    if (stillDisconnected && !pendingConnectionPeers.contains(peerName)) {
                        Log.i(TAG, "⏰ Fallback timer ativado: Iniciando conexão com $peerName ($endpointId)...")
                        connectToPeer(endpointId, peerName)
                    }
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.i(TAG, "Dispositivo perdeu proximidade física: $endpointId")
            val updated = _discoveredPeers.value.toMutableMap()
            // Só remove se não for alcançável via malha Gossip
            val existing = updated[endpointId]
            if (existing != null && !existing.isMeshRelayed) {
                updated.remove(endpointId)
                _discoveredPeers.value = updated
                discoveredPeers.value = updated.values.toList()
            }
        }
    }

    fun connectToPeer(endpointId: String, peerName: String) {
        if (_connectedPeers.value.containsKey(endpointId)) return
        if (pendingConnectionPeers.contains(peerName)) return

        pendingConnectionPeers.add(peerName)
        initiatedOrIncomingNames[endpointId] = peerName

        Log.i(TAG, "Solicitando conexão P2P para $peerName ($endpointId)")
        connectionsClient.requestConnection(
            myDeviceName,
            endpointId,
            connectionLifecycleCallback
        ).addOnFailureListener { e ->
            Log.w(TAG, "Falha na requisição de conexão com $peerName ($endpointId): ${e.message}")
            pendingConnectionPeers.remove(peerName)
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            val peerName = connectionInfo.endpointName.ifBlank { "Dispositivo Próximo" }
            Log.i(TAG, "Conexão iniciada com $peerName ($endpointId). Aceitando automaticamente...")
            initiatedOrIncomingNames[endpointId] = peerName

            // Aceita conexão de maneira automática para fluidez da malha P2P
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e ->
                    Log.e(TAG, "Erro ao aceitar conexão com $endpointId: ${e.message}")
                    pendingConnectionPeers.remove(peerName)
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val peerName = initiatedOrIncomingNames[endpointId]
                ?: _discoveredPeers.value[endpointId]?.deviceName
                ?: "Dispositivo Próximo"

            pendingConnectionPeers.remove(peerName)

            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK,
                ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT -> {
                    Log.i(TAG, "✅ CONECTADO com sucesso ao nó: $peerName ($endpointId)")
                    val peer = ConnectedPeer(endpointId = endpointId, deviceName = peerName)
                    val map = _connectedPeers.value.toMutableMap()
                    map[endpointId] = peer
                    _connectedPeers.value = map
                    connectedPeers.value = map.values.toList()

                    // Remove da tabela de rotas se agora está direto
                    meshRoutingTable.remove(peerName)

                    scope.launch {
                        dao.updateConexaoContato(peerName, endpointId, true)
                        dao.updateConexaoContato(endpointId, endpointId, true)
                        // Despacha mensagens pendentes armazenadas localmente para este nó
                        drainStoreAndForwardQueue(endpointId)
                        // Anuncia os nós da malha via Gossip Protocol
                        broadcastGossipAnnouncement()
                        startGossipLoop()
                    }
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.w(TAG, "Conexão rejeitada por $peerName ($endpointId)")
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.e(TAG, "Erro na conexão com $peerName ($endpointId)")
                }
                else -> {
                    Log.w(TAG, "Status de conexão: ${result.status.statusCode} com $peerName")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            val peer = _connectedPeers.value[endpointId]
            val peerName = peer?.deviceName ?: initiatedOrIncomingNames[endpointId] ?: "Dispositivo"
            Log.i(TAG, "Desconectado do nó: $endpointId ($peerName)")

            val map = _connectedPeers.value.toMutableMap()
            map.remove(endpointId)
            _connectedPeers.value = map
            connectedPeers.value = map.values.toList()

            pendingConnectionPeers.remove(peerName)
            initiatedOrIncomingNames.remove(endpointId)

            // Limpa rotas que usavam este nó como gateway
            meshRoutingTable.entries.removeIf { it.value == endpointId }

            scope.launch {
                dao.desconectarEndpoint(endpointId)
                if (peerName.isNotBlank()) {
                    dao.updateConexaoContato(peerName, null, false)
                }
            }

            if (_connectedPeers.value.isEmpty()) {
                stopGossipLoop()
                // Se não há nenhum nó conectado, reinicia a malha para reestabelecer o radar
                scope.launch {
                    delay(2000)
                    if (_connectedPeers.value.isEmpty()) {
                        Log.d(TAG, "Nenhum peer restante conectado, garantindo que o radar está ativo...")
                        startMeshNetwork()
                    }
                }
            } else {
                // Atualiza os peers restantes via gossip
                broadcastGossipAnnouncement()
            }
        }
    }

    // -------------------------------------------------------------------------
    // 3. GOSSIP PROTOCOL (ANÚNCIO E PROPAGAÇÃO DE NÓS DA MALHA)
    // -------------------------------------------------------------------------

    private fun startGossipLoop() {
        gossipJob?.cancel()
        gossipJob = scope.launch {
            while (isActive) {
                delay(8000)
                broadcastGossipAnnouncement()
            }
        }
    }

    private fun stopGossipLoop() {
        gossipJob?.cancel()
        gossipJob = null
    }

    /**
     * Anuncia para todos os nós conectados os outros aparelhos que este nó conhece
     */
    fun broadcastGossipAnnouncement() {
        if (_connectedPeers.value.isEmpty()) return

        val connectedNames = _connectedPeers.value.values.map { it.deviceName }.distinct()
        val allKnownNodes = (connectedNames + meshRoutingTable.keys).distinct().filter {
            !it.equals(myDeviceName, ignoreCase = true)
        }
        if (allKnownNodes.isEmpty()) return

        val gossipPayloadJson = gson.toJson(allKnownNodes)
        val packet = MeshPacket(
            packetId = UUID.randomUUID().toString(),
            senderId = myDeviceId,
            senderName = myDeviceName,
            recipientId = "*",
            recipientName = "*",
            content = gossipPayloadJson,
            timestamp = System.currentTimeMillis(),
            type = "GOSSIP_ANNOUNCE",
            hops = 1,
            ttl = 3
        )

        val json = gson.toJson(packet)
        val payload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))
        val peers = _connectedPeers.value.keys.toList()
        if (peers.isNotEmpty()) {
            connectionsClient.sendPayload(peers, payload)
        }
    }

    // -------------------------------------------------------------------------
    // 4. PAYLOAD CALLBACK E LÓGICA DE REDE EM MALHA (STORE-AND-FORWARD MESH)
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
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                Log.d(TAG, "Transferência concluída com sucesso para endpoint $endpointId")
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

        if (forwardedPacketIds.size > 2000) {
            forwardedPacketIds.clear()
        }

        // Se for anúncio de nós via Gossip Protocol
        if (packet.type == "GOSSIP_ANNOUNCE") {
            try {
                val listType = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                val announcedNodes: List<String> = gson.fromJson(packet.content, listType) ?: emptyList()
                val updated = _discoveredPeers.value.toMutableMap()
                var hasChanges = false

                for (nodeName in announcedNodes) {
                    if (nodeName.isBlank() || nodeName.equals(myDeviceName, ignoreCase = true)) continue

                    val isDirect = _connectedPeers.value.values.any { it.deviceName.equals(nodeName, ignoreCase = true) }
                    if (!isDirect) {
                        meshRoutingTable[nodeName] = fromEndpointId

                        val existing = updated[nodeName]
                        if (existing == null || !existing.isConnected) {
                            updated[nodeName] = DiscoveredPeer(
                                endpointId = nodeName,
                                deviceName = nodeName,
                                serviceId = SERVICE_ID,
                                isConnecting = false,
                                isConnected = false,
                                isMeshRelayed = true,
                                relayedVia = packet.senderName,
                                hops = packet.hops
                            )
                            hasChanges = true
                        }

                        scope.launch {
                            val contato = Contato(
                                id = nodeName,
                                nomeDispositivo = nodeName,
                                endpointId = null,
                                isConectadoDireto = false,
                                ultimoVisto = System.currentTimeMillis()
                            )
                            dao.insertOrUpdateContato(contato)
                        }
                    }
                }

                if (hasChanges) {
                    _discoveredPeers.value = updated
                    discoveredPeers.value = updated.values.toList()
                }

                // Propaga o gossip aos demais peers se TTL permitir
                if (packet.ttl > 1) {
                    val relayedGossip = packet.copy(
                        hops = packet.hops + 1,
                        ttl = packet.ttl - 1
                    )
                    broadcastToOtherPeers(fromEndpointId, relayedGossip)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao processar anúncio Gossip: ${e.message}")
            }
            return
        }

        // Se for pacote de confirmação de entrega (ACK)
        if (packet.type == "ACK") {
            // Verifica se este ACK é para nós ou se devemos retransmitir
            val isAckForMe = packet.recipientId == myDeviceId ||
                             packet.recipientName.equals(myDeviceName, ignoreCase = true) ||
                             packet.recipientName.isBlank()

            if (isAckForMe) {
                scope.launch {
                    dao.updateStatusMensagem(packet.content, StatusEntrega.ENTREGUE)
                    Log.i(TAG, "✅ Confirmação de entrega (ACK) recebida para mensagem ID: ${packet.content}")
                }
            } else {
                // Repassa o ACK para o autor da mensagem
                val gateway = _connectedPeers.value.entries.firstOrNull {
                    it.value.deviceName.equals(packet.recipientName, ignoreCase = true)
                }?.key ?: meshRoutingTable[packet.recipientName]

                if (gateway != null && _connectedPeers.value.containsKey(gateway)) {
                    sendPacketToEndpoint(gateway, packet)
                } else {
                    broadcastToOtherPeers(fromEndpointId, packet)
                }
            }
            return
        }

        // 2. O pacote é destinado a este nó?
        val isForMe = packet.recipientId == myDeviceId ||
                      packet.recipientId.equals(myDeviceName, ignoreCase = true) ||
                      (packet.recipientName.isNotBlank() && (
                          packet.recipientName.equals(myDeviceName, ignoreCase = true) ||
                          packet.recipientName.contains(myDeviceName, ignoreCase = true) ||
                          myDeviceName.contains(packet.recipientName, ignoreCase = true)
                      )) ||
                      (packet.hops == 0 && _connectedPeers.value.containsKey(fromEndpointId)) ||
                      _connectedPeers.value.size <= 1

        if (isForMe) {
            Log.i(TAG, "📩 MENSAGEM RECEBIDA de ${packet.senderName}: '${packet.content}' (Hops: ${packet.hops})")
            scope.launch {
                val msg = Mensagem(
                    idMensagem = packet.packetId,
                    remetenteId = packet.senderId,
                    remetenteNome = packet.senderName,
                    destinatarioId = myDeviceName,
                    conteudo = packet.content,
                    timestamp = packet.timestamp,
                    statusEntrega = StatusEntrega.ENTREGUE,
                    isMeshRoute = packet.hops > 0,
                    hops = packet.hops,
                    ttl = packet.ttl
                )
                dao.insertMensagem(msg)

                val conversa = Conversa(
                    id = packet.senderName,
                    contatoNome = packet.senderName,
                    ultimaMensagem = packet.content,
                    ultimoTimestamp = packet.timestamp,
                    naoLidas = 1
                )
                dao.insertOrUpdateConversa(conversa)

                // Feedback tátil/vibração ao receber mensagem
                try {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(120)
                    }
                } catch (e: Exception) {}

                // Responde imediatamente com ACK de confirmação de entrega ao remetente
                sendDeliveryAck(fromEndpointId, packet.senderId, packet.senderName, packet.packetId)
            }
        } else {
            // 3. Roteamento em Malha (Mesh Relay)
            Log.i(TAG, "🔄 [Relay Intermediário] Roteando pacote ${packet.packetId} de ${packet.senderName} para ${packet.recipientName} (Hops: ${packet.hops})")

            if (packet.ttl <= 1) {
                Log.w(TAG, "Pacote descartado: TTL esgotado")
                return
            }

            val relayedPacket = packet.copy(
                hops = packet.hops + 1,
                ttl = packet.ttl - 1
            )

            // Verifica se o destinatário final está conectado diretamente a este nó intermediário
            val directTargetEndpoint = _connectedPeers.value.entries.firstOrNull { entry ->
                entry.key == packet.recipientId ||
                entry.value.deviceName.equals(packet.recipientName, ignoreCase = true) ||
                (packet.recipientName.isNotBlank() && entry.value.deviceName.contains(packet.recipientName, ignoreCase = true))
            }?.key

            if (directTargetEndpoint != null) {
                Log.i(TAG, "🎯 Destinatário final ${packet.recipientName} está conectado neste nó! Entregando direto para $directTargetEndpoint...")
                sendPacketToEndpoint(directTargetEndpoint, relayedPacket)
            } else {
                val gateway = meshRoutingTable[packet.recipientName]
                if (gateway != null && gateway != fromEndpointId && _connectedPeers.value.containsKey(gateway)) {
                    Log.i(TAG, "Roteando para gateway conhecido $gateway...")
                    sendPacketToEndpoint(gateway, relayedPacket)
                } else {
                    storeAndForwardQueue.add(relayedPacket)
                    broadcastToOtherPeers(fromEndpointId, relayedPacket)
                }
            }
        }
    }

    /**
     * Envia mensagem para um contato na rede offline
     */
    fun sendMessage(recipientId: String, recipientName: String, content: String): Mensagem {
        val messageId = UUID.randomUUID().toString()
        val targetName = recipientName.ifBlank { recipientId }

        val packet = MeshPacket(
            packetId = messageId,
            senderId = myDeviceId,
            senderName = myDeviceName,
            recipientId = targetName,
            recipientName = targetName,
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
            destinatarioId = targetName,
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
                    id = targetName,
                    contatoNome = targetName,
                    ultimaMensagem = content,
                    ultimoTimestamp = localMsg.timestamp,
                    naoLidas = 0
                )
            )

            // 1. Procura conexão direta nos peers conectados
            val directEndpoint = _connectedPeers.value.entries.firstOrNull { entry ->
                entry.key == recipientId ||
                entry.value.deviceName.equals(targetName, ignoreCase = true) ||
                entry.value.deviceName.contains(targetName, ignoreCase = true) ||
                targetName.contains(entry.value.deviceName, ignoreCase = true)
            }?.key

            // 2. Se não estiver conectado direto, procura na tabela de roteamento da malha (Gossip)
            val gatewayEndpoint = directEndpoint ?: meshRoutingTable[targetName] ?: meshRoutingTable.entries.firstOrNull {
                it.key.equals(targetName, ignoreCase = true) || it.key.contains(targetName, ignoreCase = true)
            }?.value

            if (gatewayEndpoint != null && _connectedPeers.value.containsKey(gatewayEndpoint)) {
                Log.i(TAG, "Roteando mensagem para $targetName através do endpoint $gatewayEndpoint (Direto=${directEndpoint != null})")
                sendPacketToEndpoint(gatewayEndpoint, packet)
            } else {
                Log.i(TAG, "Destinatário não conectado diretamente. Armazenando na fila e propagando na malha...")
                forwardedPacketIds.add(packet.packetId)
                storeAndForwardQueue.add(packet)
                if (_connectedPeers.value.isNotEmpty()) {
                    broadcastToAllPeers(packet)
                }
            }
        }

        return localMsg
    }

    private fun sendDeliveryAck(directEndpointId: String?, originalSenderId: String, originalSenderName: String, deliveredPacketId: String) {
        val ack = MeshPacket(
            packetId = UUID.randomUUID().toString(),
            senderId = myDeviceId,
            senderName = myDeviceName,
            recipientId = originalSenderName.ifBlank { originalSenderId },
            recipientName = originalSenderName,
            content = deliveredPacketId,
            timestamp = System.currentTimeMillis(),
            type = "ACK",
            hops = 0,
            ttl = 5
        )

        // Se temos conexão direta com o remetente original
        if (directEndpointId != null && _connectedPeers.value.containsKey(directEndpointId)) {
            val json = gson.toJson(ack)
            val payload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))
            connectionsClient.sendPayload(directEndpointId, payload).addOnSuccessListener {
                Log.d(TAG, "ACK enviado direto com sucesso para $directEndpointId")
            }
        } else {
            val gateway = meshRoutingTable[originalSenderName]
            if (gateway != null && _connectedPeers.value.containsKey(gateway)) {
                sendPacketToEndpoint(gateway, ack)
            } else {
                broadcastToAllPeers(ack)
            }
        }
    }

    private fun sendPacketToEndpoint(endpointId: String, packet: MeshPacket) {
        val json = gson.toJson(packet)
        val payload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))
        connectionsClient.sendPayload(endpointId, payload)
            .addOnSuccessListener {
                Log.i(TAG, "Payload entregue ao rádio para envio ao nó $endpointId (msg: ${packet.packetId})")
                scope.launch {
                    dao.updateStatusMensagem(packet.packetId, StatusEntrega.ENVIADO)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Falha ao enviar payload para $endpointId: ${e.message}")
                // Armazena na fila para reenvio automático quando a conexão for reestabelecida
                storeAndForwardQueue.add(packet)
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
            connectionsClient.sendPayload(peers, payload).addOnSuccessListener {
                scope.launch {
                    dao.updateStatusMensagem(packet.packetId, StatusEntrega.ENVIADO)
                }
            }
        }
    }

    private fun drainStoreAndForwardQueue(newlyConnectedEndpointId: String) {
        val peerName = _connectedPeers.value[newlyConnectedEndpointId]?.deviceName ?: ""
        val toRemove = mutableListOf<MeshPacket>()
        synchronized(storeAndForwardQueue) {
            for (packet in storeAndForwardQueue) {
                val matches = packet.recipientId == newlyConnectedEndpointId ||
                              packet.recipientId.equals(peerName, ignoreCase = true) ||
                              (peerName.isNotBlank() && (
                                  packet.recipientName.equals(peerName, ignoreCase = true) ||
                                  packet.recipientName.contains(peerName, ignoreCase = true) ||
                                  peerName.contains(packet.recipientName, ignoreCase = true)
                              )) ||
                              packet.recipientName.isBlank()
                if (matches) {
                    sendPacketToEndpoint(newlyConnectedEndpointId, packet)
                    toRemove.add(packet)
                }
            }
            storeAndForwardQueue.removeAll(toRemove)
        }
    }
}
