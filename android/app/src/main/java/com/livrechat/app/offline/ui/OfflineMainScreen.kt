package com.livrechat.app.offline.ui

import android.graphics.Paint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrechat.app.offline.data.Contato
import com.livrechat.app.offline.data.Conversa
import com.livrechat.app.offline.data.OfflineDatabase
import com.livrechat.app.offline.nearby.DiscoveredPeer
import com.livrechat.app.offline.nearby.MeshMediaHelper
import com.livrechat.app.offline.nearby.NearbyManager
import com.livrechat.app.offline.nearby.NearbyPermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Interface Principal Offline Superpoderosa
 * - Aba 0: "Conversas" (Histórico de chats locais)
 * - Aba 1: "Radar BLE" (Lista de nós próximos e conectividade)
 * - Aba 2: "Topologia da Malha" (Radar Gráfico Interativo com órbitas, nós e partículas)
 * - Walkie-Talkie Push-to-Talk (PTT) instantâneo
 * - Ponte de Internet Híbrida (Internet Gateway / Mesh Proxy)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMainScreen(
    onOpenChat: (contactId: String, contactName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val nearbyManager = remember { NearbyManager.getInstance(context) }
    val dao = remember { OfflineDatabase.getInstance(context).offlineDao() }
    val scope = rememberCoroutineScope()

    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Conversas, 1: Radar BLE, 2: Topologia
    var hasPermissions by remember { mutableStateOf(NearbyPermissionHelper.hasNearbyPermissions(context)) }
    var isGpsEnabled by remember { mutableStateOf(NearbyPermissionHelper.isLocationEnabled(context)) }
    var isBtEnabled by remember { mutableStateOf(NearbyPermissionHelper.isBluetoothEnabled()) }

    var showNewChatDialog by remember { mutableStateOf(false) }
    var newContactNameInput by remember { mutableStateOf("") }
    var inspectingPeer by remember { mutableStateOf<DiscoveredPeer?>(null) }

    // Estados observáveis da malha
    val conversas by dao.getAllConversas().collectAsState(initial = emptyList())
    val discoveredPeers by nearbyManager.discoveredPeers.collectAsState()
    val connectedPeers by nearbyManager.connectedPeers.collectAsState()

    // Walkie-Talkie e Gateway
    val incomingSpeaker by nearbyManager.incomingPttSpeaker.collectAsState()
    val isInternetGateway by nearbyManager.isInternetGateway.collectAsState()
    val meshGateways by nearbyManager.meshGateways.collectAsState()
    val lastProxyStatus by nearbyManager.lastProxyStatus.collectAsState()

    // Estado local de transmissão PTT
    var isTransmittingPtt by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        hasPermissions = allGranted
        if (allGranted) {
            nearbyManager.startMeshNetwork()
            Toast.makeText(context, "Radar P2P Ativado!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permissões de proximidade são necessárias para o modo offline", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            nearbyManager.startMeshNetwork()
        }
    }

    val uniqueDiscoveredPeers = remember(discoveredPeers) {
        discoveredPeers.distinctBy { it.deviceName.trim().lowercase() }
    }
    val uniqueConnectedPeers = remember(connectedPeers) {
        connectedPeers.distinctBy { it.deviceName.trim().lowercase() }
    }

    fun triggerVibration(durationMs: Long) {
        try {
            val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {}
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. AVISO DE PERMISSÕES
            if (!hasPermissions) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Permissões de Proximidade", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Ative Bluetooth e Wi-Fi Direct para comunicar sem internet.", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { permissionLauncher.launch(NearbyPermissionHelper.getRequiredPermissions()) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Ativar", fontSize = 12.sp)
                        }
                    }
                }
            } else if (!isBtEnabled || !isGpsEnabled) {
                // 2. AVISO DE RÁDIOS DESATIVADOS
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (!isBtEnabled) "Bluetooth Desativado" else "Localização (GPS) Desativada",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = if (!isBtEnabled) "Ative o Bluetooth para escanear aparelhos." else "O Android exige GPS ativo na barra de status para descobrir dispositivos.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Button(
                            onClick = {
                                val intent = if (!isBtEnabled) {
                                    android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                } else {
                                    android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                }
                                try { context.startActivity(intent) } catch (e: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Text("Ativar", fontSize = 11.sp)
                        }
                    }
                }
            }

            // 3. BANNER DE WALKIE-TALKIE RECEBENDO ÁUDIO
            AnimatedVisibility(
                visible = incomingSpeaker != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = Color(0xFF1E88E5),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📢 Walkie-Talkie em Reprodução",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Nó: ${incomingSpeaker ?: "Desconhecido"} falando...",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // 4. STATUS DO DISPOSITIVO & PONTE DE INTERNET HÍBRIDA
            Surface(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(if (uniqueConnectedPeers.isNotEmpty()) Color(0xFF00E676) else Color(0xFFFFB74D), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uniqueConnectedPeers.isNotEmpty()) "Malha Mesh Conectada (${uniqueConnectedPeers.size} nós)" else "Buscando vizinhos BLE...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = {
                                nearbyManager.restartMeshNetwork()
                                isGpsEnabled = NearbyPermissionHelper.isLocationEnabled(context)
                                isBtEnabled = NearbyPermissionHelper.isBluetoothEnabled()
                                Toast.makeText(context, "Radar P2P Reiniciado", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reiniciar radar", modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "📱 Seu aparelho: ${nearbyManager.myDeviceName}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Indicador de Gateway de Internet
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            if (isInternetGateway) {
                                Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("🟢 Gateway Ativo (Compartilhando internet)", fontSize = 11.sp, color = Color(0xFF00E676), fontWeight = FontWeight.SemiBold)
                            } else if (meshGateways.isNotEmpty()) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("⚡ Internet via nó: ${meshGateways.first()}", fontSize = 11.sp, color = Color(0xFFFFD54F), fontWeight = FontWeight.SemiBold)
                            } else {
                                Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Malha Offline Isolada (Sem gateway)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (!isInternetGateway && meshGateways.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    nearbyManager.requestInternetProxy()
                                    Toast.makeText(context, "Solicitando ping à nuvem via malha...", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Testar Nuvem", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Resposta do Proxy Nuvem
                    if (!lastProxyStatus.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Text(
                                text = lastProxyStatus ?: "",
                                fontSize = 10.sp,
                                modifier = Modifier.padding(6.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // 5. BARRA DE ABAS: CONVERSAS / RADAR / MAPA DA MALHA
            TabRow(selectedTabIndex = selectedSubTab) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = { Text("Conversas (${conversas.size})", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = { Text("Radar (${uniqueDiscoveredPeers.size})", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedSubTab == 2,
                    onClick = { selectedSubTab = 2 },
                    text = { Text("Mapa da Malha", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                )
            }

            // 6. CONTEÚDO DAS ABAS
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedSubTab) {
                    0 -> {
                        // TAB 0: CONVERSAS ATIVAS
                        if (conversas.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                    Icon(
                                        Icons.Default.WifiOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        "Nenhuma conversa offline ainda",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Toque no botão '+' abaixo ou vá até o 'Radar' para iniciar um bate-papo sem internet.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(conversas, key = { it.id }) { conv ->
                                    OfflineConversationItem(
                                        conversa = conv,
                                        onClick = { onOpenChat(conv.contatoNome, conv.contatoNome) }
                                    )
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }

                    1 -> {
                        // TAB 1: RADAR DISPOSITIVOS PRÓXIMOS
                        if (uniqueDiscoveredPeers.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Escaneando via Bluetooth LE e Wi-Fi Direct...",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Mantenha o app aberto em outro celular próximo para conectar automaticamente.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(uniqueDiscoveredPeers, key = { it.deviceName.trim().lowercase() }) { peer ->
                                    val isConnected = uniqueConnectedPeers.any {
                                        it.endpointId == peer.endpointId ||
                                        it.deviceName.equals(peer.deviceName, ignoreCase = true)
                                    }
                                    NearbyPeerCard(
                                        peer = peer,
                                        isConnected = isConnected,
                                        onConnect = { nearbyManager.connectToPeer(peer.endpointId, peer.deviceName) },
                                        onChat = { onOpenChat(peer.deviceName, peer.deviceName) }
                                    )
                                }
                            }
                        }
                    }

                    2 -> {
                        // TAB 2: MAPA TOPOLÓGICO DA MALHA (RADAR INTERATIVO CANVAS)
                        MeshTopologyRadarCanvas(
                            myDeviceName = nearbyManager.myDeviceName,
                            connectedPeers = uniqueConnectedPeers,
                            discoveredPeers = uniqueDiscoveredPeers,
                            meshGateways = meshGateways,
                            onNodeClick = { peer ->
                                inspectingPeer = peer
                            }
                        )
                    }
                }
            }

            // 7. BOTÃO WALKIE-TALKIE PUSH-TO-TALK (PTT)
            Surface(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val pttButtonColor = if (isTransmittingPtt) Color(0xFFE53935) else MaterialTheme.colorScheme.primary

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(pttButtonColor)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        triggerVibration(50)
                                        isTransmittingPtt = true
                                        val started = MeshMediaHelper.startPttRecording(context)
                                        if (started) {
                                            tryAwaitRelease()
                                            val audio = MeshMediaHelper.stopPttRecording()
                                            isTransmittingPtt = false
                                            triggerVibration(40)
                                            if (!audio.isNullOrBlank()) {
                                                nearbyManager.broadcastPttAudio(audio)
                                                Toast.makeText(context, "Áudio transmitido no Walkie-Talkie!", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            isTransmittingPtt = false
                                            Toast.makeText(context, "Permissão de microfone necessária", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isTransmittingPtt) Icons.Default.GraphicEq else Icons.Default.Radio,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isTransmittingPtt) "FALANDO NA MALHA (SOLTE PARA ENVIAR)..." else "SEGURE PARA FALAR NO WALKIE-TALKIE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // FLOATING ACTION BUTTON: NOVA CONVERSA
        if (selectedSubTab == 0) {
            FloatingActionButton(
                onClick = { showNewChatDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 80.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nova Conversa na Malha")
            }
        }
    }

    // DIÁLOGO: NOVA CONVERSA
    if (showNewChatDialog) {
        AlertDialog(
            onDismissRequest = { showNewChatDialog = false },
            icon = {
                Icon(
                    Icons.Default.AddComment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    "Nova Conversa na Malha",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column {
                    Text(
                        "Digite o nome do aparelho ou apelido com quem deseja conversar via malha offline (mesmo a vários saltos de distância):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = newContactNameInput,
                        onValueChange = { newContactNameInput = it },
                        placeholder = { Text("Ex: Motorola moto e(6i)", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newContactNameInput.trim()
                        if (name.isNotBlank()) {
                            showNewChatDialog = false
                            newContactNameInput = ""
                            onOpenChat(name, name)
                        }
                    }
                ) {
                    Text("Iniciar Conversa")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewChatDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // BOTTOM SHEET / DIALOG: INSPEÇÃO DO NÓ NO RADAR
    inspectingPeer?.let { peer ->
        val isDirect = uniqueConnectedPeers.any { it.deviceName.equals(peer.deviceName, ignoreCase = true) }
        val hasNet = peer.hasInternet || meshGateways.contains(peer.deviceName)

        AlertDialog(
            onDismissRequest = { inspectingPeer = null },
            icon = {
                Icon(
                    if (isDirect) Icons.Default.Sensors else Icons.Default.Hub,
                    contentDescription = null,
                    tint = if (isDirect) Color(0xFF00E676) else Color(0xFFA78BFA)
                )
            },
            title = {
                Text(peer.deviceName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isDirect) "🔗 Conexão Direta P2P (Wi-Fi Direct / BLE)" else "🌐 Roteamento em Malha Mesh (${peer.hops} salto${if (peer.hops > 1) "s" else ""})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (isDirect) Color(0xFF00E676) else Color(0xFFA78BFA)
                    )
                    if (!peer.relayedVia.isNullOrBlank()) {
                        Text(
                            text = "Roteado através de: ${peer.relayedVia}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (hasNet) {
                        Text(
                            text = "⚡ Gateway de Internet Ativo (Conectado à nuvem)",
                            fontSize = 12.sp,
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Offline local (Ponto a Ponto sem internet)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val name = peer.deviceName
                    inspectingPeer = null
                    onOpenChat(name, name)
                }) {
                    Text("Conversar Agora")
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectingPeer = null }) {
                    Text("Fechar")
                }
            }
        )
    }
}

/**
 * Radar Visual Interativo da Topologia da Malha Mesh
 * Desenha:
 * - Centro: "Você" com pulso animado
 * - Órbita 1: Nós Diretos P2P
 * - Órbita 2: Nós Relayed / Multi-hop Mesh
 * - Linhas de conexão com partículas de dados fluindo
 * - Ícone ⚡ nos Gateways de Internet
 * - Toque no nó para inspecionar
 */
@Composable
fun MeshTopologyRadarCanvas(
    myDeviceName: String,
    connectedPeers: List<com.livrechat.app.offline.nearby.ConnectedPeer>,
    discoveredPeers: List<DiscoveredPeer>,
    meshGateways: Set<String>,
    onNodeClick: (DiscoveredPeer) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarAnimation")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    val density = LocalDensity.current
    val nodeHitTargets = remember { mutableStateListOf<Triple<DiscoveredPeer, Offset, Float>>() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(discoveredPeers) {
                    detectTapGestures { tapOffset ->
                        val hit = nodeHitTargets.firstOrNull { (_, center, radius) ->
                            val dx = tapOffset.x - center.x
                            val dy = tapOffset.y - center.y
                            sqrt(dx * dx + dy * dy) <= (radius + 20f)
                        }
                        if (hit != null) {
                            onNodeClick(hit.first)
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val orbit1Radius = size.width.coerceAtMost(size.height) * 0.24f
            val orbit2Radius = size.width.coerceAtMost(size.height) * 0.42f

            nodeHitTargets.clear()

            // 1. Órbitas Construtivas do Radar
            val orbitPaint = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
            )

            // Pulso de Radar Central
            drawCircle(
                color = Color(0xFF38BDF8).copy(alpha = (1f - pulseProgress) * 0.35f),
                radius = orbit2Radius * pulseProgress,
                center = center
            )

            drawCircle(color = Color(0xFF334155), radius = orbit1Radius, center = center, style = orbitPaint)
            drawCircle(color = Color(0xFF1E293B), radius = orbit2Radius, center = center, style = orbitPaint)

            // Separa os nós em Diretos e Relayed
            val directList = discoveredPeers.filter { peer ->
                !peer.isMeshRelayed || connectedPeers.any { it.deviceName.equals(peer.deviceName, ignoreCase = true) }
            }
            val relayedList = discoveredPeers.filter { it.isMeshRelayed && !directList.contains(it) }

            val directPositions = mutableMapOf<String, Offset>()

            // 2. Nós Diretos (Órbita 1)
            val directCount = directList.size
            directList.forEachIndexed { index, peer ->
                val angle = (2 * Math.PI / directCount.coerceAtLeast(1) * index) - (Math.PI / 2)
                val nodePos = Offset(
                    (center.x + orbit1Radius * cos(angle)).toFloat(),
                    (center.y + orbit1Radius * sin(angle)).toFloat()
                )
                directPositions[peer.deviceName] = nodePos

                // Linha de Conexão com "Você"
                drawLine(
                    color = Color(0xFF00E676).copy(alpha = 0.5f),
                    start = center,
                    end = nodePos,
                    strokeWidth = 2.5f
                )

                // Partícula de dados em movimento na linha
                val particlePos = Offset(
                    center.x + (nodePos.x - center.x) * pulseProgress,
                    center.y + (nodePos.y - center.y) * pulseProgress
                )
                drawCircle(color = Color(0xFF69F0AE), radius = 4f, center = particlePos)

                // Nó Direto
                val hasNet = peer.hasInternet || meshGateways.contains(peer.deviceName)
                val nodeColor = if (hasNet) Color(0xFFFFD54F) else Color(0xFF00E676)
                drawCircle(color = nodeColor, radius = 16f, center = nodePos)
                drawCircle(color = Color.White, radius = 6f, center = nodePos)

                nodeHitTargets.add(Triple(peer, nodePos, 20f))

                // Texto do Nome do Nó
                val paint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 28f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                val label = if (hasNet) "⚡ ${peer.deviceName}" else peer.deviceName
                drawContext.canvas.nativeCanvas.drawText(
                    label.take(12),
                    nodePos.x,
                    nodePos.y + 36f,
                    paint
                )
            }

            // 3. Nós em Malha Mesh / Relayed (Órbita 2)
            val relayedCount = relayedList.size
            relayedList.forEachIndexed { index, peer ->
                val angle = (2 * Math.PI / relayedCount.coerceAtLeast(1) * index)
                val nodePos = Offset(
                    (center.x + orbit2Radius * cos(angle)).toFloat(),
                    (center.y + orbit2Radius * sin(angle)).toFloat()
                )

                // Conecta nó relayed ao seu nó intermediário ou ao centro se desconhecido
                val parentPos = directPositions[peer.relayedVia] ?: center
                drawLine(
                    color = Color(0xFFA78BFA).copy(alpha = 0.4f),
                    start = parentPos,
                    end = nodePos,
                    strokeWidth = 2f
                )

                // Partícula
                val particlePos = Offset(
                    parentPos.x + (nodePos.x - parentPos.x) * pulseProgress,
                    parentPos.y + (nodePos.y - parentPos.y) * pulseProgress
                )
                drawCircle(color = Color(0xFFC084FC), radius = 3.5f, center = particlePos)

                // Nó Relayed
                val hasNet = peer.hasInternet || meshGateways.contains(peer.deviceName)
                val nodeColor = if (hasNet) Color(0xFFFFD54F) else Color(0xFFA78BFA)
                drawCircle(color = nodeColor, radius = 14f, center = nodePos)
                drawCircle(color = Color.White, radius = 5f, center = nodePos)

                nodeHitTargets.add(Triple(peer, nodePos, 18f))

                val paint = Paint().apply {
                    color = android.graphics.Color.LTGRAY
                    textSize = 24f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                val label = if (hasNet) "⚡ ${peer.deviceName}" else peer.deviceName
                drawContext.canvas.nativeCanvas.drawText(
                    label.take(12),
                    nodePos.x,
                    nodePos.y + 32f,
                    paint
                )
            }

            // 4. Nó Central "Você"
            drawCircle(color = Color(0xFF0284C7).copy(alpha = 0.3f), radius = 30f, center = center)
            drawCircle(color = Color(0xFF38BDF8), radius = 18f, center = center)
            drawCircle(color = Color.White, radius = 7f, center = center)

            val centerPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 30f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                "Você ($myDeviceName)".take(16),
                center.x,
                center.y + 46f,
                centerPaint
            )
        }

        // LEGENDA DO MAPA
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E293B).copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Legenda da Malha", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF38BDF8), CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Você (Centro)", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF00E676), CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Direto P2P (Órbita 1)", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFA78BFA), CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Malha Mesh (Órbita 2)", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡", fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gateway de Internet", fontSize = 10.sp, color = Color(0xFFFFD54F))
                }
                Text("Toque em qualquer nó para abrir", fontSize = 9.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun OfflineConversationItem(
    conversa: Conversa,
    onClick: () -> Unit
) {
    val dateText = remember(conversa.ultimoTimestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(conversa.ultimoTimestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversa.contatoNome,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val isMeshPreview = conversa.ultimaMensagem.startsWith("🌐")
            Text(
                text = conversa.ultimaMensagem,
                fontSize = 13.sp,
                color = if (isMeshPreview) Color(0xFFA78BFA) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isMeshPreview) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun NearbyPeerCard(
    peer: DiscoveredPeer,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onChat: () -> Unit
) {
    val isMesh = peer.isMeshRelayed
    val badgeText = when {
        isConnected -> "Conectado diretamente via P2P"
        isMesh -> "Alcançável via Malha (através de ${peer.relayedVia ?: "nó"} • ${peer.hops} salto${if (peer.hops > 1) "s" else ""})"
        else -> "Descoberto via Bluetooth LE"
    }
    val badgeColor = when {
        isConnected -> Color(0xFF00E676)
        isMesh -> Color(0xFFA78BFA)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconVector = when {
        peer.hasInternet -> Icons.Default.Bolt
        isConnected -> Icons.Default.Sensors
        isMesh -> Icons.Default.Hub
        else -> Icons.Default.SensorsOff
    }
    val iconColor = when {
        peer.hasInternet -> Color(0xFFFFD54F)
        isConnected -> Color(0xFF00E676)
        isMesh -> Color(0xFFA78BFA)
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconColor.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = peer.deviceName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (peer.hasInternet) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "⚡ Gateway",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD54F)
                        )
                    }
                }
                Text(
                    text = badgeText,
                    fontSize = 11.sp,
                    color = badgeColor
                )
            }

            Button(
                onClick = {
                    if (isConnected || isMesh) {
                        onChat()
                    } else {
                        onConnect()
                        onChat()
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected || isMesh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(if (isConnected || isMesh) "Conversar" else "Conectar", fontSize = 12.sp)
            }
        }
    }
}
