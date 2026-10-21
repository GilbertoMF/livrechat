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
import com.livrechat.app.data.remote.model.AddMembersRequest
import com.livrechat.app.data.remote.model.GroupMemberDto
import com.livrechat.app.data.remote.model.UserDto
import com.livrechat.app.media.AudioPlayerHelper
import com.livrechat.app.ui.common.UserAvatarView
import com.livrechat.app.ui.contacts.ContactUiModel
import com.livrechat.app.ui.theme.DangerRed
import com.livrechat.app.ui.theme.SuccessGreen
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.livrechat.app.ui.common.PhotoAdjustDialog
import com.livrechat.app.sync.ContactSyncHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
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
    isGroup: Boolean = false,
    groupTitle: String? = null,
    groupAvatar: String? = null,
    onDismiss: () -> Unit,
    onStartCall: () -> Unit,
    onToggleLock: (Boolean) -> Unit,
    onClearChat: () -> Unit,
    onBlockContact: () -> Unit,
    onLeaveGroup: () -> Unit = {},
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
    var isContactBlocked by remember { mutableStateOf(app.preferencesManager.isUserBlocked(otherUserId)) }
    var showReportDialog by remember { mutableStateOf(false) }

    // Group specific state
    val currentConv by remember(conversationId) {
        app.database.conversationDao().getConversationByIdFlow(conversationId)
    }.collectAsState(initial = null)

    val savedContacts by app.database.contactDao().getAllContactsFlow().collectAsState(initial = emptyList())

    val adminOnlyMessage = currentConv?.adminOnlyMessage ?: false
    val adminOnlyEditInfo = currentConv?.adminOnlyEditInfo ?: false
    val adminOnlyAddMembers = currentConv?.adminOnlyAddMembers ?: false

    var groupMembers by remember { mutableStateOf<List<GroupMemberDto>>(emptyList()) }
    val isCurrentUserAdmin = remember(groupMembers, app.preferencesManager.currentUserId) {
        groupMembers.any { it.id == app.preferencesManager.currentUserId && it.isAdmin }
    }

    var isLoadingMembers by remember { mutableStateOf(false) }
    var showAddMembersDialog by remember { mutableStateOf(false) }
    var contactsToSelect by remember { mutableStateOf<List<ContactUiModel>>(emptyList()) }
    var selectedMemberIdsToAdd by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isAddingMembers by remember { mutableStateOf(false) }
    var showLeaveGroupConfirm by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<GroupMemberDto?>(null) }
    var selectedMemberForAction by remember { mutableStateOf<GroupMemberDto?>(null) }
    var showGroupSettingsDialog by remember { mutableStateOf(false) }

    fun loadMembers() {
        if (!isGroup) return
        ContactSyncHelper.autoSyncIfPermitted(context)
        scope.launch(Dispatchers.IO) {
            try {
                isLoadingMembers = true
                val res = app.apiClient.apiService.getGroupMembers(conversationId)
                if (res.isSuccessful && res.body() != null) {
                    withContext(Dispatchers.Main) {
                        groupMembers = res.body()!!
                    }
                }
            } catch (e: Exception) {
            } finally {
                withContext(Dispatchers.Main) {
                    isLoadingMembers = false
                }
            }
        }
    }

    fun setMemberAdminStatus(targetMember: GroupMemberDto, makeAdmin: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                val res = app.apiClient.apiService.setGroupMemberAdmin(
                    conversationId,
                    targetMember.id,
                    mapOf("isAdmin" to makeAdmin)
                )
                if (res.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            if (makeAdmin) "${targetMember.displayName} agora é administrador" else "${targetMember.displayName} não é mais administrador",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadMembers()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Não foi possível alterar a permissão", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Erro de rede: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun saveGroupPermissions(msg: Boolean, editInfo: Boolean, addMembers: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                val res = app.apiClient.apiService.updateGroupPermissions(
                    conversationId,
                    mapOf(
                        "adminOnlyMessage" to msg,
                        "adminOnlyEditInfo" to editInfo,
                        "adminOnlyAddMembers" to addMembers
                    )
                )
                if (res.isSuccessful) {
                    val conv = app.database.conversationDao().getConversationById(conversationId)
                    if (conv != null) {
                        app.database.conversationDao().insertOrUpdate(
                            conv.copy(
                                adminOnlyMessage = msg,
                                adminOnlyEditInfo = editInfo,
                                adminOnlyAddMembers = addMembers
                            )
                        )
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Configurações do grupo atualizadas!", Toast.LENGTH_SHORT).show()
                        showGroupSettingsDialog = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Erro ao salvar permissões do grupo", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Erro de rede: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(conversationId, isGroup) {
        if (isGroup) {
            loadMembers()
        }
    }

    fun openAddMembersDialog() {
        scope.launch(Dispatchers.IO) {
            try {
                val currentUserId = app.preferencesManager.currentUserId
                val allContacts = app.database.contactDao().getAllContacts()
                val existingMemberIds = groupMembers.map { it.id }.toSet()
                val available = allContacts
                    .filter { it.contactUserId != currentUserId && it.contactUserId !in existingMemberIds }
                    .map {
                        ContactUiModel(
                            userId = it.contactUserId,
                            displayName = it.displayName.ifBlank { it.username },
                            username = it.username,
                            avatarUrl = it.avatarUrl,
                            bio = it.bio
                        )
                    }
                withContext(Dispatchers.Main) {
                    contactsToSelect = available
                    selectedMemberIdsToAdd = emptySet()
                    showAddMembersDialog = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Erro ao carregar contatos", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Carrega perfil enriquecido do contato e status de bloqueio
    LaunchedEffect(otherUserId, isGroup) {
        if (!isGroup) {
            withContext(Dispatchers.IO) {
                try {
                    val res = app.apiClient.apiService.getUserProfile(otherUserId)
                    if (res.isSuccessful && res.body() != null) {
                        userProfile = res.body()
                    }
                } catch (e: Exception) {}
                try {
                    val blockedRes = app.apiClient.apiService.getBlockedUsers()
                    if (blockedRes.isSuccessful && blockedRes.body() != null) {
                        val isBlockedOnServer = blockedRes.body()!!.any { it.id == otherUserId }
                        app.preferencesManager.setUserBlocked(otherUserId, isBlockedOnServer)
                        withContext(Dispatchers.Main) {
                            isContactBlocked = isBlockedOnServer
                        }
                    }
                } catch (e: Exception) {}
            }
        }
    }

    val customSavedName = remember(otherUserId) { if (!isGroup) app.preferencesManager.getContactCustomName(otherUserId) else null }
    var localCustomName by remember { mutableStateOf(customSavedName) }
    var currentGroupTitle by remember(groupTitle) { mutableStateOf(groupTitle) }
    var currentGroupAvatar by remember(groupAvatar) { mutableStateOf(groupAvatar) }

    var showAvatarOptionsDialog by remember { mutableStateOf(false) }
    var showPhotoAdjustDialog by remember { mutableStateOf(false) }
    var bitmapToAdjust by remember { mutableStateOf<Bitmap?>(null) }
    var isUpdatingGroupPhoto by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraFile != null && tempCameraFile!!.exists()) {
            val fileToProcess = tempCameraFile!!
            scope.launch(Dispatchers.IO) {
                try {
                    var bitmap = BitmapFactory.decodeFile(fileToProcess.absolutePath)
                    if (bitmap != null) {
                        try {
                            val exif = ExifInterface(fileToProcess.absolutePath)
                            val orientation = exif.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                            )
                            val matrix = android.graphics.Matrix()
                            when (orientation) {
                                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                            }
                            if (orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED) {
                                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            }
                        } catch (e: Exception) {}

                        withContext(Dispatchers.Main) {
                            bitmapToAdjust = bitmap
                            showPhotoAdjustDialog = true
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Erro ao carregar foto da câmera", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    fileToProcess.delete()
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            tempCameraUri?.let { takePictureLauncher.launch(it) }
        } else {
            Toast.makeText(context, "Permissão de câmera necessária para tirar foto", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCamera() {
        try {
            val file = File(context.cacheDir, "group_cam_${System.currentTimeMillis()}.jpg")
            tempCameraFile = file
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            tempCameraUri = uri
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                takePictureLauncher.launch(uri)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao abrir câmera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (originalBitmap != null) {
                        withContext(Dispatchers.Main) {
                            bitmapToAdjust = originalBitmap
                            showPhotoAdjustDialog = true
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Erro ao selecionar imagem", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun removeGroupPhoto() {
        isUpdatingGroupPhoto = true
        scope.launch(Dispatchers.IO) {
            try {
                try {
                    File(context.filesDir, "group_avatar_${conversationId}.jpg").delete()
                } catch (e: Exception) {}

                val conv = app.database.conversationDao().getConversationById(conversationId)
                if (conv != null) {
                    app.database.conversationDao().insertOrUpdate(conv.copy(groupAvatar = null))
                }

                app.apiClient.apiService.updateGroup(conversationId, mapOf("avatarUrl" to ""))

                withContext(Dispatchers.Main) {
                    currentGroupAvatar = null
                    Toast.makeText(context, "Foto do grupo removida", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Erro ao remover foto: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isUpdatingGroupPhoto = false
                }
            }
        }
    }

    val savedContact = remember(savedContacts, otherUserId) {
        savedContacts.find { it.contactUserId == otherUserId }
    }

    val displayTitle = if (isGroup) {
        currentGroupTitle?.ifBlank { null } ?: otherDisplayName.ifBlank { "Grupo" }
    } else {
        val remoteName = userProfile?.displayName?.takeIf {
            it.isNotBlank() && it != otherUserId && !it.startsWith("ID ") && it != userProfile?.username
        }
        val convName = otherDisplayName.takeIf {
            it.isNotBlank() && it != otherUserId && !it.startsWith("ID ")
        }
        localCustomName
            ?: savedContact?.displayName?.takeIf { it.isNotBlank() }
            ?: convName
            ?: remoteName
            ?: userProfile?.displayName?.ifBlank { null }
            ?: otherDisplayName.ifBlank { "Contato" }
    }

    val displayHandle = if (isGroup) {
        "Grupo · ${groupMembers.size} participante(s)"
    } else {
        userProfile?.username?.ifBlank { null }
            ?: otherUsername.ifBlank { otherUserId.take(12) }
    }

    val avatarUrl = if (isGroup) currentGroupAvatar else (userProfile?.avatarUrl ?: otherAvatarUrl)
    val bioText = if (isGroup) "Grupo criado no LivreChat" else (userProfile?.bio?.ifBlank { null } ?: "Disponível no LivreChat")

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
                            text = if (isGroup) "Dados do grupo" else "Dados do contato",
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
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(116.dp)
                        ) {
                            UserAvatarView(
                                avatarUrl = avatarUrl,
                                displayName = displayTitle,
                                size = 110.dp,
                                fontSize = 44.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                                    .clickable {
                                        if (isGroup) {
                                            if (!adminOnlyEditInfo || isCurrentUserAdmin) {
                                                showAvatarOptionsDialog = true
                                            } else if (!avatarUrl.isNullOrBlank()) {
                                                onViewMedia(avatarUrl)
                                            } else {
                                                Toast.makeText(context, "Apenas administradores podem alterar a foto do grupo", Toast.LENGTH_SHORT).show()
                                            }
                                        } else if (!avatarUrl.isNullOrBlank()) {
                                            onViewMedia(avatarUrl)
                                        }
                                    }
                            )

                            if (isGroup && (!adminOnlyEditInfo || isCurrentUserAdmin)) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(36.dp)
                                        .clickable { showAvatarOptionsDialog = true }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isUpdatingGroupPhoto) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.CameraAlt,
                                                contentDescription = "Alterar foto do grupo",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
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
                            if (!isGroup || (!adminOnlyEditInfo || isCurrentUserAdmin)) {
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
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Identificador / Detalhes
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = if (isGroup) displayHandle else "@$displayHandle",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Linha de Ações Rápidas (Áudio/Adicionar, Mensagem, Silenciar)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            if (isGroup) {
                                if (!adminOnlyAddMembers || isCurrentUserAdmin) {
                                    QuickActionPill(
                                        icon = Icons.Default.PersonAdd,
                                        label = "Adicionar",
                                        onClick = {
                                            openAddMembersDialog()
                                        }
                                    )
                                }
                            } else {
                                QuickActionPill(
                                    icon = Icons.Default.Phone,
                                    label = "Áudio",
                                    onClick = {
                                        onDismiss()
                                        onStartCall()
                                    }
                                )
                            }

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
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
                                                                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                            }
                                                        } else {
                                                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                                                        color = if (isThisPlaying) Color(0xFF25D366) else MaterialTheme.colorScheme.primary,
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
                                                    Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
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

                // 3.5. Card: Participantes do Grupo (Estilo WhatsApp / Telegram)
                if (isGroup) {
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
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${groupMembers.size} participante${if (groupMembers.size != 1) "s" else ""}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isLoadingMembers) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Permissões do Grupo (Apenas para Admins)
                                if (isCurrentUserAdmin) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { showGroupSettingsDialog = true }
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            modifier = Modifier.size(42.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.AdminPanelSettings,
                                                    contentDescription = "Permissões do grupo",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Permissões do grupo",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Editar dados, enviar mensagens e adicionar membros",
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
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                }

                                if (!adminOnlyAddMembers || isCurrentUserAdmin) {
                                    // Adicionar participantes
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { openAddMembersDialog() }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(42.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.PersonAdd,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column {
                                            Text(
                                                text = "Adicionar participantes",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Convidar contatos salvos",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Convidar via link
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("Link do Grupo", "https://livrechat.app/join/$conversationId")
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Link do grupo copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.size(42.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Link,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column {
                                            Text(
                                                text = "Convidar via link",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Compartilhar convite de acesso rápido",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (groupMembers.isNotEmpty()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }

                                // Lista de Membros
                                val currentUserId = app.preferencesManager.currentUserId

                                groupMembers.forEach { member ->
                                    val isSelf = member.id == currentUserId
                                    val rawId = member.username.ifBlank { member.displayName ?: "" }
                                    val resolvedName = ContactSyncHelper.getContactNameByPhoneOrUsername(context, rawId)
                                    val finalDisplayName: String = (if (!resolvedName.isNullOrBlank()) resolvedName else member.displayName)?.ifBlank { member.username } ?: member.username
                                    val resolvedAvatar = ContactSyncHelper.getContactPhotoByPhoneOrUsername(context, rawId) ?: member.avatarUrl

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                selectedMemberForAction = member
                                            }
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        UserAvatarView(
                                            avatarUrl = resolvedAvatar,
                                            displayName = finalDisplayName,
                                            size = 42.dp,
                                            fontSize = 16.sp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = finalDisplayName + if (isSelf) " (Você)" else "",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (member.isAdmin) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = SuccessGreen.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = "Admin",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = SuccessGreen,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = if (!member.bio.isNullOrBlank()) member.bio else "@${member.username}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (isCurrentUserAdmin && !isSelf) {
                                            IconButton(
                                                onClick = { memberToRemove = member },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.RemoveCircleOutline,
                                                    contentDescription = "Remover do grupo",
                                                    tint = DangerRed.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(20.dp)
                                                )
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
                                    tint = MaterialTheme.colorScheme.primary,
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
                                    tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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

                // 5. Card: Ações do Contato ou Grupo
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

                            if (isGroup) {
                                // Sair do grupo
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showLeaveGroupConfirm = true }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ExitToApp,
                                        contentDescription = null,
                                        tint = DangerRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = "Sair do grupo",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = DangerRed
                                    )
                                }
                            } else {
                                // Bloquear ou Desbloquear Contato
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showBlockConfirmDialog = true }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isContactBlocked) Icons.Default.LockOpen else Icons.Default.Block,
                                        contentDescription = null,
                                        tint = if (isContactBlocked) MaterialTheme.colorScheme.primary else DangerRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = if (isContactBlocked) "Desbloquear $displayTitle" else "Bloquear $displayTitle",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isContactBlocked) MaterialTheme.colorScheme.primary else DangerRed
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                Spacer(modifier = Modifier.height(6.dp))

                                // Denunciar Contato (WhatsApp Style)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showReportDialog = true }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Report,
                                        contentDescription = null,
                                        tint = DangerRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = "Denunciar $displayTitle",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = DangerRed
                                    )
                                }
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
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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

    // Confirmação de Bloquear / Desbloquear Contato
    if (showBlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            title = { Text(if (isContactBlocked) "Desbloquear $displayTitle?" else "Bloquear $displayTitle?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (isContactBlocked)
                        "Ao desbloquear, vocês poderão conversar e fazer chamadas normalmente."
                    else
                        "Contatos bloqueados não podem enviar mensagens nem fazer chamadas para você."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBlockConfirmDialog = false
                    val currentlyBlocked = isContactBlocked
                    scope.launch(Dispatchers.IO) {
                        try {
                            if (currentlyBlocked) {
                                app.apiClient.apiService.unblockUser(otherUserId)
                                app.preferencesManager.setUserBlocked(otherUserId, false)
                                withContext(Dispatchers.Main) {
                                    isContactBlocked = false
                                    Toast.makeText(context, "Contato desbloqueado", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                app.apiClient.apiService.blockUser(mapOf("blockedUserId" to otherUserId, "targetUserId" to otherUserId))
                                app.preferencesManager.setUserBlocked(otherUserId, true)
                                withContext(Dispatchers.Main) {
                                    isContactBlocked = true
                                    Toast.makeText(context, "Contato bloqueado", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Erro ao alterar bloqueio", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    onDismiss()
                }) {
                    Text(
                        text = if (isContactBlocked) "Desbloquear" else "Bloquear",
                        color = if (isContactBlocked) MaterialTheme.colorScheme.primary else DangerRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showReportDialog) {
        ReportDialog(
            conversationId = conversationId,
            otherUserId = otherUserId,
            otherDisplayName = displayTitle,
            messages = messages,
            reportedMessage = null,
            onDismiss = { showReportDialog = false },
            onReportSubmitted = { blocked ->
                showReportDialog = false
                if (blocked) {
                    onBlockContact()
                    onDismiss()
                }
            }
        )
    }

    // Editar Nome do Contato / Grupo
    if (showEditNicknameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNicknameDialog = false },
            title = {
                Text(
                    text = if (isGroup) "Editar Nome do Grupo" else "Editar Nome do Contato",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = if (isGroup) "Defina o nome de exibição deste grupo:" else "Defina como este contato será exibido para você:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editNicknameInput,
                        onValueChange = { editNicknameInput = it },
                        label = { Text(if (isGroup) "Nome do grupo" else "Nome / Apelido") },
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
                        if (isGroup) {
                            currentGroupTitle = newName
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val conv = app.database.conversationDao().getConversationById(conversationId)
                                    if (conv != null) {
                                        app.database.conversationDao().insertOrUpdate(conv.copy(groupTitle = newName))
                                    }
                                    app.apiClient.apiService.updateGroup(conversationId, mapOf("title" to newName))
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Nome do grupo atualizado", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Erro ao atualizar nome: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else {
                            localCustomName = newName
                            app.preferencesManager.setContactCustomName(otherUserId, newName)
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        app.database.conversationDao().updateOtherDisplayName(otherUserId, newName)
                                        val conv = app.database.conversationDao().getConversationById(conversationId)
                                        if (conv != null) {
                                            app.database.conversationDao().insertOrUpdate(conv.copy(otherDisplayName = newName))
                                        }
                                        val existingContact = app.database.contactDao().getContactByUserId(otherUserId)
                                        if (existingContact != null) {
                                            app.database.contactDao().insertContact(existingContact.copy(displayName = newName))
                                        } else {
                                            app.database.contactDao().insertContact(
                                                com.livrechat.app.data.local.entity.ContactEntity(
                                                    id = otherUserId,
                                                    contactUserId = otherUserId,
                                                    username = otherUsername,
                                                    displayName = newName,
                                                    avatarUrl = otherAvatarUrl,
                                                    publicKey = null
                                                )
                                            )
                                        }
                                    } catch (e: Exception) {}
                                    try {
                                        app.apiClient.apiService.updateContact(otherUserId, mapOf("nickname" to newName))
                                    } catch (e: Exception) {}
                                }
                                userProfile = userProfile?.copy(displayName = newName)
                                Toast.makeText(context, "Nome atualizado", Toast.LENGTH_SHORT).show()
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

    // Diálogo: Opções de Foto do Grupo
    if (showAvatarOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarOptionsDialog = false },
            title = {
                Text(
                    text = "Foto do grupo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Câmera
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAvatarOptionsDialog = false
                                launchCamera()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Câmera",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Câmera",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Galeria
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAvatarOptionsDialog = false
                                galleryLauncher.launch("image/*")
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Galeria",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Galeria",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (!currentGroupAvatar.isNullOrBlank()) {
                        // Ver foto
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAvatarOptionsDialog = false
                                    onViewMedia(currentGroupAvatar!!)
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Ver foto",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Ver foto",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Remover foto
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAvatarOptionsDialog = false
                                    removeGroupPhoto()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remover foto",
                                tint = DangerRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Remover foto",
                                fontSize = 16.sp,
                                color = DangerRed
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAvatarOptionsDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de Ajuste e Corte da Foto do Grupo (Circular)
    if (showPhotoAdjustDialog && bitmapToAdjust != null) {
        PhotoAdjustDialog(
            sourceBitmap = bitmapToAdjust!!,
            onDismiss = {
                showPhotoAdjustDialog = false
                bitmapToAdjust = null
            },
            onCropFinished = { croppedBitmap ->
                isUpdatingGroupPhoto = true
                showPhotoAdjustDialog = false
                bitmapToAdjust = null
                scope.launch(Dispatchers.IO) {
                    try {
                        // 1. Salvar arquivo local em cache
                        val localGroupFile = File(context.filesDir, "group_avatar_${conversationId}.jpg")
                        FileOutputStream(localGroupFile).use { fos ->
                            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                        }

                        // 2. Codificar para Base64 data URI
                        val baos = ByteArrayOutputStream()
                        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        val bytes = baos.toByteArray()
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val dataUri = "data:image/jpeg;base64,$base64"

                        // 3. Atualizar Room DB
                        val conv = app.database.conversationDao().getConversationById(conversationId)
                        if (conv != null) {
                            app.database.conversationDao().insertOrUpdate(conv.copy(groupAvatar = dataUri))
                        }

                        // 4. Enviar ao servidor
                        app.apiClient.apiService.updateGroup(conversationId, mapOf("avatarUrl" to dataUri))

                        withContext(Dispatchers.Main) {
                            currentGroupAvatar = dataUri
                            Toast.makeText(context, "Foto do grupo atualizada!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Erro ao atualizar foto: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            isUpdatingGroupPhoto = false
                        }
                    }
                }
            }
        )
    }

    // Diálogo: Adicionar participantes ao grupo
    if (showAddMembersDialog) {
        var addFilterQuery by remember { mutableStateOf("") }
        val filteredContacts = remember(contactsToSelect, addFilterQuery) {
            if (addFilterQuery.isBlank()) contactsToSelect
            else contactsToSelect.filter {
                it.displayName.contains(addFilterQuery, ignoreCase = true) ||
                it.username.contains(addFilterQuery, ignoreCase = true)
            }
        }

        AlertDialog(
            onDismissRequest = { if (!isAddingMembers) showAddMembersDialog = false },
            title = {
                Text(
                    text = "Adicionar ao grupo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    OutlinedTextField(
                        value = addFilterQuery,
                        onValueChange = { addFilterQuery = it },
                        placeholder = { Text("Pesquisar contatos...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    if (contactsToSelect.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhum novo contato para adicionar.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (filteredContacts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhum contato encontrado.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(filteredContacts) { contact ->
                                val isSelected = selectedMemberIdsToAdd.contains(contact.userId)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedMemberIdsToAdd = if (isSelected) {
                                                selectedMemberIdsToAdd - contact.userId
                                            } else {
                                                selectedMemberIdsToAdd + contact.userId
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatarView(
                                        avatarUrl = contact.avatarUrl,
                                        displayName = contact.displayName,
                                        size = 40.dp,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.displayName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "@${contact.username}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedMemberIdsToAdd = if (checked) {
                                                selectedMemberIdsToAdd + contact.userId
                                            } else {
                                                selectedMemberIdsToAdd - contact.userId
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedMemberIdsToAdd.isEmpty()) return@Button
                        isAddingMembers = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                val res = app.apiClient.apiService.addGroupMembers(
                                    conversationId,
                                    AddMembersRequest(selectedMemberIdsToAdd.toList())
                                )
                                if (res.isSuccessful) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Participante(s) adicionado(s) com sucesso!", Toast.LENGTH_SHORT).show()
                                        showAddMembersDialog = false
                                        loadMembers()
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Não foi possível adicionar os membros", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Erro de rede ao adicionar", Toast.LENGTH_SHORT).show()
                                }
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isAddingMembers = false
                                }
                            }
                        }
                    },
                    enabled = selectedMemberIdsToAdd.isNotEmpty() && !isAddingMembers
                ) {
                    if (isAddingMembers) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (selectedMemberIdsToAdd.isNotEmpty()) "Adicionar (${selectedMemberIdsToAdd.size})" else "Adicionar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddMembersDialog = false },
                    enabled = !isAddingMembers
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo: Confirmar remoção de participante
    if (memberToRemove != null) {
        val member = memberToRemove!!
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Remover participante?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Deseja remover ${member.displayName ?: member.username} do grupo?")
            },
            confirmButton = {
                TextButton(onClick = {
                    val toRemoveId = member.id
                    memberToRemove = null
                    scope.launch(Dispatchers.IO) {
                        try {
                            val res = app.apiClient.apiService.removeGroupMember(conversationId, toRemoveId)
                            if (res.isSuccessful) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Participante removido do grupo", Toast.LENGTH_SHORT).show()
                                    loadMembers()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Não foi possível remover participante", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Erro ao remover participante", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }) {
                    Text("Remover", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo: Confirmar saída do grupo
    if (showLeaveGroupConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveGroupConfirm = false },
            title = { Text("Sair do grupo?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Você não receberá mais mensagens deste grupo.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveGroupConfirm = false
                    onLeaveGroup()
                }) {
                    Text("Sair do grupo", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveGroupConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo: Permissões do Grupo (WhatsApp style)
    if (showGroupSettingsDialog) {
        var permMsg by remember(adminOnlyMessage) { mutableStateOf(adminOnlyMessage) }
        var permEdit by remember(adminOnlyEditInfo) { mutableStateOf(adminOnlyEditInfo) }
        var permAdd by remember(adminOnlyAddMembers) { mutableStateOf(adminOnlyAddMembers) }
        var isSavingPerms by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSavingPerms) showGroupSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Permissões do grupo", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Defina as regras e permissões para os participantes:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Switch 1: Enviar mensagens
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { permMsg = !permMsg }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Apenas admins enviam mensagens",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (permMsg) "Somente administradores podem falar" else "Todos os participantes podem enviar mensagens",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = permMsg,
                            onCheckedChange = { permMsg = it }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Switch 2: Editar dados do grupo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { permEdit = !permEdit }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Apenas admins alteram dados",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (permEdit) "Somente administradores alteram foto e nome" else "Todos podem alterar nome e foto do grupo",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = permEdit,
                            onCheckedChange = { permEdit = it }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Switch 3: Adicionar outros participantes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { permAdd = !permAdd }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Apenas admins adicionam participantes",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (permAdd) "Somente administradores adicionam membros" else "Todos os participantes podem adicionar outros",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = permAdd,
                            onCheckedChange = { permAdd = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSavingPerms = true
                        saveGroupPermissions(permMsg, permEdit, permAdd)
                    },
                    enabled = !isSavingPerms
                ) {
                    if (isSavingPerms) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showGroupSettingsDialog = false },
                    enabled = !isSavingPerms
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo: Ações de Membro do Grupo (WhatsApp style)
    if (selectedMemberForAction != null) {
        val member = selectedMemberForAction!!
        val isSelf = member.id == app.preferencesManager.currentUserId
        val rawId = member.username.ifBlank { member.displayName ?: "" }
        val resolvedName = ContactSyncHelper.getContactNameByPhoneOrUsername(context, rawId)
        val finalDisplayName: String = (if (!resolvedName.isNullOrBlank()) resolvedName else member.displayName)?.ifBlank { member.username } ?: member.username
        val resolvedAvatar = ContactSyncHelper.getContactPhotoByPhoneOrUsername(context, rawId) ?: member.avatarUrl

        AlertDialog(
            onDismissRequest = { selectedMemberForAction = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatarView(
                        avatarUrl = resolvedAvatar,
                        displayName = finalDisplayName,
                        size = 40.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(finalDisplayName, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("@${member.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (!isSelf) {
                        // Conversar diretamente
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedMemberForAction = null
                                    onDismiss()
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            app.apiClient.apiService.getOrCreateConversation(
                                                mapOf("otherUserId" to member.id)
                                            )
                                        } catch (e: Exception) {}
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Conversar com $finalDisplayName", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        }

                        if (isCurrentUserAdmin) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Tornar / Dispensar admin
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val target = member
                                        val makeAdmin = !target.isAdmin
                                        selectedMemberForAction = null
                                        setMemberAdminStatus(target, makeAdmin)
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (member.isAdmin) Icons.Default.RemoveModerator else Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (member.isAdmin) DangerRed else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = if (member.isAdmin) "Dispensar como administrador" else "Tornar administrador do grupo",
                                    fontSize = 15.sp,
                                    color = if (member.isAdmin) DangerRed else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Remover do grupo
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val target = member
                                        selectedMemberForAction = null
                                        memberToRemove = target
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonRemove,
                                    contentDescription = null,
                                    tint = DangerRed,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Remover do grupo",
                                    fontSize = 15.sp,
                                    color = DangerRed
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (member.isAdmin) "Você é um administrador deste grupo." else "Você é um participante deste grupo.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedMemberForAction = null }) {
                    Text("Fechar")
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
            color = if (isToggled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isToggled) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isToggled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
