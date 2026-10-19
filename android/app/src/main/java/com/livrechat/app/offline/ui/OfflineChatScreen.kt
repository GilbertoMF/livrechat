package com.livrechat.app.offline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrechat.app.offline.data.Mensagem
import com.livrechat.app.offline.data.OfflineDatabase
import com.livrechat.app.offline.data.StatusEntrega
import com.livrechat.app.offline.nearby.NearbyManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Janela de Bate-Papo P2P Offline
 * Histórico persistido 100% local no Room
 * Indicador de entrega (Pendente / Enviado / Entregue) e Flag de Roteamento em Malha
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineChatScreen(
    contactId: String,
    contactName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val nearbyManager = remember { NearbyManager.getInstance(context) }
    val dao = remember { OfflineDatabase.getInstance(context).offlineDao() }
    val scope = rememberCoroutineScope()

    val mensagens by dao.getMensagensForContato(contactId, contactName).collectAsState(initial = emptyList())
    val connectedPeers by nearbyManager.connectedPeers.collectAsState()
    val isDirectPeerConnected = remember(connectedPeers, contactId, contactName) {
        connectedPeers.any { it.endpointId == contactId || it.deviceName.equals(contactName, ignoreCase = true) }
    }

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(mensagens.size) {
        if (mensagens.isNotEmpty()) {
            listState.animateScrollToItem(mensagens.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = contactName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(if (isDirectPeerConnected) Color(0xFF00E676) else Color(0xFFA78BFA), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isDirectPeerConnected) "P2P Direto Conectado" else "Roteamento via Malha Mesh",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Mensagem offline sem internet...", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            val text = inputText.trim()
                            if (text.isNotBlank()) {
                                nearbyManager.sendMessage(contactId, contactName, text)
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Enviar",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (mensagens.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Envie a primeira mensagem offline.\nEla será entregue diretamente ou roteada via nós próximos.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(mensagens, key = { it.idMensagem }) { msg ->
                        val isMine = msg.remetenteId == nearbyManager.myDeviceId
                        OfflineMessageBubble(msg = msg, isMine = isMine)
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineMessageBubble(
    msg: Mensagem,
    isMine: Boolean
) {
    val timeText = remember(msg.timestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(msg.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isMine) 14.dp else 2.dp,
                bottomEnd = if (isMine) 2.dp else 14.dp
            ),
            color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // MESH RELAY HOP INDICATOR
                if (msg.isMeshRoute) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (isMine) Color(0xFFFFD54F) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Roteada via Malha (${msg.hops} saltos)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMine) Color(0xFFFFD54F) else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = msg.conteudo,
                    fontSize = 14.sp,
                    color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeText,
                        fontSize = 10.sp,
                        color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isMine) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (msg.statusEntrega) {
                            StatusEntrega.PENDENTE -> {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = "Pendente",
                                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            StatusEntrega.ENVIADO -> {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Enviado",
                                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            StatusEntrega.ENTREGUE -> {
                                Icon(
                                    Icons.Default.DoneAll,
                                    contentDescription = "Entregue",
                                    tint = Color(0xFF69F0AE),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
