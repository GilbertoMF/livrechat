package com.livrechat.app.ui.home

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.livrechat.app.LivreChatApp
import com.livrechat.app.data.local.entity.CallRecordEntity
import com.livrechat.app.data.local.entity.ConversationEntity
import com.livrechat.app.ui.call.CallViewModel
import com.livrechat.app.ui.chat.ChatViewModel
import com.livrechat.app.ui.theme.BluePrimary
import com.livrechat.app.ui.theme.DangerRed
import com.livrechat.app.ui.theme.SuccessGreen
import com.livrechat.app.util.BiometricHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    chatViewModel: ChatViewModel,
    callViewModel: CallViewModel,
    onNavigateToChat: (String, String, String) -> Unit, // convId, otherUserId, displayName
    onNavigateToSearch: () -> Unit,
    onNavigateToQRCode: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onStartCall: (String, String, String, String?) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Conversas, 1 = Chamadas
    val conversations by chatViewModel.conversationsFlow.collectAsState(initial = emptyList())
    val callHistory by callViewModel.callHistoryFlow.collectAsState(initial = emptyList())
    val isConnected by chatViewModel.isConnected.collectAsState()
    val typingStatus by chatViewModel.typingStatus.collectAsState()

    val context = LocalContext.current
    val prefs = remember { LivreChatApp.instance.preferencesManager }
    var showClearCallHistoryDialog by remember { mutableStateOf(false) }
    var selectedConvForAction by remember { mutableStateOf<ConversationEntity?>(null) }
    var showClearConvDialog by remember { mutableStateOf<ConversationEntity?>(null) }

    LaunchedEffect(isConnected) {
        chatViewModel.syncConversations()
    }

    if (showClearCallHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearCallHistoryDialog = false },
            title = { Text("Limpar histórico de chamadas?", fontWeight = FontWeight.Bold) },
            text = { Text("Todas as chamadas recebidas e efetuadas serão apagadas.") },
            confirmButton = {
                TextButton(onClick = {
                    callViewModel.clearCallHistory()
                    showClearCallHistoryDialog = false
                    Toast.makeText(context, "Histórico de chamadas limpo", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Limpar", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCallHistoryDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (selectedConvForAction != null) {
        val conv = selectedConvForAction!!
        val isLocked = prefs.isConversationLocked(conv.id)
        AlertDialog(
            onDismissRequest = { selectedConvForAction = null },
            title = { Text(conv.otherDisplayName, fontWeight = FontWeight.Bold) },
            text = { Text("Opções da conversa:") },
            confirmButton = {
                TextButton(onClick = {
                    val newLocked = !isLocked
                    prefs.setConversationLocked(conv.id, newLocked)
                    selectedConvForAction = null
                    Toast.makeText(
                        context,
                        if (newLocked) "Conversa bloqueada com biometria" else "Conversa desbloqueada",
                        Toast.LENGTH_SHORT
                    ).show()
                }) {
                    Text(if (isLocked) "Desbloquear com Digital" else "Bloquear com Digital", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showClearConvDialog = conv
                        selectedConvForAction = null
                    }) {
                        Text("Limpar conversa", color = DangerRed)
                    }
                    TextButton(onClick = { selectedConvForAction = null }) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }

    if (showClearConvDialog != null) {
        val conv = showClearConvDialog!!
        AlertDialog(
            onDismissRequest = { showClearConvDialog = null },
            title = { Text("Limpar conversa?", fontWeight = FontWeight.Bold) },
            text = { Text("Todas as mensagens com ${conv.otherDisplayName} serão apagadas.") },
            confirmButton = {
                TextButton(onClick = {
                    chatViewModel.clearConversation(conv.id, conv.otherUserId)
                    showClearConvDialog = null
                    Toast.makeText(context, "Conversa limpa", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Limpar mensagens", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConvDialog = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("LivreChat", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (isConnected) SuccessGreen else DangerRed,
                            modifier = Modifier.size(9.dp)
                        ) {}
                    }
                },
                actions = {
                    if (selectedTab == 1 && callHistory.isNotEmpty()) {
                        IconButton(onClick = { showClearCallHistoryDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Limpar Histórico", tint = DangerRed)
                        }
                    }
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Pesquisar")
                    }
                    IconButton(onClick = onNavigateToQRCode) {
                        Icon(Icons.Default.QrCode, contentDescription = "Meu QR Code")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToSearch,
                containerColor = BluePrimary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (selectedTab == 0) Icons.Default.Chat else Icons.Default.Call,
                    contentDescription = "Novo"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs: Conversas / Chamadas
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Conversas",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Chamadas",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            if (selectedTab == 0) {
                // Conversations Tab
                if (conversations.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Forum,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Nenhuma conversa iniciada",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Toque no botão abaixo para adicionar contatos",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(conversations, key = { it.id }) { conv ->
                            val isTyping = typingStatus[conv.id] == true
                            val isLocked = prefs.isConversationLocked(conv.id)
                            ConversationItem(
                                conversation = conv,
                                isTyping = isTyping,
                                isLocked = isLocked,
                                onClick = {
                                    if (isLocked) {
                                        val activity = context as? FragmentActivity
                                        if (activity != null) {
                                            BiometricHelper.authenticate(
                                                activity = activity,
                                                title = "Conversa Bloqueada",
                                                subtitle = "Confirme sua digital para abrir a conversa",
                                                onSuccess = {
                                                    onNavigateToChat(conv.id, conv.otherUserId, conv.otherDisplayName)
                                                },
                                                onError = {
                                                    Toast.makeText(context, "Acesso negado", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        } else {
                                            onNavigateToChat(conv.id, conv.otherUserId, conv.otherDisplayName)
                                        }
                                    } else {
                                        onNavigateToChat(conv.id, conv.otherUserId, conv.otherDisplayName)
                                    }
                                },
                                onLongClick = {
                                    selectedConvForAction = conv
                                }
                            )
                            Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }
                }
            } else {
                // Calls Tab
                if (callHistory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PhoneMissed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Nenhum histórico de chamadas",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(callHistory, key = { it.id }) { call ->
                            CallHistoryItem(
                                call = call,
                                onCallBack = {
                                    onStartCall(
                                        call.contactUserId,
                                        call.contactDisplayName,
                                        call.contactUsername,
                                        call.contactAvatarUrl
                                    )
                                }
                            )
                            Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    conversation: ConversationEntity,
    isTyping: Boolean = false,
    isLocked: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Surface(
            shape = CircleShape,
            color = BluePrimary.copy(alpha = 0.2f),
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!conversation.otherAvatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = conversation.otherAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Text(
                        text = conversation.otherDisplayName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = BluePrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = conversation.otherDisplayName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isLocked) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Bloqueada",
                            tint = BluePrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = formatTimestamp(conversation.lastMessageTimestamp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isTyping) {
                    Text(
                        text = "digitando...",
                        fontSize = 14.sp,
                        color = Color(0xFF34B7F1),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = conversation.lastMessageContent ?: "Sem mensagens ainda",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (conversation.unreadCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = BluePrimary,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = conversation.unreadCount.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CallHistoryItem(
    call: CallRecordEntity,
    onCallBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCallBack)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = BluePrimary.copy(alpha = 0.2f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = call.contactDisplayName.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = BluePrimary
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = call.contactDisplayName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (call.isOutgoing) Icons.Default.CallMade else Icons.Default.CallReceived,
                    contentDescription = null,
                    tint = if (call.status == "rejected" || call.status == "missed") DangerRed else SuccessGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatTimestamp(call.timestamp) + if (call.durationSeconds > 0) " (${call.durationSeconds}s)" else "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onCallBack) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = "Ligar",
                tint = BluePrimary
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val sdf = if (diff < 86400000L) {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    } else {
        SimpleDateFormat("dd/MM", Locale.getDefault())
    }
    return sdf.format(Date(timestamp))
}
