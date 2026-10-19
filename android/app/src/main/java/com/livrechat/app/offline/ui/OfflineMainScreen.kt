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
 * Abas: "Conversas Ativas" e "Dispositivos Próximos" (Radar BLE)
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // PERMISSION ALERT BANNER
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
        }

        // MESH STATUS BAR
        Surface(
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
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
                        text = if (connectedPeers.isNotEmpty()) "Malha Mesh Conectada (${connectedPeers.size} nós)" else "Buscando vizinhos BLE...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = {
                        nearbyManager.stopMeshNetwork()
                        nearbyManager.startMeshNetwork()
                        Toast.makeText(context, "Radar P2P Reiniciado", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reiniciar radar", modifier = Modifier.size(18.dp))
                }
            }
        }

        // TAB ROW: CONVERSAS ATIVAS / DISPOSITIVOS PRÓXIMOS
        TabRow(selectedTabIndex = selectedSubTab) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("Conversas Ativas (${conversas.size})", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("Radar Próximos (${discoveredPeers.size})", fontWeight = FontWeight.SemiBold) }
            )
        }

        // TAB CONTENT
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
                            "Nenhuma conversa offline",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Vá até a aba 'Radar Próximos' para descobrir aparelhos e iniciar uma conversa ponto a ponto sem internet.",
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
                            onClick = { onOpenChat(conv.id, conv.contatoNome) }
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        } else {
            // TAB 2: DISPOSITIVOS PRÓXIMOS (RADAR)
            if (discoveredPeers.isEmpty()) {
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
                    items(discoveredPeers, key = { it.endpointId }) { peer ->
                        val isConnected = connectedPeers.any { it.endpointId == peer.endpointId }
                        NearbyPeerCard(
                            peer = peer,
                            isConnected = isConnected,
                            onConnect = { nearbyManager.connectToPeer(peer.endpointId, peer.deviceName) },
                            onChat = { onOpenChat(peer.endpointId, peer.deviceName) }
                        )
                    }
                }
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
                    .background(
                        if (isConnected) Color(0xFF00E676).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.Sensors else Icons.Default.SensorsOff,
                    contentDescription = null,
                    tint = if (isConnected) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
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
                    text = if (isConnected) "Conectado diretamente via P2P" else "Descoberto via Bluetooth LE",
                    fontSize = 11.sp,
                    color = if (isConnected) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onChat,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(if (isConnected) "Conversar" else "Conectar", fontSize = 12.sp)
            }
        }
    }
}
