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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import coil.compose.AsyncImage
import com.livrechat.app.data.local.model.ReactionUtils
import com.livrechat.app.ui.camera.CustomCameraDialog
import com.livrechat.app.ui.chat.EmojiStickerBottomSheet
import com.livrechat.app.ui.chat.EmojiStickerPanel
import com.livrechat.app.ui.chat.MessageActionMenuDialog
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
import com.livrechat.app.ui.common.UserAvatarView
import androidx.compose.ui.text.style.TextOverflow
import com.livrechat.app.ui.theme.OfflineGray
import com.livrechat.app.util.MediaFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
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
    val recordingAmplitudes = remember { mutableStateListOf<Int>() }

    val currentFlow by viewModel.messagesFlow.collectAsState()
    val messages by currentFlow.collectAsState(initial = emptyList())
    val isTyping by viewModel.isContactTyping.collectAsState()

    LaunchedEffect(conversationId) {
        viewModel.selectConversation(conversationId, otherUserId)
    }

    LaunchedEffect(messages) {
        audioPlayerHelper.onAudioCompleted = { completedId ->
            val idx = messages.indexOfFirst { it.id == completedId }
            if (idx != -1 && idx < messages.size - 1) {
                val nextAudio = messages.subList(idx + 1, messages.size)
                    .firstOrNull { it.messageType == "audio" && !it.isDeleted }
                if (nextAudio != null) {
                    audioPlayerHelper.playAudio(nextAudio.id, nextAudio.content)
                }
            }
        }
    }

    DisposableEffect(conversationId) {
        onDispose {
            typingDebounceJob?.cancel()
            recordingJob?.cancel()
            audioPlayerHelper.stop()
            if (isRecording) {
                audioRecorderHelper.cancelRecording()
                recordingAmplitudes.clear()
            }
            viewModel.sendTyping(false, otherUserId)
            viewModel.unselectConversation()
        }
    }

    var initialScrollCompleted by remember { mutableStateOf(false) }
    var unreadBelowCount by remember { mutableIntStateOf(0) }
    var showScrollToBottomButton by remember { mutableStateOf(false) }

    val isNearBottom by remember {
        derivedStateOf {
            val totalItems = messages.size
            if (totalItems <= 1) return@derivedStateOf true
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= totalItems - 2
        }
    }

    LaunchedEffect(isNearBottom) {
        if (isNearBottom) {
            showScrollToBottomButton = false
            unreadBelowCount = 0
        }
    }

    val app = remember { com.livrechat.app.LivreChatApp.instance }
    val prefs = remember { app.preferencesManager }
    val onlineUsers by app.connectionManager.onlineUsers.collectAsState()
    val isContactOnline = onlineUsers[otherUserId] == true

    LaunchedEffect(otherUserId) {
        if (otherUserId.isNotBlank()) {
            app.connectionManager.queryUserStatus(otherUserId)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect

        if (!initialScrollCompleted) {
            val firstUnreadIndex = messages.indexOfFirst { !it.isOutgoing && it.status != "read" }
            if (firstUnreadIndex > 0) {
                listState.scrollToItem(firstUnreadIndex)
                showScrollToBottomButton = true
                unreadBelowCount = messages.size - firstUnreadIndex
            } else {
                listState.scrollToItem(messages.size - 1)
            }
            initialScrollCompleted = true
        } else {
            val lastMsg = messages.lastOrNull()
            if (lastMsg?.isOutgoing == true) {
                listState.animateScrollToItem(messages.size - 1)
                showScrollToBottomButton = false
                unreadBelowCount = 0
            } else {
                if (prefs.conversationSounds && lastMsg != null) {
                    try {
                        val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 65)
                        toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 120)
                    } catch (e: Exception) {}
                }
                if (isNearBottom) {
                    listState.animateScrollToItem(messages.size - 1)
                    showScrollToBottomButton = false
                    unreadBelowCount = 0
                } else {
                    unreadBelowCount++
                    showScrollToBottomButton = true
                }
            }
        }
    }

    val currentConv by remember(conversationId) {
        app.database.conversationDao().getConversationByIdFlow(conversationId)
    }.collectAsState(initial = null)
    var isConvLocked by remember { mutableStateOf(prefs.isConversationLocked(conversationId)) }
    var showMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showContactProfile by remember { mutableStateOf(false) }
    var showCryptoInfoDialog by remember { mutableStateOf(false) }
    var selectedMessageForAction by remember { mutableStateOf<MessageEntity?>(null) }
    var messageActionTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var replyingToMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var showCameraDialog by remember { mutableStateOf(false) }
    var showEmojiStickerSheet by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputFocusRequester = remember { FocusRequester() }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var viewingImageBase64 by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val input = context.contentResolver.openInputStream(uri)
                    val original = BitmapFactory.decodeStream(input)
                    input?.close()
                    if (original != null) {
                        val maxDim = 1280
                        val width = original.width
                        val height = original.height
                        val scale = if (width > height) maxDim.toFloat() / width else maxDim.toFloat() / height
                        val scaled = if (scale < 1f) {
                            Bitmap.createScaledBitmap(original, (width * scale).toInt(), (height * scale).toInt(), true)
                        } else original

                        val baos = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        val bytes = baos.toByteArray()
                        val localFile = MediaFileManager.saveBytesToLocalFile(context, bytes, "photo_${System.currentTimeMillis()}.jpg")
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val dataUri = "data:image/jpeg;base64,$b64"
                        viewModel.sendMediaMessage(
                            conversationId = conversationId,
                            content = localFile.absolutePath,
                            networkPayload = dataUri,
                            messageType = "image",
                            targetUserId = otherUserId
                        )
                    }
                } catch (e: Exception) {
                    Log.e("ChatScreen", "Erro ao enviar foto", e)
                }
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val (fileName, fileSize) = getFileNameAndSize(context, uri)
                    if (fileSize > 8 * 1024 * 1024) {
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "O vídeo deve ter no máximo 8MB para envio rápido", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    val input = context.contentResolver.openInputStream(uri) ?: return@launch
                    val localFile = MediaFileManager.saveStreamToLocalFile(context, input, fileName)
                    input.close()
                    val bytes = localFile.readBytes()
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val dataUri = "data:video/mp4;base64,$b64"
                    viewModel.sendMediaMessage(
                        conversationId = conversationId,
                        content = localFile.absolutePath,
                        networkPayload = dataUri,
                        messageType = "video",
                        fileName = fileName,
                        fileSize = localFile.length(),
                        targetUserId = otherUserId
                    )
                } catch (e: Exception) {
                    Log.e("ChatScreen", "Erro ao enviar vídeo", e)
                }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val (fileName, fileSize) = getFileNameAndSize(context, uri)
                    if (fileSize > 8 * 1024 * 1024) {
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "O arquivo deve ter no máximo 8MB para envio rápido", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    val input = context.contentResolver.openInputStream(uri) ?: return@launch
                    val localFile = MediaFileManager.saveStreamToLocalFile(context, input, fileName)
                    input.close()
                    val bytes = localFile.readBytes()
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val dataUri = "data:application/octet-stream;base64,$b64"
                    viewModel.sendMediaMessage(
                        conversationId = conversationId,
                        content = localFile.absolutePath,
                        networkPayload = dataUri,
                        messageType = "file",
                        fileName = fileName,
                        fileSize = localFile.length(),
                        targetUserId = otherUserId
                    )
                } catch (e: Exception) {
                    Log.e("ChatScreen", "Erro ao enviar arquivo", e)
                }
            }
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val (fileName, fileSize) = getFileNameAndSize(context, uri)
                    if (fileSize > 8 * 1024 * 1024) {
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "O áudio deve ter no máximo 8MB", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    val input = context.contentResolver.openInputStream(uri) ?: return@launch
                    val localFile = MediaFileManager.saveStreamToLocalFile(context, input, fileName)
                    input.close()
                    val bytes = localFile.readBytes()
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val dataUri = "data:audio/mp3;base64,$b64"
                    viewModel.sendAudioMessage(
                        conversationId = conversationId,
                        audioBase64 = dataUri,
                        durationSeconds = 0,
                        targetUserId = otherUserId,
                        localAudioFilePath = localFile.absolutePath
                    )
                } catch (e: Exception) {
                    Log.e("ChatScreen", "Erro ao enviar áudio", e)
                }
            }
        }
    }

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

    if (showContactProfile) {
        ContactProfileDialog(
            conversationId = conversationId,
            otherUserId = otherUserId,
            otherDisplayName = otherDisplayName,
            otherUsername = currentConv?.otherUsername ?: "",
            otherAvatarUrl = currentConv?.otherAvatarUrl,
            messages = messages,
            isLocked = isConvLocked,
            onDismiss = { showContactProfile = false },
            onStartCall = {
                onStartCall(
                    otherUserId,
                    otherDisplayName,
                    currentConv?.otherUsername ?: "",
                    currentConv?.otherAvatarUrl
                )
            },
            onToggleLock = { newLocked ->
                isConvLocked = newLocked
                prefs.setConversationLocked(conversationId, newLocked)
            },
            onClearChat = {
                viewModel.clearConversation(conversationId, otherUserId)
            },
            onBlockContact = {
                scope.launch(Dispatchers.IO) {
                    try {
                        app.apiClient.apiService.blockUser(mapOf("blockedUserId" to otherUserId, "targetUserId" to otherUserId))
                    } catch (e: Exception) {}
                }
            },
            onViewMedia = { viewingImageBase64 = it }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val isGroup = currentConv?.isGroup == true
                    val headerTitle = if (isGroup) (currentConv?.groupTitle ?: "Grupo") else otherDisplayName
                    val headerAvatar = if (isGroup) currentConv?.groupAvatar else currentConv?.otherAvatarUrl

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showContactProfile = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatarView(
                            avatarUrl = headerAvatar,
                            displayName = headerTitle,
                            size = 40.dp,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = headerTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isGroup) "Grupo"
                                       else if (isTyping) "digitando..."
                                       else if (isContactOnline) "Online"
                                       else "Visto por último recentemente",
                                fontSize = 12.sp,
                                color = if (isTyping) BlueAccent
                                        else if (isContactOnline && !isGroup) Color(0xFF4CAF50)
                                        else Color.Gray,
                                fontWeight = if (isTyping) FontWeight.Bold else FontWeight.Normal
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
                        onStartCall(
                            otherUserId,
                            otherDisplayName,
                            currentConv?.otherUsername ?: "",
                            currentConv?.otherAvatarUrl
                        )
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
                                text = { Text("Dados do contato") },
                                onClick = {
                                    showMenu = false
                                    showContactProfile = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = BluePrimary)
                                }
                            )
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Quoted message preview if replying
                    if (replyingToMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(36.dp)
                                        .background(BluePrimary, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (replyingToMessage!!.isOutgoing) "Você" else otherDisplayName,
                                        color = BluePrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = when (replyingToMessage!!.messageType) {
                                            "audio" -> "🎤 Mensagem de voz"
                                            "image" -> "📷 Foto"
                                            "video" -> "🎥 Vídeo"
                                            "sticker" -> "📑 Figurinha"
                                            "file" -> "📄 Documento"
                                            else -> replyingToMessage!!.content
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp
                                    )
                                }
                                IconButton(
                                    onClick = { replyingToMessage = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancelar resposta", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

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
                                recordingAmplitudes.clear()
                                Toast.makeText(context, "Gravação cancelada", Toast.LENGTH_SHORT).show()
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = DangerRed.copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Cancelar", tint = DangerRed)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Duration label with red dot & live amplitude visualizer
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
                            Spacer(modifier = Modifier.width(8.dp))
                            // Live bouncing bars
                            Row(
                                modifier = Modifier.height(20.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val recent = recordingAmplitudes.takeLast(14)
                                for (a in recent) {
                                    val barH = ((a / 12000f).coerceIn(0.2f, 1f) * 18).dp
                                    Box(
                                        modifier = Modifier
                                            .width(2.5.dp)
                                            .height(barH)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(DangerRed.copy(alpha = 0.85f))
                                    )
                                }
                            }
                        }

                        // Send Audio Button
                        IconButton(
                            onClick = {
                                recordingJob?.cancel()
                                val recordedFile = audioRecorderHelper.stopRecording()
                                val seconds = recordingSeconds
                                val amps = recordingAmplitudes.toList()
                                isRecording = false
                                recordingSeconds = 0
                                recordingAmplitudes.clear()

                                if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val bytes = recordedFile.readBytes()
                                            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                            val audioUri = "data:audio/mp4;base64,$base64"
                                            val bars = processAmplitudesToBars(amps, 28)
                                            val encodedWaveform = encodeWaveform(bars)
                                            viewModel.sendAudioMessage(
                                                conversationId = conversationId,
                                                audioBase64 = audioUri,
                                                durationSeconds = seconds.coerceAtLeast(1),
                                                targetUserId = otherUserId,
                                                waveform = encodedWaveform
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
                                    if (now - lastTypingSentTime > 1500L) {
                                        lastTypingSentTime = now
                                        viewModel.sendTyping(true, otherUserId)
                                    }
                                    typingDebounceJob?.cancel()
                                    typingDebounceJob = scope.launch {
                                        delay(1500)
                                        viewModel.sendTyping(false, otherUserId)
                                        lastTypingSentTime = 0L
                                    }
                                } else {
                                    typingDebounceJob?.cancel()
                                    viewModel.sendTyping(false, otherUserId)
                                    lastTypingSentTime = 0L
                                }
                            },
                            placeholder = { Text("Mensagem", fontSize = 15.sp) },
                            leadingIcon = {
                                IconButton(onClick = {
                                    if (showEmojiStickerSheet) {
                                        showEmojiStickerSheet = false
                                        inputFocusRequester.requestFocus()
                                        keyboardController?.show()
                                    } else {
                                        keyboardController?.hide()
                                        showEmojiStickerSheet = true
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (showEmojiStickerSheet) Icons.Default.Keyboard else Icons.Default.SentimentSatisfied,
                                        contentDescription = if (showEmojiStickerSheet) "Teclado" else "Emojis e Figurinhas",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { showAttachmentSheet = true }) {
                                        Icon(
                                            imageVector = Icons.Default.AttachFile,
                                            contentDescription = "Anexar",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (textInput.isBlank()) {
                                        IconButton(onClick = { showCameraDialog = true }) {
                                            Icon(
                                                imageVector = Icons.Default.CameraAlt,
                                                contentDescription = "Câmera",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                                .focusRequester(inputFocusRequester)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused && showEmojiStickerSheet) {
                                        showEmojiStickerSheet = false
                                    }
                                }
                        )

                        if (textInput.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    if (textInput.isNotBlank()) {
                                        typingDebounceJob?.cancel()
                                        val replyId = replyingToMessage?.id
                                        viewModel.sendMessage(
                                            conversationId = conversationId,
                                            text = textInput.trim(),
                                            replyToId = replyId,
                                            targetUserId = otherUserId
                                        )
                                        textInput = ""
                                        replyingToMessage = null
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
                                        recordingAmplitudes.clear()
                                        recordingJob?.cancel()
                                        recordingJob = scope.launch {
                                            var tick = 0
                                            while (isActive) {
                                                delay(100)
                                                val amp = audioRecorderHelper.getMaxAmplitude()
                                                recordingAmplitudes.add(amp)
                                                tick++
                                                if (tick % 10 == 0) {
                                                    recordingSeconds++
                                                }
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

                    AnimatedVisibility(
                        visible = showEmojiStickerSheet,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        EmojiStickerPanel(
                            onDismiss = { showEmojiStickerSheet = false },
                            onEmojiSelected = { emoji ->
                                textInput += emoji
                            },
                            onSendSticker = { stickerContent ->
                                viewModel.sendSticker(conversationId, stickerContent, targetUserId = otherUserId)
                            },
                            onBackspace = {
                                if (textInput.isNotEmpty()) {
                                    textInput = textInput.dropLast(1)
                                }
                            }
                        )
                    }
                }
            }
        }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        audioPlayerHelper = audioPlayerHelper,
                        onLongPress = { messageActionTarget = it },
                        onSwipeToReply = {
                            replyingToMessage = it
                            inputFocusRequester.requestFocus()
                            keyboardController?.show()
                        },
                        onReactionClick = { viewModel.toggleReaction(it.id, "❤️") },
                        onImageClick = { viewingImageBase64 = it },
                        isGroup = currentConv?.isGroup == true
                    )
                }
            }

            // Floating "Scroll to Bottom" button when user scrolled up and new messages exist
            AnimatedVisibility(
                visible = showScrollToBottomButton,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(messages.size - 1)
                            showScrollToBottomButton = false
                            unreadBelowCount = 0
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = BluePrimary,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(6.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Descer para as novas mensagens",
                            modifier = Modifier.size(24.dp)
                        )
                        if (unreadBelowCount > 0) {
                            Badge(
                                containerColor = BluePrimary,
                                contentColor = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-6).dp)
                            ) {
                                Text(text = "$unreadBelowCount", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    "Compartilhar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    AttachmentItem(
                        icon = Icons.Default.Description,
                        label = "Documento",
                        color = Color(0xFF7F66FF)
                    ) {
                        showAttachmentSheet = false
                        filePickerLauncher.launch("*/*")
                    }

                    AttachmentItem(
                        icon = Icons.Default.CameraAlt,
                        label = "Câmera",
                        color = Color(0xFFE91E63)
                    ) {
                        showAttachmentSheet = false
                        showCameraDialog = true
                    }

                    AttachmentItem(
                        icon = Icons.Default.PhotoLibrary,
                        label = "Galeria",
                        color = Color(0xFF9C27B0)
                    ) {
                        showAttachmentSheet = false
                        photoPickerLauncher.launch("image/*")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    AttachmentItem(
                        icon = Icons.Default.Headphones,
                        label = "Áudio",
                        color = Color(0xFFFF9800)
                    ) {
                        showAttachmentSheet = false
                        audioPickerLauncher.launch("audio/*")
                    }

                    AttachmentItem(
                        icon = Icons.Default.Videocam,
                        label = "Vídeo",
                        color = Color(0xFF00B0FF)
                    ) {
                        showAttachmentSheet = false
                        videoPickerLauncher.launch("video/*")
                    }

                    Spacer(modifier = Modifier.width(60.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showCameraDialog) {
        CustomCameraDialog(
            onDismiss = { showCameraDialog = false },
            onMediaCaptured = { file, mediaType, caption ->
                scope.launch(Dispatchers.IO) {
                    try {
                        val bytes = file.readBytes()
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val isVideo = mediaType == "video"
                        val dataUri = if (isVideo) "data:video/mp4;base64,$b64" else "data:image/jpeg;base64,$b64"
                        viewModel.sendMediaMessage(
                            conversationId = conversationId,
                            content = file.absolutePath,
                            networkPayload = dataUri,
                            messageType = if (isVideo) "video" else "image",
                            fileName = if (isVideo) file.name else null,
                            fileSize = file.length(),
                            caption = caption.ifBlank { null },
                            targetUserId = otherUserId
                        )
                    } catch (e: Exception) {
                        Log.e("ChatScreen", "Erro ao enviar mídia da câmera", e)
                    }
                }
            }
        )
    }


    if (messageActionTarget != null) {
        val target = messageActionTarget!!
        MessageActionMenuDialog(
            message = target,
            onDismiss = { messageActionTarget = null },
            onReact = { emoji ->
                viewModel.toggleReaction(target.id, emoji)
                messageActionTarget = null
            },
            onReply = {
                replyingToMessage = target
                messageActionTarget = null
            },
            onForward = {
                messageActionTarget = null
                try {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, target.content)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Encaminhar via"))
                } catch (e: Exception) {}
            },
            onDelete = {
                selectedMessageForAction = target
                messageActionTarget = null
            },
            onStar = {
                Toast.makeText(context, "Mensagem favoritada ⭐", Toast.LENGTH_SHORT).show()
                messageActionTarget = null
            },
            onPin = {
                Toast.makeText(context, "Mensagem fixada 📌", Toast.LENGTH_SHORT).show()
                messageActionTarget = null
            },
            onInfo = {
                showCryptoInfoDialog = true
                messageActionTarget = null
            }
        )
    }

    if (viewingImageBase64 != null) {
        FullscreenImageViewerDialog(
            base64Content = viewingImageBase64!!,
            onDismiss = { viewingImageBase64 = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    audioPlayerHelper: AudioPlayerHelper,
    onLongPress: (MessageEntity) -> Unit,
    onSwipeToReply: (MessageEntity) -> Unit,
    onReactionClick: (MessageEntity) -> Unit,
    onImageClick: (String) -> Unit,
    isGroup: Boolean = false
) {
    val isOutgoing = message.isOutgoing
    val alignment = if (isOutgoing) Alignment.End else Alignment.Start
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "swipe_reply"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragOffset >= 115f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSwipeToReply(message)
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        if (dragAmount > 0 || dragOffset > 0) {
                            dragOffset = (dragOffset + dragAmount * 0.7f).coerceIn(0f, 130f)
                        }
                    }
                )
            }
    ) {
        // Reply circle icon appears on left while dragging right
        if (animatedOffset > 8f) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = (animatedOffset * 0.35f - 18f).dp)
                    .clip(CircleShape)
                    .background(BluePrimary.copy(alpha = (animatedOffset / 115f).coerceIn(0.2f, 1f))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Responder",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = animatedOffset.dp),
            horizontalAlignment = alignment
        ) {
            if (message.messageType == "sticker") {
                // Sticker without speech bubble container
                Column(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { onLongPress(message) }
                        )
                ) {
                    StickerMessageBubbleContent(
                        message = message,
                        isOutgoing = isOutgoing
                    )
                    Row(
                        modifier = Modifier.align(Alignment.End).padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.createdAt)),
                            color = OfflineGray,
                            fontSize = 11.sp
                        )
                        if (isOutgoing && !message.isDeleted) {
                            Spacer(modifier = Modifier.width(4.dp))
                            MessageStatusIcon(status = message.status)
                        }
                    }
                }
            } else {
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
                        // Sender identifier in group conversation if incoming
                        if (isGroup && !isOutgoing) {
                            Text(
                                text = message.senderId,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = BluePrimary,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        // Render quoted reply banner inside bubble if replying
                        if (!message.replyToContent.isNullOrBlank() || message.replyToId != null) {
                            Surface(
                                color = if (isOutgoing) Color.Black.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(28.dp)
                                            .background(if (isOutgoing) Color.White else BluePrimary, RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Resposta",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (isOutgoing) Color.White.copy(alpha = 0.9f) else BluePrimary
                                        )
                                        Text(
                                            text = message.replyToContent ?: "Mensagem respondida",
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isOutgoing) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

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
                        } else if (message.messageType == "image" || message.content.startsWith("data:image")) {
                            ImageMessageBubbleContent(
                                message = message,
                                isOutgoing = isOutgoing,
                                onImageClick = { onImageClick(message.content) }
                            )
                        } else if (message.messageType == "video" || message.content.startsWith("data:video")) {
                            VideoMessageBubbleContent(
                                message = message,
                                isOutgoing = isOutgoing,
                                context = context
                            )
                        } else if (message.messageType == "file" || message.content.startsWith("data:application")) {
                            FileMessageBubbleContent(
                                message = message,
                                isOutgoing = isOutgoing,
                                context = context
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

            // Floating Reactions Pill attached to bottom corner of bubble
            val reactions = remember(message.reactions) {
                ReactionUtils.parseReactions(message.reactions)
            }
            if (reactions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    tonalElevation = 4.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .offset(y = (-6).dp)
                        .padding(horizontal = 4.dp)
                        .clickable { onReactionClick(message) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        val uniqueEmojis = reactions.map { it.emoji }.distinct()
                        Text(text = uniqueEmojis.joinToString(""), fontSize = 13.sp)
                        if (reactions.size > 1) {
                            Text(
                                text = "${reactions.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StickerMessageBubbleContent(
    message: MessageEntity,
    isOutgoing: Boolean
) {
    val model = if (message.content.startsWith("http") || message.content.startsWith("data:")) {
        message.content
    } else {
        File(message.content)
    }

    Box(
        modifier = Modifier
            .size(140.dp)
            .padding(4.dp)
    ) {
        AsyncImage(
            model = model,
            contentDescription = "Figurinha",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

fun processAmplitudesToBars(raw: List<Int>, targetCount: Int = 28): List<Int> {
    if (raw.isEmpty()) {
        return listOf(8, 14, 22, 12, 28, 18, 10, 24, 16, 26, 30, 14, 20, 26, 12, 18, 28, 16, 10, 22, 30, 20, 14, 18, 26, 16, 10, 14)
    }
    val maxAmp = raw.maxOrNull()?.toFloat()?.coerceAtLeast(1200f) ?: 1200f
    val bars = ArrayList<Int>(targetCount)
    val chunkSize = raw.size.toFloat() / targetCount.toFloat()
    for (i in 0 until targetCount) {
        val startIdx = (i * chunkSize).toInt().coerceIn(0, raw.size - 1)
        val endIdx = ((i + 1) * chunkSize).toInt().coerceIn(startIdx + 1, raw.size)
        val sub = raw.subList(startIdx, endIdx)
        val avg = if (sub.isNotEmpty()) sub.average().toFloat() else 0f
        val ratio = (avg / maxAmp).coerceIn(0f, 1f)
        val height = (6 + (ratio * 24)).toInt().coerceIn(6, 30)
        bars.add(height)
    }
    return bars
}

fun encodeWaveform(bars: List<Int>): String {
    val sb = StringBuilder("w:")
    for (b in bars.take(28)) {
        val level = (b - 6).coerceIn(0, 25)
        sb.append(('A'.code + level).toChar())
    }
    return sb.toString()
}

fun decodeWaveform(encoded: String?): List<Int> {
    if (encoded != null && encoded.startsWith("w:")) {
        val chars = encoded.removePrefix("w:")
        val list = chars.map { ch ->
            val level = (ch - 'A').coerceIn(0, 25)
            6 + level
        }
        if (list.isNotEmpty()) return list
    }
    if (encoded != null && encoded.startsWith("wf:")) {
        val parsed = encoded.removePrefix("wf:").split(",").mapNotNull { it.toIntOrNull() }
        if (parsed.isNotEmpty()) return parsed
    }
    return listOf(8, 14, 22, 12, 28, 18, 10, 24, 16, 26, 30, 14, 20, 26, 12, 18, 28, 16, 10, 22, 30, 20, 14, 18, 26, 16, 10, 14)
}

@Composable
fun AudioWaveformVisualizer(
    bars: List<Int>,
    progress: Float,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier
) {
    val barHeights = if (bars.isNotEmpty()) bars else listOf(8, 14, 22, 12, 28, 18, 10, 24, 16, 26, 30, 14, 20, 26, 12, 18, 28, 16, 10, 22, 30, 20, 14, 18, 26, 16, 10, 14)
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

    val waveformBars = remember(message.iv, message.id) {
        if (!message.iv.isNullOrBlank()) {
            decodeWaveform(message.iv)
        } else {
            val hash = kotlin.math.abs(message.id.hashCode())
            (0 until 28).map { i ->
                val pseudo = ((hash / (i + 1)) * 37 + i * 19) % 24
                6 + kotlin.math.abs(pseudo)
            }
        }
    }

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
                bars = waveformBars,
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

@Composable
fun AttachmentItem(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = color,
            modifier = Modifier.size(54.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ImageMessageBubbleContent(
    message: MessageEntity,
    isOutgoing: Boolean,
    onImageClick: () -> Unit
) {
    var decodedBitmap by remember(message.content) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(message.content) {
        withContext(Dispatchers.IO) {
            try {
                if (MediaFileManager.isLocalFilePath(message.content)) {
                    val local = java.io.File(message.content.removePrefix("file://"))
                    if (local.exists()) {
                        val bmp = BitmapFactory.decodeFile(local.absolutePath)
                        withContext(Dispatchers.Main) {
                            decodedBitmap = bmp
                        }
                        return@withContext
                    }
                }
                val clean = if (message.content.contains(",")) message.content.substringAfter(",") else message.content
                val bytes = Base64.decode(clean, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                withContext(Dispatchers.Main) {
                    decodedBitmap = bmp
                }
            } catch (e: Exception) {
                decodedBitmap = null
            }
        }
    }

    Column {
        if (decodedBitmap != null) {
            Image(
                bitmap = decodedBitmap!!.asImageBitmap(),
                contentDescription = "Foto",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onImageClick),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = if (isOutgoing) Color.White else BluePrimary)
            }
        }

        if (!message.iv.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message.iv,
                color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun VideoMessageBubbleContent(
    message: MessageEntity,
    isOutgoing: Boolean,
    context: Context
) {
    val parts = remember(message.iv) { message.iv?.split("|") ?: emptyList() }
    val fileName = parts.getOrNull(0)?.ifBlank { null } ?: "video_${message.id.take(8)}.mp4"
    val fileSize = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    val caption = parts.getOrNull(2)?.ifBlank { null }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable {
                    openMediaFile(context, message.content, fileName, "video/mp4")
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Reproduzir Vídeo",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (fileSize > 0) "Vídeo • ${formatFileSize(fileSize)}" else "Vídeo",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }

        if (!caption.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = caption,
                color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun FileMessageBubbleContent(
    message: MessageEntity,
    isOutgoing: Boolean,
    context: Context
) {
    val parts = remember(message.iv) { message.iv?.split("|") ?: emptyList() }
    val fileName = parts.getOrNull(0)?.ifBlank { null } ?: "arquivo_${message.id.take(8)}"
    val fileSize = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    val ext = fileName.substringAfterLast(".", "").uppercase()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isOutgoing) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val mime = when (ext.lowercase()) {
                    "pdf" -> "application/pdf"
                    "doc", "docx" -> "application/msword"
                    "xls", "xlsx" -> "application/vnd.ms-excel"
                    "zip" -> "application/zip"
                    "apk" -> "application/vnd.android.package-archive"
                    "txt" -> "text/plain"
                    else -> "*/*"
                }
                openMediaFile(context, message.content, fileName, mime)
            }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (ext == "PDF") Color(0xFFF44336) else if (ext in listOf("ZIP", "RAR")) Color(0xFFFF9800) else BluePrimary,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        if (fileSize > 0) formatFileSize(fileSize) else null,
                        ext.ifBlank { null }
                    ).joinToString(" • "),
                    fontSize = 11.sp,
                    color = if (isOutgoing) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Abrir",
                tint = if (isOutgoing) Color.White else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun FullscreenImageViewerDialog(
    base64Content: String,
    onDismiss: () -> Unit
) {
    var decodedBitmap by remember(base64Content) { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(base64Content) {
        withContext(Dispatchers.IO) {
            try {
                if (MediaFileManager.isLocalFilePath(base64Content)) {
                    val local = java.io.File(base64Content.removePrefix("file://"))
                    if (local.exists()) {
                        val bmp = BitmapFactory.decodeFile(local.absolutePath)
                        withContext(Dispatchers.Main) {
                            decodedBitmap = bmp
                        }
                        return@withContext
                    }
                }
                val clean = if (base64Content.contains(",")) base64Content.substringAfter(",") else base64Content
                val bytes = Base64.decode(clean, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                withContext(Dispatchers.Main) {
                    decodedBitmap = bmp
                }
            } catch (e: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (decodedBitmap != null) {
                Image(
                    bitmap = decodedBitmap!!.asImageBitmap(),
                    contentDescription = "Foto em tela cheia",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                }

                IconButton(onClick = {
                    openMediaFile(context, base64Content, "foto_${System.currentTimeMillis()}.jpg", "image/jpeg")
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Compartilhar", tint = Color.White)
                }
            }
        }
    }
}

fun getFileNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
    var name = "arquivo_${System.currentTimeMillis()}"
    var size = 0L
    try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) name = it.getString(nameIndex) ?: name
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) size = it.getLong(sizeIndex)
            }
        }
    } catch (e: Exception) {}
    return Pair(name, size)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.getDefault(), "%.0f KB", kb)
        else -> "$bytes B"
    }
}

fun openMediaFile(context: Context, base64Content: String, fileName: String, mimeType: String) {
    try {
        val targetFile: File = if (MediaFileManager.isLocalFilePath(base64Content)) {
            val local = java.io.File(base64Content.removePrefix("file://"))
            if (local.exists()) local else {
                val cleanBase64 = if (base64Content.contains(",")) base64Content.substringAfter(",") else base64Content
                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                val cacheFile = File(context.cacheDir, fileName)
                FileOutputStream(cacheFile).use { it.write(bytes) }
                cacheFile
            }
        } else {
            val cleanBase64 = if (base64Content.contains(",")) base64Content.substringAfter(",") else base64Content
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val cacheFile = File(context.cacheDir, fileName)
            FileOutputStream(cacheFile).use { it.write(bytes) }
            cacheFile
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Abrir com..."))
    } catch (e: Exception) {
        Toast.makeText(context, "Não foi possível abrir o arquivo: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
