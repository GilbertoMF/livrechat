package com.livrechat.app.offline.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrechat.app.offline.data.Contato
import com.livrechat.app.offline.data.Conversa
import com.livrechat.app.offline.data.OfflineDatabase
import com.livrechat.app.offline.nearby.DiscoveredPeer
import com.livrechat.app.offline.nearby.NearbyManager
import com.livrechat.app.offline.nearby.NearbyPermissionHelper
import java.text.SimpleDateFormat
import java.util.*

/**
 * Interface Principal estilo WhatsApp Offline
 * Abas: "Conversas Ativas" e "Radar Próximos" (Radar BLE & Nós da Malha Mesh)
 * Botão "+" para iniciar conversa com qualquer aparelho conhecido na malha
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

    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Conversas, 1: Dispositivos Próximos
    var hasPermissions by remember { mutableStateOf(NearbyPermissionHelper.hasNearbyPermissions(context)) }
    var isGpsEnabled by remember { mutableStateOf(NearbyPermissionHelper.isLocationEnabled(context)) }
    var isBtEnabled by remember { mutableStateOf(NearbyPermissionHelper.isBluetoothEnabled()) }

    var showNewChatDialog by remember { mutableStateOf(false) }
    var newContactNameInput by remember { mutableStateOf("") }

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

    val conversas by dao.getAllConversas().collectAsState(initial = emptyList())
    val discoveredPeers by nearbyManager.discoveredPeers.collectAsState()
    val connectedPeers by nearbyManager.connectedPeers.collectAsState()

    val uniqueDiscoveredPeers = remember(discoveredPeers) {
        discoveredPeers.distinctBy { it.deviceName.trim().lowercase() }
    }
    val uniqueConnectedPeers = remember(connectedPeers) {
        connectedPeers.distinctBy { it.deviceName.trim().lowercase() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. PERMISSION ALERT BANNER
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
                // 2. HARDWARE RADIO ALERT (BLUETOOTH / GPS)
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
                                text = if (!isBtEnabled) "Ative o Bluetooth para escanear aparelhos." else "O Android 10/11 exige o GPS ativo na barra de status para descobrir dispositivos.",
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

            // 3. THIS DEVICE CARD & MESH STATUS BAR
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
                                    .background(if (connectedPeers.isNotEmpty()) Color(0xFF00E676) else Color(0xFFFFB74D), CircleShape)
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
                }
            }

            // 4. TAB ROW: CONVERSAS ATIVAS / RADAR PRÓXIMOS
            TabRow(selectedTabIndex = selectedSubTab) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = { Text("Conversas Ativas (${conversas.size})", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = { Text("Radar Próximos (${uniqueDiscoveredPeers.size})", fontWeight = FontWeight.SemiBold) }
                )
            }

            // 5. TAB CONTENT
            if (selectedSubTab == 0) {
                // TAB 1: CONVERSAS ATIVAS
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
                                "Toque no botão '+' abaixo ou vá até o 'Radar Próximos' para iniciar uma conversa ponto a ponto sem internet.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
            } else {
                // TAB 2: DISPOSITIVOS PRÓXIMOS (RADAR & NÓS DA MALHA)
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
                                "Abra esta aba em outro celular Android próximo com o app instalado para conectar automaticamente.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
        }

        // FLOATING ACTION BUTTON: NOVA CONVERSA NA MALHA
        FloatingActionButton(
            onClick = { showNewChatDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nova Conversa na Malha")
        }
    }

    // DIÁLOGO: NOVA CONVERSA POR NOME / ID
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
                        "Digite o nome do aparelho ou apelido com quem deseja conversar via malha offline (mesmo que ele esteja a vários saltos de distância através de outro celular):",
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

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = conversa.ultimaMensagem,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        isConnected -> Icons.Default.Sensors
        isMesh -> Icons.Default.Hub
        else -> Icons.Default.SensorsOff
    }
    val iconColor = when {
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
                Text(
                    text = peer.deviceName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
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
