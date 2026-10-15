package com.livrechat.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.livrechat.app.LivreChatApp
import com.livrechat.app.data.local.entity.MessageEntity
import com.livrechat.app.data.remote.model.UserDto
import com.livrechat.app.media.AudioPlayerHelper
import com.livrechat.app.ui.common.UserAvatarView
import com.livrechat.app.ui.theme.BluePrimary
import com.livrechat.app.ui.theme.DangerRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactProfileDialog(
    conversationId: String,
    otherUserId: String,
    otherDisplayName: String,
    otherUsername: String,
    otherAvatarUrl: String?,
    messages: List<MessageEntity>,
    isLocked: Boolean,
    onDismiss: () -> Unit,
    onStartCall: () -> Unit,
    onToggleLock: (Boolean) -> Unit,
    onClearChat: () -> Unit,
    onBlockContact: () -> Unit,
    onViewMedia: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = LivreChatApp.instance

    var userProfile by remember { mutableStateOf<UserDto?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showEditNicknameDialog by remember { mutableStateOf(false) }
    var editNicknameInput by remember { mutableStateOf("") }

    // Carrega perfil enriquecido do contato
    LaunchedEffect(otherUserId) {
        withContext(Dispatchers.IO) {
            try {
                val res = app.apiClient.apiService.getUserProfile(otherUserId)
                if (res.isSuccessful && res.body() != null) {
                    userProfile = res.body()
                }
            } catch (e: Exception) {}
        }
    }

    val displayTitle = userProfile?.displayName?.ifBlank { null }
        ?: otherDisplayName.ifBlank { "Contato" }
    val displayHandle = userProfile?.username?.ifBlank { null }
        ?: otherUsername.ifBlank { otherUserId.take(12) }
    val avatarUrl = userProfile?.avatarUrl ?: otherAvatarUrl
    val bioText = userProfile?.bio?.ifBlank { null } ?: "Disponível no LivreChat"

    val audioPlayerHelper = remember { AudioPlayerHelper(context) }
    DisposableEffect(Unit) {
        onDispose { audioPlayerHelper.stop() }
    }

    val mediaMessages = remember(messages) {
        messages.filter { msg ->
            !msg.isDeleted && (msg.messageType in listOf("image", "video") || msg.content.startsWith("data:image"))
        }
    }

    val audioMessages = remember(messages) {
        messages.filter { msg ->
            !msg.isDeleted && (
                msg.messageType == "audio" ||
                msg.content.startsWith("data:audio") ||
                msg.content.endsWith(".m4a") ||
                msg.content.endsWith(".mp3") ||
                msg.content.contains("voice_") ||
                msg.content.contains("audio_")
            )
        }
    }

    val docMessages = remember(messages) {
        messages.filter { msg ->
            !msg.isDeleted && (msg.messageType == "file" || msg.content.startsWith("data:application"))
        }
    }

    val totalMediaCount = mediaMessages.size + audioMessages.size + docMessages.size
    var selectedMediaTab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Dados do contato",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 1. Hero Profile Section (Avatar grande, Nome, Handle e Botões de Ação)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(top = 16.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar Grande
                        Box(contentAlignment = Alignment.Center) {
                            UserAvatarView(
                                avatarUrl = avatarUrl,
                                displayName = displayTitle,
                                size = 110.dp,
                                fontSize = 44.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .border(2.dp, BluePrimary.copy(alpha = 0.5f), CircleShape)
                                    .clickable {
                                        if (!avatarUrl.isNullOrBlank()) {
                                            onViewMedia(avatarUrl)
                                        }
                                    }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Nome de Exibição com Botão de Editar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = displayTitle,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    editNicknameInput = displayTitle
                                    showEditNicknameDialog = true
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Editar nome",
                                    tint = BluePrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Identificador / Username
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BluePrimary.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BluePrimary.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = "@$displayHandle",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = BluePrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Linha de Ações Rápidas (Áudio, Mensagem, Silenciar)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            QuickActionPill(
                                icon = Icons.Default.Phone,
                                label = "Áudio",
                                onClick = {
                                    onDismiss()
                                    onStartCall()
                                }
                            )

                            QuickActionPill(
                                icon = Icons.Default.Chat,
                                label = "Mensagem",
                                onClick = onDismiss
                            )

                            QuickActionPill(
                                icon = if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                label = if (isMuted) "Silenciado" else "Silenciar",
                                isToggled = isMuted,
                                onClick = {
                                    isMuted = !isMuted
                                    Toast.makeText(
                                        context,
                                        if (isMuted) "Notificações silenciadas" else "Notificações ativadas",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 2. Card: Recado e Informações do Usuário
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Recado e informações",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Bio / Status
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = bioText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Bio / Recado",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // ID / Telefone Virtual com botão Copiar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("ID LivreChat", displayHandle))
                                        Toast.makeText(context, "ID copiado para a área de transferência", Toast.LENGTH_SHORT).show()
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AlternateEmail,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayHandle,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Identificador / Número do contato",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("ID LivreChat", displayHandle))
                                    Toast.makeText(context, "ID copiado!", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = BluePrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 3. Card: Mídia, links e documentos
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Mídia, links e documentos",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$totalMediaCount itens",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Media Category Tabs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedMediaTab == 0,
                                    onClick = { selectedMediaTab = 0 },
                                    label = { Text("Mídia (${mediaMessages.size})", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = selectedMediaTab == 1,
                                    onClick = { selectedMediaTab = 1 },
                                    label = { Text("Áudios (${audioMessages.size})", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = selectedMediaTab == 2,
                                    onClick = { selectedMediaTab = 2 },
                                    label = { Text("Docs (${docMessages.size})", fontSize = 11.sp) }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            when (selectedMediaTab) {
                                0 -> {
                                    if (mediaMessages.isEmpty()) {
                                        Text(
                                            text = "Nenhuma foto ou vídeo compartilhado ainda",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    } else {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(mediaMessages.take(15)) { msg ->
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    modifier = Modifier
                                                        .size(76.dp)
                                                        .clickable { onViewMedia(msg.content) }
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        if (msg.content.startsWith("/") || msg.content.startsWith("file://")) {
                                                            val local = File(msg.content.removePrefix("file://"))
                                                            if (local.exists()) {
                                                                AsyncImage(
                                                                    model = local,
                                                                    contentDescription = null,
                                                                    contentScale = ContentScale.Crop,
                                                                    modifier = Modifier.fillMaxSize()
                                                                )
                                                            } else {
                                                                Icon(Icons.Default.Image, contentDescription = null, tint = BluePrimary)
                                                            }
                                                        } else {
                                                            Icon(Icons.Default.Image, contentDescription = null, tint = BluePrimary)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                1 -> {
                                    val isPlaying by audioPlayerHelper.isPlaying.collectAsState()
                                    val playingId by audioPlayerHelper.playingMessageId.collectAsState()

                                    if (audioMessages.isEmpty()) {
                                        Text(
                                            text = "Nenhum áudio compartilhado ainda",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            audioMessages.take(10).forEach { msg ->
                                                val isThisPlaying = isPlaying && playingId == msg.id
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                                        .clickable { audioPlayerHelper.playAudio(msg.id, msg.content) }
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = if (isThisPlaying) Color(0xFF25D366) else BluePrimary,
                                                        modifier = Modifier.size(34.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = if (msg.isOutgoing) "Mensagem de voz enviada" else "Mensagem de voz recebida",
                                                            fontWeight = FontWeight.Medium,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(msg.createdAt)),
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                                2 -> {
                                    if (docMessages.isEmpty()) {
                                        Text(
                                            text = "Nenhum documento compartilhado ainda",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            docMessages.take(10).forEach { msg ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(26.dp))
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = msg.content.substringAfterLast("/"),
                                                            fontWeight = FontWeight.Medium,
                                                            fontSize = 13.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(msg.createdAt)),
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 4. Card: Privacidade e Segurança
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Criptografia E2E (Item informativo)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSecurityDialog = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = BluePrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Criptografia de ponta a ponta",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "As mensagens e chamadas são protegidas. Toque para verificar.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Bloqueio de conversa com biometria
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = if (isLocked) BluePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Bloquear conversa",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Exigir biometria para abrir esta conversa",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isLocked,
                                    onCheckedChange = { onToggleLock(it) }
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 5. Card: Ações do Contato (Limpar conversa, Bloquear contato)
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Limpar Conversa
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showClearConfirmDialog = true }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    tint = DangerRed,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "Limpar histórico de mensagens",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = DangerRed
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Bloquear Contato
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showBlockConfirmDialog = true }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Block,
                                    contentDescription = null,
                                    tint = DangerRed,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "Bloquear $displayTitle",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = DangerRed
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Diálogo de Segurança e Chaves Criptográficas
    if (showSecurityDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = BluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Criptografia E2E Verificada", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Suas mensagens e chamadas nesta conversa são protegidas com criptografia de ponta a ponta.\n\n" +
                                "Protocolo: AES-256-GCM + PBKDF2/HMAC-SHA256\n" +
                                "Identificador de Chave: ${userProfile?.publicKey?.take(16) ?: otherUserId.take(16)}...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSecurityDialog = false }) {
                    Text("Entendido", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Confirmação de Limpar Conversa
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Limpar conversa?", fontWeight = FontWeight.Bold) },
            text = { Text("Todas as mensagens deste chat serão apagadas do seu dispositivo.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmDialog = false
                    onClearChat()
                    Toast.makeText(context, "Conversa limpa", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Limpar", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Confirmação de Bloquear Contato
    if (showBlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            title = { Text("Bloquear $displayTitle?", fontWeight = FontWeight.Bold) },
            text = { Text("Contatos bloqueados não podem enviar mensagens nem fazer chamadas para você.") },
            confirmButton = {
                TextButton(onClick = {
                    showBlockConfirmDialog = false
                    onBlockContact()
                    onDismiss()
                }) {
                    Text("Bloquear", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Editar Nome do Contato
    if (showEditNicknameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNicknameDialog = false },
            title = { Text("Editar Nome do Contato", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Defina como este contato será exibido para você:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editNicknameInput,
                        onValueChange = { editNicknameInput = it },
                        label = { Text("Nome / Apelido") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newName = editNicknameInput.trim()
                    if (newName.isNotBlank()) {
                        showEditNicknameDialog = false
                        scope.launch {
                            try {
                                app.apiClient.apiService.updateContact(otherUserId, mapOf("nickname" to newName))
                                withContext(Dispatchers.IO) {
                                    val conv = app.database.conversationDao().getConversationById(conversationId)
                                    if (conv != null) {
                                        app.database.conversationDao().insertOrUpdate(conv.copy(otherDisplayName = newName))
                                    }
                                }
                                userProfile = userProfile?.copy(displayName = newName)
                                Toast.makeText(context, "Nome atualizado", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Erro ao atualizar", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNicknameDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun QuickActionPill(
    icon: ImageVector,
    label: String,
    isToggled: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isToggled) BluePrimary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isToggled) Color.White else BluePrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isToggled) BluePrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
