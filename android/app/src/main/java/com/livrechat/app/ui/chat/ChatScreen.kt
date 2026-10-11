package com.livrechat.app.ui.chat

import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.font.FontStyle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrechat.app.data.local.entity.MessageEntity
import com.livrechat.app.media.AudioPlayerHelper
import com.livrechat.app.media.AudioRecorderHelper
import com.livrechat.app.ui.theme.BlueAccent
import com.livrechat.app.ui.theme.BluePrimary
import com.livrechat.app.ui.theme.DangerRed
import com.livrechat.app.ui.theme.OfflineGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val context = LocalContext.current
    val audioRecorderHelper = remember { AudioRecorderHelper(context) }
    val audioPlayerHelper = remember { AudioPlayerHelper(context) }

    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var typingDebounceJob by remember { mutableStateOf<Job?>(null) }
    var lastTypingSentTime by remember { mutableLongStateOf(0L) }

    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var recordingJob by remember { mutableStateOf<Job?>(null) }

    val currentFlow by viewModel.messagesFlow.collectAsState()
    val messages by currentFlow.collectAsState(initial = emptyList())
    val isTyping by viewModel.isContactTyping.collectAsState()

    LaunchedEffect(conversationId) {
        viewModel.selectConversation(conversationId, otherUserId)
    }

    DisposableEffect(conversationId) {
        onDispose {
            typingDebounceJob?.cancel()
            recordingJob?.cancel()
            audioPlayerHelper.stop()
            if (isRecording) {
                audioRecorderHelper.cancelRecording()
            }
            viewModel.sendTyping(false, otherUserId)
            viewModel.unselectConversation()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val prefs = remember { com.livrechat.app.LivreChatApp.instance.preferencesManager }
    var isConvLocked by remember { mutableStateOf(prefs.isConversationLocked(conversationId)) }
    var showMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showCryptoInfoDialog by remember { mutableStateOf(false) }
    var selectedMessageForAction by remember { mutableStateOf<MessageEntity?>(null) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Limpar conversa?", fontWeight = FontWeight.Bold) },
            text = { Text("Todas as mensagens desta conversa serão apagadas permanentemente.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearConversation(conversationId, otherUserId)
                    showClearDialog = false
                    Toast.makeText(context, "Conversa limpa", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Limpar mensagens", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showCryptoInfoDialog) {
        AlertDialog(
            onDismissRequest = { showCryptoInfoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = BluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Criptografia de Ponta a Ponta", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "As mensagens, áudios e chamadas desta conversa são protegidas com criptografia de nível militar (AES-256-GCM + PBKDF2/HMAC-SHA256).\n\n" +
                    "Ninguém fora desta conversa, nem mesmo o LivreChat ou provedores de rede, podem ler suas mensagens ou ouvir suas chamadas de voz.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showCryptoInfoDialog = false }) {
                    Text("Entendido", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (selectedMessageForAction != null) {
        val msg = selectedMessageForAction!!
        AlertDialog(
            onDismissRequest = { selectedMessageForAction = null },
            title = { Text("Apagar mensagem?", fontWeight = FontWeight.Bold) },
            text = { Text("Escolha como deseja apagar a mensagem:") },
            confirmButton = {
                if (msg.isOutgoing && !msg.isDeleted) {
                    TextButton(onClick = {
                        viewModel.deleteMessage(msg.id, conversationId, deleteForEveryone = true, targetUserId = otherUserId)
                        selectedMessageForAction = null
                        Toast.makeText(context, "Mensagem apagada para todos", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Apagar para todos", color = DangerRed, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.deleteMessage(msg.id, conversationId, deleteForEveryone = false)
                        selectedMessageForAction = null
                        Toast.makeText(context, "Mensagem apagada para você", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Apagar para mim")
                    }
                    TextButton(onClick = { selectedMessageForAction = null }) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable { showCryptoInfoDialog = true }
                    ) {
                        Text(
                            text = otherDisplayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!isTyping) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                            }
                            Text(
                                text = if (isTyping) "digitando..." else "Criptografia E2E",
                                fontSize = 12.sp,
                                color = if (isTyping) BlueAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
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

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Opções")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isConvLocked) "Desbloquear conversa" else "Bloquear conversa") },
                                onClick = {
                                    val newLocked = !isConvLocked
                                    prefs.setConversationLocked(conversationId, newLocked)
                                    isConvLocked = newLocked
                                    showMenu = false
                                    Toast.makeText(
                                        context,
                                        if (newLocked) "Conversa bloqueada com biometria" else "Conversa desbloqueada",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isConvLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Limpar conversa") },
                                onClick = {
                                    showMenu = false
                                    showClearDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = DangerRed)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Segurança e Criptografia") },
                                onClick = {
                                    showMenu = false
                                    showCryptoInfoDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Shield, contentDescription = null, tint = BluePrimary)
                                }
                            )
                        }
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
                if (isRecording) {
                    // Audio Recording Mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Trash button
                        IconButton(
                            onClick = {
                                recordingJob?.cancel()
                                audioRecorderHelper.cancelRecording()
                                isRecording = false
                                recordingSeconds = 0
                                Toast.makeText(context, "Gravação cancelada", Toast.LENGTH_SHORT).show()
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = DangerRed.copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Cancelar", tint = DangerRed)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Duration label with red dot
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(DangerRed, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gravando: %02d:%02d".format(recordingSeconds / 60, recordingSeconds % 60),
                                fontWeight = FontWeight.Bold,
                                color = DangerRed,
                                fontSize = 15.sp
                            )
                        }

                        // Send Audio Button
                        IconButton(
                            onClick = {
                                recordingJob?.cancel()
                                val recordedFile = audioRecorderHelper.stopRecording()
                                val seconds = recordingSeconds
                                isRecording = false
                                recordingSeconds = 0

                                if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val bytes = recordedFile.readBytes()
                                            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                            val audioUri = "data:audio/mp4;base64,$base64"
                                            viewModel.sendAudioMessage(
                                                conversationId = conversationId,
                                                audioBase64 = audioUri,
                                                durationSeconds = seconds.coerceAtLeast(1),
                                                targetUserId = otherUserId
                                            )
                                        } catch (e: Exception) {
                                            Log.e("ChatScreen", "Erro ao processar áudio", e)
                                        }
                                    }
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = BluePrimary),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar Áudio",
                                tint = Color.White
                            )
                        }
                    }
                } else {
                    // Standard Text & Mic Mode
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
                                    val now = System.currentTimeMillis()
                                    if (now - lastTypingSentTime > 2000L) {
                                        lastTypingSentTime = now
                                        viewModel.sendTyping(true, otherUserId)
                                    }
                                    typingDebounceJob?.cancel()
                                    typingDebounceJob = scope.launch {
                                        delay(3000)
                                        viewModel.sendTyping(false, otherUserId)
                                        lastTypingSentTime = 0L
                                    }
                                } else {
                                    typingDebounceJob?.cancel()
                                    viewModel.sendTyping(false, otherUserId)
                                    lastTypingSentTime = 0L
                                }
                            },
                            placeholder = { Text("Digite uma mensagem...", fontSize = 14.sp) },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )

                        if (textInput.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    if (textInput.isNotBlank()) {
                                        typingDebounceJob?.cancel()
                                        viewModel.sendMessage(conversationId, textInput.trim(), targetUserId = otherUserId)
                                        textInput = ""
                                        viewModel.sendTyping(false, otherUserId)
                                        lastTypingSentTime = 0L
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = BluePrimary),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Enviar",
                                    tint = Color.White
                                )
                            }
                        } else {
                            // Mic Button
                            IconButton(
                                onClick = {
                                    val file = audioRecorderHelper.startRecording()
                                    if (file != null) {
                                        isRecording = true
                                        recordingSeconds = 0
                                        recordingJob?.cancel()
                                        recordingJob = scope.launch {
                                            while (isActive) {
                                                delay(1000)
                                                recordingSeconds++
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Permissão de microfone necessária ou erro ao gravar", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = BluePrimary),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Gravar Áudio",
                                    tint = Color.White
                                )
                            }
                        }
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
                MessageBubble(
                    message = message,
                    audioPlayerHelper = audioPlayerHelper,
                    onLongPress = { selectedMessageForAction = it }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    audioPlayerHelper: AudioPlayerHelper,
    onLongPress: (MessageEntity) -> Unit
) {
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
            modifier = Modifier
                .widthIn(max = 295.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onLongPress(message) }
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (message.isDeleted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = if (isOutgoing) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Esta mensagem foi apagada",
                            fontStyle = FontStyle.Italic,
                            color = if (isOutgoing) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                } else if (message.messageType == "audio") {
                    AudioMessageBubbleContent(
                        message = message,
                        audioPlayerHelper = audioPlayerHelper,
                        isOutgoing = isOutgoing
                    )
                } else {
                    Text(
                        text = message.content,
                        color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp
                    )
                }

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

                    if (isOutgoing && !message.isDeleted) {
                        Spacer(modifier = Modifier.width(4.dp))
                        MessageStatusIcon(status = message.status)
                    }
                }
            }
        }
    }
}

@Composable
fun AudioWaveformVisualizer(
    progress: Float,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier
) {
    val barHeights = remember {
        listOf(8, 14, 22, 12, 28, 18, 10, 24, 16, 26, 30, 14, 20, 26, 12, 18, 28, 16, 10, 22, 30, 20, 14, 18, 26, 16, 10, 14)
    }
    Row(
        modifier = modifier.height(32.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        barHeights.forEachIndexed { index, heightDp ->
            val barRatio = index.toFloat() / barHeights.size.toFloat()
            val isPlayed = progress > barRatio
            val activeColor = if (isOutgoing) Color.White else BluePrimary
            val inactiveColor = if (isOutgoing) Color.White.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(heightDp.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isPlayed) activeColor else inactiveColor)
            )
        }
    }
}

@Composable
fun AudioMessageBubbleContent(
    message: MessageEntity,
    audioPlayerHelper: AudioPlayerHelper,
    isOutgoing: Boolean
) {
    val playingId by audioPlayerHelper.playingMessageId.collectAsState()
    val isPlayingState by audioPlayerHelper.isPlaying.collectAsState()
    val progress by audioPlayerHelper.progress.collectAsState()

    val isThisPlaying = playingId == message.id && isPlayingState

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                audioPlayerHelper.playAudio(message.id, message.content)
            },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (isOutgoing) Color.White.copy(alpha = 0.25f) else BluePrimary.copy(alpha = 0.15f)
            ),
            modifier = Modifier.size(42.dp)
        ) {
            Icon(
                imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isThisPlaying) "Pausar" else "Reproduzir",
                tint = if (isOutgoing) Color.White else BluePrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // WhatsApp-style Waveform Bars
            AudioWaveformVisualizer(
                progress = if (playingId == message.id) progress else 0f,
                isOutgoing = isOutgoing,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (isOutgoing) Color.White.copy(alpha = 0.7f) else BluePrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isThisPlaying) "Reproduzindo..." else "Áudio gravado",
                        fontSize = 11.sp,
                        color = if (isOutgoing) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
