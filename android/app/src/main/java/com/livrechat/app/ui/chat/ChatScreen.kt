package com.livrechat.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrechat.app.data.local.entity.MessageEntity
import com.livrechat.app.ui.theme.BlueAccent
import com.livrechat.app.ui.theme.BluePrimary
import com.livrechat.app.ui.theme.OfflineGray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    otherUserId: String,
    otherDisplayName: String,
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onStartCall: (String, String, String, String?) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var typingDebounceJob by remember { mutableStateOf<Job?>(null) }

    val currentFlow by viewModel.messagesFlow.collectAsState()
    val messages by currentFlow.collectAsState(initial = emptyList())
    val isTyping by viewModel.isContactTyping.collectAsState()

    LaunchedEffect(conversationId) {
        viewModel.selectConversation(conversationId)
    }

    DisposableEffect(conversationId) {
        onDispose {
            typingDebounceJob?.cancel()
            viewModel.sendTyping(false)
            viewModel.unselectConversation()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = otherDisplayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = if (isTyping) "digitando..." else "LivreChat Criptografado",
                            fontSize = 12.sp,
                            color = if (isTyping) BlueAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onStartCall(otherUserId, otherDisplayName, "", null)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Chamada de Voz WebRTC",
                            tint = BluePrimary
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Chat Input Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = {
                            textInput = it
                            if (it.isNotBlank()) {
                                viewModel.sendTyping(true)
                                typingDebounceJob?.cancel()
                                typingDebounceJob = scope.launch {
                                    delay(2500)
                                    viewModel.sendTyping(false)
                                }
                            } else {
                                typingDebounceJob?.cancel()
                                viewModel.sendTyping(false)
                            }
                        },
                        placeholder = { Text("Digite uma mensagem...", fontSize = 14.sp) },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )

                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                typingDebounceJob?.cancel()
                                viewModel.sendMessage(conversationId, textInput.trim())
                                textInput = ""
                                viewModel.sendTyping(false)
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = BluePrimary),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Enviar",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message = message)
            }
        }
    }
}

@Composable
fun MessageBubble(message: MessageEntity) {
    val isOutgoing = message.isOutgoing
    val alignment = if (isOutgoing) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isOutgoing) 16.dp else 4.dp,
                bottomEnd = if (isOutgoing) 4.dp else 16.dp
            ),
            color = if (isOutgoing) BluePrimary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.content,
                    color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.createdAt)),
                        color = if (isOutgoing) Color.White.copy(alpha = 0.7f) else OfflineGray,
                        fontSize = 11.sp
                    )

                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        MessageStatusIcon(status = message.status)
                    }
                }
            }
        }
    }
}

@Composable
fun MessageStatusIcon(status: String) {
    when (status) {
        "sending" -> {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Enviando",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(13.dp)
            )
        }
        "sent" -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Enviado",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(13.dp)
            )
        }
        "delivered" -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Entregue",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(15.dp)
            )
        }
        "read" -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Lido",
                tint = Color(0xFF34B7F1),
                modifier = Modifier.size(15.dp)
            )
        }
        "failed" -> {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Falha",
                tint = Color.Red,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
