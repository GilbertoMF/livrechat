package com.livrechat.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.livrechat.app.ui.common.UserAvatarView
import com.livrechat.app.LivreChatApp
import com.livrechat.app.data.local.entity.CallRecordEntity
import com.livrechat.app.data.local.entity.ConversationEntity
import com.livrechat.app.data.remote.model.StatusDto
import com.livrechat.app.ui.call.CallViewModel
import com.livrechat.app.ui.chat.ChatViewModel
import com.livrechat.app.ui.theme.BluePrimary
import com.livrechat.app.ui.theme.DangerRed
import com.livrechat.app.ui.theme.SuccessGreen
import com.livrechat.app.util.BiometricHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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
    // Tabs: 0 = Conversas, 1 = Atualizações (Status), 2 = Chamadas
    var selectedTab by remember { mutableStateOf(0) }
    val conversations by chatViewModel.conversationsFlow.collectAsState(initial = emptyList())
    val callHistory by callViewModel.callHistoryFlow.collectAsState(initial = emptyList())
    val isConnected by chatViewModel.isConnected.collectAsState()
    val typingStatus by chatViewModel.typingStatus.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = LivreChatApp.instance
    val prefs = remember { app.preferencesManager }
    val db = remember { app.database }
    val apiService = remember { app.apiClient.apiService }
    val currentUserId = prefs.currentUserId

    // Dialogs State
    var showClearCallHistoryDialog by remember { mutableStateOf(false) }
    var selectedConvForAction by remember { mutableStateOf<ConversationEntity?>(null) }
    var showClearConvDialog by remember { mutableStateOf<ConversationEntity?>(null) }

    // Status State
    var statusesList by remember { mutableStateOf<List<StatusDto>>(emptyList()) }
    var activeStatusViewerList by remember { mutableStateOf<List<StatusDto>?>(null) }
    var showStatusOptionsDialog by remember { mutableStateOf(false) }
    var showCreateTextStatusDialog by remember { mutableStateOf(false) }
    var showCreateImageStatusDialog by remember { mutableStateOf(false) }
    var newTextStatusContent by remember { mutableStateOf("") }
    var newStatusBgColor by remember { mutableStateOf("#075E54") }
    var newImageStatusBase64 by remember { mutableStateOf("") }
    var newImageStatusCaption by remember { mutableStateOf("") }
    var showCreateMusicStatusDialog by remember { mutableStateOf(false) }
    var showMusicCatalogPicker by remember { mutableStateOf(false) }
    var newMusicStatusAudioBase64 by remember { mutableStateOf("") }
    var newMusicStatusTitle by remember { mutableStateOf("") }
    var newMusicStatusArtist by remember { mutableStateOf("") }
    var newMusicStatusAlbumArt by remember { mutableStateOf("") }
    var newMusicStatusCaption by remember { mutableStateOf("") }
    var newMusicStatusBgColor by remember { mutableStateOf("#075E54") }
    var isPostingStatus by remember { mutableStateOf(false) }

    fun loadStatuses() {
        scope.launch {
            try {
                val res = apiService.getStatuses()
                if (res.isSuccessful && res.body() != null) {
                    statusesList = res.body()!!
                }
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(isConnected) {
        chatViewModel.syncConversations()
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            loadStatuses()
            while (isActive) {
                delay(15000L)
                loadStatuses()
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = object : com.livrechat.app.data.remote.WebSocketEventsListener {
            override fun onConnected() { loadStatuses() }
            override fun onDisconnected() {}
            override fun onMessageReceived(message: com.livrechat.app.data.local.entity.MessageEntity) {}
            override fun onMessageAck(messageId: String, clientMessageId: String?, status: String) {}
            override fun onMessageReceipt(messageId: String, status: String, userId: String) {}
            override fun onTyping(conversationId: String, userId: String, username: String, isTyping: Boolean) {}
            override fun onIncomingCall(callerId: String, callerUsername: String, callerDisplayName: String, callerAvatarUrl: String?, sdp: String) {}
            override fun onCallAnswered(calleeId: String, sdp: String) {}
            override fun onIceCandidateReceived(fromUserId: String, candidate: com.google.gson.JsonObject) {}
            override fun onCallRinging(fromUserId: String) {}
            override fun onCallRejected(fromUserId: String, reason: String) {}
            override fun onCallEnded(fromUserId: String) {}
            override fun onStatusUpdated() {
                scope.launch(Dispatchers.Main) {
                    loadStatuses()
                }
            }
        }
        val connMgr = LivreChatApp.instance.connectionManager
        connMgr.registerListener(listener)
        onDispose {
            connMgr.unregisterListener(listener)
        }
    }

    // Status Image Picker
    val statusImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (originalBitmap != null) {
                        val maxDim = 1080
                        val width = originalBitmap.width
                        val height = originalBitmap.height
                        val scale = if (width > height) maxDim.toFloat() / width else maxDim.toFloat() / height
                        val scaledBitmap = if (scale < 1f) {
                            Bitmap.createScaledBitmap(
                                originalBitmap,
                                (width * scale).toInt().coerceAtLeast(1),
                                (height * scale).toInt().coerceAtLeast(1),
                                true
                            )
                        } else {
                            originalBitmap
                        }
                        val baos = ByteArrayOutputStream()
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
                        val bytes = baos.toByteArray()
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val dataUri = "data:image/jpeg;base64,$base64"
                        withContext(Dispatchers.Main) {
                            newImageStatusBase64 = dataUri
                            newImageStatusCaption = ""
                            showCreateImageStatusDialog = true
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Erro ao carregar imagem", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    var tempStatusCameraFile by remember { mutableStateOf<File?>(null) }
    var tempStatusCameraUri by remember { mutableStateOf<Uri?>(null) }

    val statusCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempStatusCameraFile != null && tempStatusCameraFile!!.exists()) {
            val fileToProcess = tempStatusCameraFile!!
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
                            val matrix = Matrix()
                            when (orientation) {
                                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                            }
                            if (orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED) {
                                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            }
                        } catch (e: Exception) {}

                        val maxDim = 1080
                        val width = bitmap.width
                        val height = bitmap.height
                        val scale = if (width > height) maxDim.toFloat() / width else maxDim.toFloat() / height
                        val scaledBitmap = if (scale < 1f) {
                            Bitmap.createScaledBitmap(
                                bitmap,
                                (width * scale).toInt().coerceAtLeast(1),
                                (height * scale).toInt().coerceAtLeast(1),
                                true
                            )
                        } else {
                            bitmap
                        }
                        val baos = ByteArrayOutputStream()
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
                        val bytes = baos.toByteArray()
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val dataUri = "data:image/jpeg;base64,$base64"
                        withContext(Dispatchers.Main) {
                            newImageStatusBase64 = dataUri
                            newImageStatusCaption = ""
                            showCreateImageStatusDialog = true
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Erro ao processar foto da câmera", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    fileToProcess.delete()
                }
            }
        }
    }

    val statusCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            tempStatusCameraUri?.let { statusCameraLauncher.launch(it) }
        } else {
            Toast.makeText(context, "Permissão de câmera é necessária para tirar foto", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchStatusCamera() {
        try {
            val file = File(context.cacheDir, "status_cam_${System.currentTimeMillis()}.jpg")
            tempStatusCameraFile = file
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            tempStatusCameraUri = uri
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                statusCameraLauncher.launch(uri)
            } else {
                statusCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao abrir câmera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Status Audio Picker (Estilo WhatsApp)
    val statusAudioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()
                    if (bytes != null && bytes.size <= 12 * 1024 * 1024) {
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val dataUri = "data:audio/mp3;base64,$base64"

                        // Obter nome real do arquivo de música
                        var detectedTitle = "Minha Música"
                        try {
                            val cursor = context.contentResolver.query(uri, null, null, null, null)
                            cursor?.use {
                                if (it.moveToFirst()) {
                                    val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                    if (nameIdx >= 0) {
                                        val fn = it.getString(nameIdx)
                                        if (!fn.isNullOrBlank()) {
                                            detectedTitle = fn.substringBeforeLast(".")
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {}

                        withContext(Dispatchers.Main) {
                            newMusicStatusAudioBase64 = dataUri
                            newMusicStatusTitle = detectedTitle
                            newMusicStatusArtist = ""
                            newMusicStatusAlbumArt = ""
                            newMusicStatusCaption = ""
                            newMusicStatusBgColor = "#075E54"
                            showCreateMusicStatusDialog = true
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Arquivo de música muito grande (máx 12MB)", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Erro ao carregar arquivo de música", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // DIALOG: Limpar histórico de chamadas
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

    // DIALOG: Opções da conversa (Long click)
    if (selectedConvForAction != null) {
        val conv = selectedConvForAction!!
        val isLocked = prefs.isConversationLocked(conv.id)
        AlertDialog(
            onDismissRequest = { selectedConvForAction = null },
            title = { Text(conv.otherDisplayName, fontWeight = FontWeight.Bold) },
            text = { Text("Selecione uma ação para esta conversa:") },
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
                        val toDel = conv
                        selectedConvForAction = null
                        scope.launch(Dispatchers.IO) {
                            try {
                                apiService.deleteConversation(toDel.id)
                                apiService.removeContact(toDel.otherUserId)
                                apiService.clearConversation(toDel.id)
                            } catch (e: Exception) {}
                            try {
                                db.contactDao().deleteContact(toDel.otherUserId)
                                db.conversationDao().deleteConversation(toDel.id)
                                db.messageDao().clearConversationMessages(toDel.id)
                            } catch (e: Exception) {}
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Conversa apagada", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Text("Apagar conversa", color = DangerRed, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = {
                        showClearConvDialog = conv
                        selectedConvForAction = null
                    }) {
                        Text("Limpar", color = DangerRed)
                    }
                    TextButton(onClick = { selectedConvForAction = null }) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }

    // DIALOG: Limpar conversa
    if (showClearConvDialog != null) {
        val conv = showClearConvDialog!!
        AlertDialog(
            onDismissRequest = { showClearConvDialog = null },
            title = { Text("Limpar mensagens?", fontWeight = FontWeight.Bold) },
            text = { Text("Todas as mensagens com ${conv.otherDisplayName} serão apagadas.") },
            confirmButton = {
                TextButton(onClick = {
                    chatViewModel.clearConversation(conv.id, conv.otherUserId)
                    showClearConvDialog = null
                    Toast.makeText(context, "Conversa limpa", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Limpar", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConvDialog = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // DIALOG: Opções de Status (Texto, Foto, Música) com Design Moderno
    if (showStatusOptionsDialog) {
        Dialog(onDismissRequest = { showStatusOptionsDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF25D366).copy(alpha = 0.15f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AddCircleOutline,
                                    contentDescription = null,
                                    tint = Color(0xFF25D366),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Adicionar ao Status", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Compartilhe momentos que expiram em 24h", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 1. Texto Criativo
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showStatusOptionsDialog = false
                                showCreateTextStatusDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF00A884).copy(alpha = 0.2f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF00A884), modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Texto Criativo", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Escreva frases com fundos coloridos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Câmera
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showStatusOptionsDialog = false
                                launchStatusCamera()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF25D366).copy(alpha = 0.2f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Tirar Foto na Câmera", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Capture e publique instantaneamente", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Galeria
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showStatusOptionsDialog = false
                                statusImagePickerLauncher.launch("image/*")
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF007AFF).copy(alpha = 0.2f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Foto da Galeria", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Escolha fotos dos seus arquivos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Música (Estilo WhatsApp)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showStatusOptionsDialog = false
                                showMusicCatalogPicker = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFE91E63).copy(alpha = 0.2f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color(0xFFE91E63), modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Música de Cantores", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Escolha músicas de cantores, astral ou pesquise", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedButton(
                        onClick = { showStatusOptionsDialog = false },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancelar", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    // DIALOG: Criar Status de Texto
    if (showCreateTextStatusDialog) {
        val colorOptions = listOf("#075E54", "#128C7E", "#7B1FA2", "#D81B60", "#1E88E5", "#263238")
        AlertDialog(
            onDismissRequest = { if (!isPostingStatus) showCreateTextStatusDialog = false },
            title = { Text("Novo Status de Texto", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTextStatusContent,
                        onValueChange = { newTextStatusContent = it },
                        placeholder = { Text("Digite o que está pensando...") },
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Cor de fundo:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colorOptions.forEach { hex ->
                            val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Black }
                            Surface(
                                shape = CircleShape,
                                color = color,
                                modifier = Modifier
                                    .size(36.dp)
                                    .border(
                                        width = if (newStatusBgColor == hex) 3.dp else 0.dp,
                                        color = if (newStatusBgColor == hex) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { newStatusBgColor = hex }
                            ) {}
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newTextStatusContent.trim()
                        if (trimmed.isNotBlank()) {
                            scope.launch {
                                isPostingStatus = true
                                try {
                                    val res = apiService.createStatus(
                                        mapOf(
                                            "contentType" to "text",
                                            "content" to trimmed,
                                            "backgroundColor" to newStatusBgColor,
                                            "avatarUrl" to (prefs.userAvatarUrl ?: ""),
                                            "displayName" to (prefs.currentDisplayName ?: prefs.currentUsername ?: "Usuário")
                                        )
                                    )
                                    if (res.isSuccessful) {
                                        showCreateTextStatusDialog = false
                                        newTextStatusContent = ""
                                        Toast.makeText(context, "Status publicado!", Toast.LENGTH_SHORT).show()
                                        loadStatuses()
                                    } else {
                                        Toast.makeText(context, "Falha ao publicar status", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Erro: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isPostingStatus = false
                                }
                            }
                        }
                    },
                    enabled = newTextStatusContent.isNotBlank() && !isPostingStatus,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Text(if (isPostingStatus) "Publicando..." else "Compartilhar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTextStatusDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // DIALOG: Preview e Legenda de Foto do Status
    if (showCreateImageStatusDialog) {
        AlertDialog(
            onDismissRequest = { if (!isPostingStatus) showCreateImageStatusDialog = false },
            title = { Text("Compartilhar Foto no Status", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(200.dp)
                    ) {
                        AsyncImage(
                            model = newImageStatusBase64,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = newImageStatusCaption,
                        onValueChange = { newImageStatusCaption = it },
                        placeholder = { Text("Adicionar legenda...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isPostingStatus = true
                            try {
                                val res = apiService.createStatus(
                                    mapOf(
                                        "contentType" to "image",
                                        "content" to newImageStatusBase64,
                                        "caption" to newImageStatusCaption.trim(),
                                        "avatarUrl" to (prefs.userAvatarUrl ?: ""),
                                        "displayName" to (prefs.currentDisplayName ?: prefs.currentUsername ?: "Usuário")
                                    )
                                )
                                if (res.isSuccessful) {
                                    showCreateImageStatusDialog = false
                                    newImageStatusBase64 = ""
                                    newImageStatusCaption = ""
                                    Toast.makeText(context, "Foto publicada no Status!", Toast.LENGTH_SHORT).show()
                                    loadStatuses()
                                } else {
                                    Toast.makeText(context, "Falha ao publicar foto", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Erro: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isPostingStatus = false
                            }
                        }
                    },
                    enabled = !isPostingStatus,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Text(if (isPostingStatus) "Enviando..." else "Compartilhar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateImageStatusDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // DIALOG: Seletor de Músicas de Cantores (Estilo WhatsApp / Instagram)
    if (showMusicCatalogPicker) {
        MusicPickerDialog(
            onDismiss = { showMusicCatalogPicker = false },
            onSelectTrack = { track ->
                showMusicCatalogPicker = false
                newMusicStatusAudioBase64 = track.previewUrl
                newMusicStatusTitle = track.title
                newMusicStatusArtist = track.artist
                newMusicStatusAlbumArt = track.albumArtUrl
                newMusicStatusCaption = ""
                newMusicStatusBgColor = "#075E54"
                showCreateMusicStatusDialog = true
            },
            onPickLocalFile = {
                showMusicCatalogPicker = false
                statusAudioPickerLauncher.launch("audio/*")
            }
        )
    }

    // DIALOG: Criar Status com Música (Estilo WhatsApp)
    if (showCreateMusicStatusDialog) {
        val colorOptions = listOf("#075E54", "#128C7E", "#7B1FA2", "#C2185B", "#1976D2", "#212121", "#E65100")
        var isPlayingPreview by remember { mutableStateOf(false) }
        var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

        DisposableEffect(Unit) {
            onDispose {
                try {
                    previewPlayer?.stop()
                    previewPlayer?.release()
                } catch (e: Exception) {}
                previewPlayer = null
            }
        }

        Dialog(
            onDismissRequest = {
                if (!isPostingStatus) {
                    try { previewPlayer?.stop(); previewPlayer?.release() } catch (e: Exception) {}
                    previewPlayer = null
                    showCreateMusicStatusDialog = false
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val bgColor = remember(newMusicStatusBgColor) {
                try { Color(android.graphics.Color.parseColor(newMusicStatusBgColor)) }
                catch (e: Exception) { Color(0xFF075E54) }
            }

            Scaffold(
                containerColor = bgColor,
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = {
                            try { previewPlayer?.stop(); previewPlayer?.release() } catch (e: Exception) {}
                            previewPlayer = null
                            showCreateMusicStatusDialog = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                        }
                        Text(
                            "Status com Música",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Box(modifier = Modifier.size(48.dp))
                    }
                },
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(16.dp)
                    ) {
                        // Paleta de Cores de Fundo
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            colorOptions.forEach { hex ->
                                val c = Color(android.graphics.Color.parseColor(hex))
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp)
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .clickable { newMusicStatusBgColor = hex }
                                        .then(
                                            if (newMusicStatusBgColor == hex)
                                                Modifier.border(2.5.dp, Color.White, CircleShape)
                                            else Modifier
                                        )
                                )
                            }
                        }

                        // Legenda e Botão Enviar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newMusicStatusCaption,
                                onValueChange = { newMusicStatusCaption = it },
                                placeholder = { Text("Adicione uma legenda...", color = Color.White.copy(alpha = 0.6f)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            FloatingActionButton(
                                onClick = {
                                    if (isPostingStatus) return@FloatingActionButton
                                    try { previewPlayer?.stop(); previewPlayer?.release() } catch (e: Exception) {}
                                    previewPlayer = null
                                    scope.launch {
                                        isPostingStatus = true
                                        val displayTitle = newMusicStatusTitle.ifBlank { "Música de Status" }
                                        val combinedCaption = buildString {
                                            append("🎵 ")
                                            append(displayTitle)
                                            if (newMusicStatusArtist.isNotBlank()) {
                                                append(" • ")
                                                append(newMusicStatusArtist)
                                            }
                                            if (newMusicStatusAlbumArt.isNotBlank()) {
                                                append(" [art:")
                                                append(newMusicStatusAlbumArt)
                                                append("]")
                                            }
                                            if (newMusicStatusCaption.isNotBlank()) {
                                                append(" -- ")
                                                append(newMusicStatusCaption.trim())
                                            }
                                        }
                                        try {
                                            val res = apiService.createStatus(
                                                mapOf(
                                                    "contentType" to "audio",
                                                    "content" to newMusicStatusAudioBase64,
                                                    "caption" to combinedCaption,
                                                    "backgroundColor" to newMusicStatusBgColor,
                                                    "avatarUrl" to (prefs.userAvatarUrl ?: ""),
                                                    "displayName" to (prefs.currentDisplayName ?: prefs.currentUsername ?: "Usuário")
                                                )
                                            )
                                            if (res.isSuccessful) {
                                                showCreateMusicStatusDialog = false
                                                Toast.makeText(context, "Música publicada no Status!", Toast.LENGTH_SHORT).show()
                                                loadStatuses()
                                            } else {
                                                Toast.makeText(context, "Falha ao publicar status", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isPostingStatus = false
                                        }
                                    }
                                },
                                containerColor = Color(0xFF25D366),
                                contentColor = Color.White
                            ) {
                                if (isPostingStatus) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                } else {
                                    Icon(Icons.Default.Send, contentDescription = "Enviar")
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    // Floating WhatsApp Music Sticker Card
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (newMusicStatusAlbumArt.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(2.dp, Color.White.copy(alpha = 0.35f)),
                                    modifier = Modifier.size(100.dp)
                                ) {
                                    AsyncImage(
                                        model = newMusicStatusAlbumArt,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF25D366).copy(alpha = 0.25f),
                                    border = BorderStroke(2.dp, Color(0xFF25D366)),
                                    modifier = Modifier.size(80.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Audiotrack,
                                            contentDescription = null,
                                            tint = Color(0xFF25D366),
                                            modifier = Modifier.size(42.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            BasicTextField(
                                value = newMusicStatusTitle,
                                onValueChange = { newMusicStatusTitle = it },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (newMusicStatusArtist.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = newMusicStatusArtist,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Música no Status",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            IconButton(
                                onClick = {
                                    if (isPlayingPreview) {
                                        previewPlayer?.pause()
                                        isPlayingPreview = false
                                    } else {
                                        if (previewPlayer == null) {
                                            try {
                                                if (newMusicStatusAudioBase64.startsWith("http")) {
                                                    val mp = MediaPlayer().apply {
                                                        setDataSource(newMusicStatusAudioBase64)
                                                        setOnCompletionListener { isPlayingPreview = false }
                                                        setOnPreparedListener {
                                                            it.start()
                                                            isPlayingPreview = true
                                                        }
                                                        prepareAsync()
                                                    }
                                                    previewPlayer = mp
                                                } else {
                                                    val file = File(context.cacheDir, "preview_music_status.mp3")
                                                    val base64 = if (newMusicStatusAudioBase64.contains(",")) newMusicStatusAudioBase64.substringAfter(",") else newMusicStatusAudioBase64
                                                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                                                    FileOutputStream(file).use { it.write(bytes) }
                                                    val mp = MediaPlayer().apply {
                                                        setDataSource(file.absolutePath)
                                                        setOnCompletionListener { isPlayingPreview = false }
                                                        prepare()
                                                        start()
                                                    }
                                                    previewPlayer = mp
                                                    isPlayingPreview = true
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Erro ao reproduzir prévia", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            previewPlayer?.start()
                                            isPlayingPreview = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    if (isPlayingPreview) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Prévia",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Full-Screen Status Story Viewer
    if (activeStatusViewerList != null) {
        FullScreenStatusViewer(
            statuses = activeStatusViewerList!!,
            currentUserId = currentUserId,
            userAvatarUrl = prefs.userAvatarUrl,
            onDismiss = { activeStatusViewerList = null },
            onDeleteStatus = { statusId ->
                scope.launch {
                    try {
                        val res = apiService.deleteStatus(statusId)
                        if (res.isSuccessful) {
                            statusesList = statusesList.filter { it.id != statusId }
                            activeStatusViewerList = activeStatusViewerList?.filter { it.id != statusId }
                            if (activeStatusViewerList.isNullOrEmpty()) {
                                activeStatusViewerList = null
                            }
                            Toast.makeText(context, "Status excluído", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {}
                }
            }
        )
    }

    val myStatuses = remember(statusesList, currentUserId) {
        statusesList.filter { it.userId == currentUserId }
    }
    val contactsStatuses = remember(statusesList, currentUserId) {
        statusesList.filter { it.userId != currentUserId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("LivreChat", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (isConnected) SuccessGreen else DangerRed,
                                modifier = Modifier.size(9.dp)
                            ) {}
                        }
                        prefs.currentUsername?.let { uname ->
                            Text(
                                text = if (uname.length == 8 && uname.all { it.isDigit() }) "ID Virtual: $uname" else "@$uname",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    if (selectedTab == 1) {
                        IconButton(onClick = { loadStatuses() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Atualizar Status")
                        }
                    } else if (selectedTab == 2 && callHistory.isNotEmpty()) {
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
            when (selectedTab) {
                0 -> {
                    FloatingActionButton(
                        onClick = onNavigateToSearch,
                        containerColor = Color(0xFF25D366),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AddComment, contentDescription = "Novo Chat")
                    }
                }
                1 -> {
                    Column(horizontalAlignment = Alignment.End) {
                        SmallFloatingActionButton(
                            onClick = { showCreateTextStatusDialog = true },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Status de Texto", modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        FloatingActionButton(
                            onClick = { showStatusOptionsDialog = true },
                            containerColor = Color(0xFF25D366),
                            contentColor = Color.White,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Novo Status")
                        }
                    }
                }
                2 -> {
                    FloatingActionButton(
                        onClick = onNavigateToSearch,
                        containerColor = Color(0xFF25D366),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Call, contentDescription = "Nova Chamada")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs: Conversas / Atualizações / Chamadas
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
                            text = "Atualizações",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            text = "Chamadas",
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            if (selectedTab == 0) {
                // CONVERSATIONS TAB
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
            } else if (selectedTab == 1) {
                // STATUS / ATUALIZAÇÕES TAB
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Status",
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                            IconButton(onClick = { loadStatuses() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // Ações Rápidas em Estilo WhatsApp / Redes
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showStatusOptionsDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Câmera", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showCreateTextStatusDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Texto", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showCreateMusicStatusDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFE91E63), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Música", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // "Meu status" Card
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (myStatuses.isNotEmpty()) {
                                        activeStatusViewerList = myStatuses
                                    } else {
                                        showStatusOptionsDialog = true
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(58.dp),
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (myStatuses.isNotEmpty()) Color(0xFF25D366).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .size(58.dp)
                                            .then(
                                                if (myStatuses.isNotEmpty()) Modifier.border(2.5.dp, Color(0xFF25D366), CircleShape).padding(2.5.dp)
                                                else Modifier
                                            )
                                    ) {
                                        UserAvatarView(
                                            avatarUrl = myStatuses.lastOrNull()?.avatarUrl ?: prefs.userAvatarUrl,
                                            displayName = prefs.currentDisplayName ?: "V",
                                            size = 54.dp,
                                            fontSize = 22.sp
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF25D366),
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clickable { showStatusOptionsDialog = true }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Adicionar status",
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Meu status", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (myStatuses.isNotEmpty()) "${myStatuses.size} atualização(ões) ativa(s)" else "Toque para adicionar atualização",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row {
                                    IconButton(onClick = { showCreateTextStatusDialog = true }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Texto", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { showStatusOptionsDialog = true }) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = "Câmera", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // "Atualizações recentes"
                    val grouped = contactsStatuses.groupBy { it.userId }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Atualizações recentes",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (contactsStatuses.isNotEmpty()) {
                                Text(
                                    text = "${grouped.keys.size} contato(s)",
                                    fontSize = 12.sp,
                                    color = Color(0xFF25D366),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (contactsStatuses.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.HistoryToggleOff,
                                        contentDescription = null,
                                        tint = Color(0xFF25D366),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        "Nenhuma atualização recente",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Os status postados pelos seus contatos aparecerão aqui e ficam visíveis por 24h.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = { loadStatuses() },
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Verificar atualizações", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        items(grouped.keys.toList(), key = { it }) { contactUserId ->
                            val userStatuses = grouped[contactUserId] ?: emptyList()
                            val latestStatus = userStatuses.last()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeStatusViewerList = userStatuses }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .border(2.5.dp, Color(0xFF25D366), CircleShape)
                                        .padding(2.5.dp)
                                ) {
                                    UserAvatarView(
                                        avatarUrl = latestStatus.avatarUrl,
                                        displayName = latestStatus.displayName,
                                        size = 50.dp,
                                        fontSize = 18.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(latestStatus.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        formatRelativeTime(latestStatus.createdAt),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (userStatuses.size > 1) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF25D366).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "${userStatuses.size}",
                                            color = Color(0xFF25D366),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), thickness = 0.5.dp)
                        }
                    }
                }
            } else {
                // CALLS TAB
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

@Composable
fun FullScreenStatusViewer(
    statuses: List<StatusDto>,
    currentUserId: String?,
    userAvatarUrl: String? = null,
    onDismiss: () -> Unit,
    onDeleteStatus: (String) -> Unit
) {
    if (statuses.isEmpty()) {
        onDismiss()
        return
    }
    val context = LocalContext.current
    var currentIndex by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }

    val currentStatus = statuses.getOrNull(currentIndex) ?: run {
        onDismiss()
        return
    }

    var isAudioPlaying by remember { mutableStateOf(false) }
    var statusMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(currentStatus) {
        if (currentStatus.contentType == "audio" && currentStatus.content.isNotBlank()) {
            try {
                val mp = MediaPlayer()
                val content = currentStatus.content
                if (content.startsWith("data:") || content.length > 500) {
                    val file = File(context.cacheDir, "status_play_${currentStatus.id}.mp3")
                    val base64 = if (content.contains(",")) content.substringAfter(",") else content
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    FileOutputStream(file).use { it.write(bytes) }
                    mp.setDataSource(file.absolutePath)
                } else {
                    mp.setDataSource(content)
                }
                mp.setOnPreparedListener {
                    it.start()
                    isAudioPlaying = true
                }
                mp.setOnCompletionListener {
                    isAudioPlaying = false
                }
                mp.prepareAsync()
                statusMediaPlayer = mp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        onDispose {
            try {
                statusMediaPlayer?.stop()
                statusMediaPlayer?.release()
            } catch (e: Exception) {}
            statusMediaPlayer = null
            isAudioPlaying = false
        }
    }

    LaunchedEffect(currentIndex, isPaused) {
        progress = 0f
        val stepMs = 50L
        val totalMs = if (currentStatus.contentType == "audio") 15000L else 5000L
        val steps = (totalMs / stepMs).toInt()
        for (i in 1..steps) {
            if (isPaused) {
                while (isPaused) {
                    delay(100)
                }
            }
            delay(stepMs)
            progress = i.toFloat() / steps
        }
        if (currentIndex < statuses.size - 1) {
            currentIndex++
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val backgroundColor = remember(currentStatus) {
            if ((currentStatus.contentType == "text" || currentStatus.contentType == "audio") && !currentStatus.backgroundColor.isNullOrBlank()) {
                try {
                    Color(android.graphics.Color.parseColor(currentStatus.backgroundColor))
                } catch (e: Exception) {
                    Color(0xFF075E54)
                }
            } else {
                Color.Black
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            // Tap area: Left half previous, Right half next
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .clickable {
                            if (currentIndex > 0) {
                                currentIndex--
                            }
                        }
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .clickable {
                            if (currentIndex < statuses.size - 1) {
                                currentIndex++
                            } else {
                                onDismiss()
                            }
                        }
                )
            }

            // Main Content Centered
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                when (currentStatus.contentType) {
                    "text" -> {
                        Text(
                            text = currentStatus.content,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                    "image" -> {
                        AsyncImage(
                            model = currentStatus.content,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    "audio" -> {
                        val rawCaption = currentStatus.caption.orEmpty()
                        val (musicTitle, musicArtist, albumArt, userCaption) = remember(rawCaption) {
                            var art = ""
                            var rest = rawCaption
                            if (rest.contains("[art:") && rest.contains("]")) {
                                art = rest.substringAfter("[art:").substringBefore("]")
                                rest = rest.substringBefore("[art:") + rest.substringAfter("]")
                            }
                            var userC = ""
                            if (rest.contains(" -- ")) {
                                userC = rest.substringAfter(" -- ").trim()
                                rest = rest.substringBefore(" -- ").trim()
                            }
                            val cleanTitleArtist = rest.removePrefix("🎵 ").trim()
                            val title = if (cleanTitleArtist.contains(" • ")) cleanTitleArtist.substringBefore(" • ").trim() else cleanTitleArtist.ifBlank { "Música de Status" }
                            val artist = if (cleanTitleArtist.contains(" • ")) cleanTitleArtist.substringAfter(" • ").trim() else ""
                            listOf(title, artist, art, userC)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            // WhatsApp Floating Music Card
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = Color.Black.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    if (albumArt.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            shadowElevation = 8.dp,
                                            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.35f)),
                                            modifier = Modifier.size(110.dp)
                                        ) {
                                            AsyncImage(
                                                model = albumArt,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(110.dp)
                                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                                .border(2.5.dp, Color(0xFF25D366), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color.Black.copy(alpha = 0.4f),
                                                modifier = Modifier.size(50.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.MusicNote,
                                                        contentDescription = null,
                                                        tint = Color(0xFF25D366),
                                                        modifier = Modifier.size(30.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(18.dp))

                                    Text(
                                        text = musicTitle,
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (musicArtist.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = musicArtist,
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF25D366).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (musicArtist.isNotBlank()) "Música de Cantor" else "Áudio de Status",
                                            color = Color(0xFF25D366),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        IconButton(
                                            onClick = {
                                                try {
                                                    if (statusMediaPlayer?.isPlaying == true) {
                                                        statusMediaPlayer?.pause()
                                                        isAudioPlaying = false
                                                    } else {
                                                        statusMediaPlayer?.start()
                                                        isAudioPlaying = true
                                                    }
                                                } catch (e: Exception) {}
                                            },
                                            modifier = Modifier
                                                .size(54.dp)
                                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = if (isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isAudioPlaying) "Pausar" else "Reproduzir",
                                                tint = Color.White,
                                                modifier = Modifier.size(30.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Row(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val heights = listOf(14.dp, 24.dp, 36.dp, 20.dp, 30.dp, 16.dp, 28.dp, 38.dp, 22.dp, 32.dp, 18.dp)
                                            heights.forEachIndexed { i, h ->
                                                val barColor = if (isAudioPlaying) {
                                                    if (i % 2 == 0) Color(0xFF25D366) else Color.White
                                                } else {
                                                    Color.White.copy(alpha = 0.4f)
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(h)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(barColor)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (userCaption.isNotBlank()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.Black.copy(alpha = 0.4f)
                                ) {
                                    Text(
                                        text = userCaption,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Image Caption at bottom
            if (currentStatus.contentType == "image" && !currentStatus.caption.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentStatus.caption,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Top Progress Bar & Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Segmented Progress Bars
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    statuses.forEachIndexed { idx, _ ->
                        val segProgress = when {
                            idx < currentIndex -> 1f
                            idx == currentIndex -> progress
                            else -> 0f
                        }
                        LinearProgressIndicator(
                            progress = { segProgress },
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }

                // Header Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }

                    val resolvedAvatar = if (currentStatus.userId == currentUserId && !userAvatarUrl.isNullOrEmpty()) {
                        userAvatarUrl
                    } else {
                        currentStatus.avatarUrl
                    }
                    val resolvedName = if (currentStatus.userId == currentUserId) "Você" else currentStatus.displayName

                    UserAvatarView(
                        avatarUrl = resolvedAvatar,
                        displayName = resolvedName,
                        size = 38.dp,
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = resolvedName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = formatRelativeTime(currentStatus.createdAt),
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp
                        )
                    }

                    if (currentStatus.userId == currentUserId) {
                        IconButton(onClick = { onDeleteStatus(currentStatus.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Apagar Status", tint = DangerRed)
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
        UserAvatarView(
            avatarUrl = conversation.otherAvatarUrl,
            displayName = conversation.otherDisplayName,
            size = 52.dp,
            fontSize = 20.sp
        )

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
                    val previewText = when {
                        conversation.lastMessageContent.isNullOrBlank() -> "Sem mensagens ainda"
                        conversation.lastMessageContent.startsWith("data:audio") ||
                        conversation.lastMessageContent.contains("base64,") ||
                        conversation.lastMessageContent.startsWith("🎤") -> "🎤 Mensagem de voz"
                        conversation.lastMessageContent.startsWith("data:image") -> "📷 Foto"
                        else -> conversation.lastMessageContent
                    }
                    Text(
                        text = previewText,
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
                                text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
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
        UserAvatarView(
            avatarUrl = call.contactAvatarUrl,
            displayName = call.contactDisplayName,
            size = 48.dp,
            fontSize = 18.sp
        )

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

private fun formatRelativeTime(rawTime: String): String {
    return try {
        val date: Date = when {
            rawTime.toLongOrNull() != null -> Date(rawTime.toLong())
            else -> {
                val clean = rawTime.trim()
                val formats = listOf(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS",
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd HH:mm"
                )
                var parsed: Date? = null
                for (fmt in formats) {
                    try {
                        val sdf = SimpleDateFormat(fmt, Locale.US).apply {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }
                        parsed = sdf.parse(clean)
                        if (parsed != null) break
                    } catch (e: Exception) {}
                }
                parsed ?: Date()
            }
        }
        val now = System.currentTimeMillis()
        val diffMs = now - date.time
        val minutes = diffMs / (60 * 1000)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault()).format(date)

        val calNow = java.util.Calendar.getInstance()
        val calDate = java.util.Calendar.getInstance().apply { time = date }
        val isToday = calNow.get(java.util.Calendar.YEAR) == calDate.get(java.util.Calendar.YEAR) &&
                      calNow.get(java.util.Calendar.DAY_OF_YEAR) == calDate.get(java.util.Calendar.DAY_OF_YEAR)
        val isYesterday = calNow.get(java.util.Calendar.YEAR) == calDate.get(java.util.Calendar.YEAR) &&
                          calNow.get(java.util.Calendar.DAY_OF_YEAR) - calDate.get(java.util.Calendar.DAY_OF_YEAR) == 1

        when {
            diffMs in 0..59000 -> "Agora mesmo"
            minutes in 1..59 -> "há ${minutes}m"
            isToday -> "hoje às $timeFormat"
            isYesterday -> "ontem às $timeFormat"
            else -> "$dateFormat às $timeFormat"
        }
    } catch (e: Exception) {
        "Recente"
    }
}
