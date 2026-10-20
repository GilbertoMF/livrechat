package com.livrechat.app.offline.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.livrechat.app.offline.data.Mensagem
import com.livrechat.app.offline.data.OfflineDatabase
import com.livrechat.app.offline.data.StatusEntrega
import com.livrechat.app.offline.nearby.MeshMediaHelper
import com.livrechat.app.offline.nearby.NearbyManager
import com.livrechat.app.offline.nearby.NearbyPermissionHelper
import com.livrechat.app.util.LocalAiHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Janela de Bate-Papo P2P Offline Avançada
 * - Histórico persistido localmente no Room
 * - Envio e Recepção de Imagens Ultraleves (< 18KB) via Malha Mesh
 * - Envio e Reprodução de Áudios de Voz AMR-NB na Malha Mesh
 * - Indicadores visuais de Entrega e Nó(s) Intermediários de Roteamento
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
        connectedPeers.any { 
            it.endpointId == contactId || 
            it.deviceName.equals(contactName, ignoreCase = true) ||
            it.deviceName.contains(contactName, ignoreCase = true) ||
            contactName.contains(it.deviceName, ignoreCase = true)
        }
    }

    var isGpsEnabled by remember { mutableStateOf(NearbyPermissionHelper.isLocationEnabled(context)) }
    var isBtEnabled by remember { mutableStateOf(NearbyPermissionHelper.isBluetoothEnabled()) }

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Estado da Gravação de Voz
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingDurationSeconds by remember { mutableIntStateOf(0) }
    var isDeadDropMode by remember { mutableStateOf(false) }
    var showAiSummaryDialog by remember { mutableStateOf(false) }

    // Launcher para Galeria de Imagens
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(stream)
                    stream?.close()
                    if (bitmap != null) {
                        val base64 = MeshMediaHelper.compressBitmapForMesh(bitmap)
                        nearbyManager.sendMediaMessage(
                            recipientId = contactId,
                            recipientName = contactName,
                            mediaType = "image",
                            mediaData = base64,
                            caption = "📷 Foto"
                        )
                        Toast.makeText(context, "Foto comprimida e enviada na malha!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Erro ao carregar foto: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Cronômetro da Gravação de Voz
    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordingDurationSeconds = 0
            while (isRecordingVoice) {
                delay(1000)
                recordingDurationSeconds++
                // Limite de segurança de 10 segundos para caber no pacote de 32KB da malha
                if (recordingDurationSeconds >= 10) {
                    val audioBase64 = MeshMediaHelper.stopVoiceRecording()
                    isRecordingVoice = false
                    if (!audioBase64.isNullOrBlank()) {
                        nearbyManager.sendMediaMessage(
                            recipientId = contactId,
                            recipientName = contactName,
                            mediaType = "audio",
                            mediaData = audioBase64,
                            caption = "🎤 Áudio (${recordingDurationSeconds}s)"
                        )
                        Toast.makeText(context, "Áudio enviado na malha!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        nearbyManager.startMeshNetwork()
    }

    LaunchedEffect(mensagens.size) {
        if (mensagens.isNotEmpty()) {
            listState.animateScrollToItem(mensagens.size - 1)
        }
    }

    val statusText = when {
        isDirectPeerConnected -> "P2P Direto Conectado"
        connectedPeers.isNotEmpty() -> "Malha Mesh (${connectedPeers.size} nós)"
        else -> "Buscando nós..."
    }
    val statusColor = when {
        isDirectPeerConnected -> Color(0xFF00E676)
        connectedPeers.isNotEmpty() -> Color(0xFFA78BFA)
        else -> Color(0xFFFFB74D)
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
                                    .background(statusColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = statusText,
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
                actions = {
                    IconButton(onClick = { showAiSummaryDialog = true }) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Resumo Inteligente On-Device",
                            tint = Color(0xFFFFD54F)
                        )
                    }
                    IconButton(onClick = {
                        nearbyManager.restartMeshNetwork()
                        isGpsEnabled = NearbyPermissionHelper.isLocationEnabled(context)
                        isBtEnabled = NearbyPermissionHelper.isBluetoothEnabled()
                        Toast.makeText(context, "Reconectando nós próximos...", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reconectar")
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
                if (isRecordingVoice) {
                    // BARRA DE GRAVAÇÃO DE VOZ ATIVA
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
                                    .size(12.dp)
                                    .background(Color.Red, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gravando áudio mesh: 00:${recordingDurationSeconds.toString().padStart(2, '0')} (máx 10s)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                MeshMediaHelper.stopVoiceRecording()
                                isRecordingVoice = false
                            }) {
                                Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = {
                                    val audioBase64 = MeshMediaHelper.stopVoiceRecording()
                                    isRecordingVoice = false
                                    if (!audioBase64.isNullOrBlank()) {
                                        nearbyManager.sendMediaMessage(
                                            recipientId = contactId,
                                            recipientName = contactName,
                                            mediaType = "audio",
                                            mediaData = audioBase64,
                                            caption = "🎤 Áudio (${recordingDurationSeconds}s)"
                                        )
                                        Toast.makeText(context, "Áudio enviado na malha!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Enviar Áudio",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else {
                    // BARRA NORMAL DE TEXTO + MÍDIA
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botão de Imagem
                        IconButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.AddPhotoAlternate,
                                contentDescription = "Enviar Foto",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Botão de Microfone / Nota de Voz
                        IconButton(
                            onClick = {
                                val started = MeshMediaHelper.startVoiceRecording(context)
                                if (started) {
                                    isRecordingVoice = true
                                } else {
                                    Toast.makeText(context, "Permissão de microfone necessária", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Gravar Áudio",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Botão de Dead Drop (Autodestruição Confidencial)
                        IconButton(
                            onClick = {
                                isDeadDropMode = !isDeadDropMode
                                Toast.makeText(
                                    context,
                                    if (isDeadDropMode) "🔥 Modo Dead Drop Ativado (Autodestruição ao ler)" else "Modo Normal",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.LocalFireDepartment,
                                contentDescription = "Dead Drop",
                                tint = if (isDeadDropMode) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    text = if (isDeadDropMode) "🔥 Mensagem Dead Drop..." else "Mensagem sem internet...",
                                    fontSize = 13.sp,
                                    color = if (isDeadDropMode) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = {
                                val text = inputText.trim()
                                if (text.isNotBlank()) {
                                    nearbyManager.sendMessage(contactId, contactName, text, isDeadDrop = isDeadDropMode)
                                    inputText = ""
                                    if (isDeadDropMode) isDeadDropMode = false
                                }
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .background(if (isDeadDropMode) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary, CircleShape)
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
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (!isGpsEnabled || !isBtEnabled) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (!isBtEnabled) "Ative o Bluetooth para enviar/receber sem internet." else "No Android 10/11, a Localização (GPS) precisa estar ligada na barra de status.",
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = {
                            val intent = if (!isBtEnabled) {
                                android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            } else {
                                android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            }
                            try { context.startActivity(intent) } catch (e: Exception) {}
                        }) {
                            Text("Ativar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (mensagens.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Envie texto, fotos ou notas de voz offline.\nOs dados serão entregues direto ou através dos nós próximos.",
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

    // DIÁLOGO: RESUMO INTELIGENTE ON-DEVICE (100% LOCAL SEM NUVEM)
    if (showAiSummaryDialog) {
        val summary = remember(mensagens) { LocalAiHelper.summarizeMessages(mensagens) }
        AlertDialog(
            onDismissRequest = { showAiSummaryDialog = false },
            icon = {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFD54F))
            },
            title = {
                Text("Resumo On-Device (IA Local)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Text(
                            text = "Processado 100% no seu aparelho via NLP local, sem envio de dados para servidores ou internet.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (summary.keyTopics.isNotEmpty()) {
                        item {
                            Text("📌 Principais Tópicos:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            summary.keyTopics.forEach { topic ->
                                Text("• $topic", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    if (summary.decisions.isNotEmpty()) {
                        item {
                            Text("🤝 Decisões & Acordos:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF00E676))
                            Spacer(modifier = Modifier.height(4.dp))
                            summary.decisions.forEach { dec ->
                                Text("✔ $dec", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    if (summary.pendingQuestions.isNotEmpty()) {
                        item {
                            Text("❓ Perguntas em Aberto:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFFB74D))
                            Spacer(modifier = Modifier.height(4.dp))
                            summary.pendingQuestions.forEach { q ->
                                Text("? $q", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAiSummaryDialog = false }) {
                    Text("Entendido")
                }
            }
        )
    }
}

@Composable
fun OfflineMessageBubble(
    msg: Mensagem,
    isMine: Boolean
) {
    val context = LocalContext.current
    val dao = remember { OfflineDatabase.getInstance(context).offlineDao() }
    val scope = rememberCoroutineScope()
    var isPlayingAudio by remember { mutableStateOf(false) }
    var isTranscribingAudio by remember { mutableStateOf(false) }
    var transcribedText by remember { mutableStateOf<String?>(null) }
    var showFullImageDialog by remember { mutableStateOf(false) }

    // Estados para Mensagem Dead Drop
    var isPressingDeadDrop by remember { mutableStateOf(false) }
    var deadDropCountdown by remember { mutableIntStateOf(5) }
    var hasRevealedDeadDrop by remember { mutableStateOf(false) }
    var isBurnedLocal by remember { mutableStateOf(msg.isBurned) }

    LaunchedEffect(isPressingDeadDrop) {
        if (isPressingDeadDrop && !isBurnedLocal) {
            hasRevealedDeadDrop = true
            deadDropCountdown = 5
            while (deadDropCountdown > 0 && isPressingDeadDrop) {
                delay(1000)
                deadDropCountdown--
            }
            if (deadDropCountdown == 0) {
                isBurnedLocal = true
                isPressingDeadDrop = false
                scope.launch {
                    dao.markMensagemBurned(msg.idMensagem)
                    dao.deleteMensagem(msg.idMensagem)
                }
            }
        } else if (hasRevealedDeadDrop && !isBurnedLocal) {
            // Soltou o dedo após revelar -> autodestruição imediata!
            isBurnedLocal = true
            scope.launch {
                dao.markMensagemBurned(msg.idMensagem)
                dao.deleteMensagem(msg.idMensagem)
            }
        }
    }

    val timeText = remember(msg.timestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(msg.timestamp))
    }

    // Decodifica Bitmap se for mídia de imagem
    val imageBitmap = remember(msg.mediaData) {
        if (msg.mediaType == "image" && !msg.mediaData.isNullOrBlank()) {
            try {
                val clean = msg.mediaData.substringAfter("base64,")
                val bytes = Base64.decode(clean, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else null
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
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                // INDICADOR DE SALTO E NÓ INTERMEDIÁRIO (MESH RELAY)
                val isRelayed = msg.isMeshRoute || msg.hops > 0 || !msg.relayedVia.isNullOrBlank()
                if (isRelayed) {
                    val hopCount = if (msg.hops > 0) msg.hops else 1
                    val hopText = if (hopCount == 1) "1 salto" else "$hopCount saltos"
                    val label = when {
                        !msg.relayedVia.isNullOrBlank() && !isMine -> "Via nó: ${msg.relayedVia} ($hopText)"
                        !msg.relayedVia.isNullOrBlank() && isMine -> "Encaminhada via ${msg.relayedVia}"
                        !isMine -> "Roteada via nó intermediário ($hopText)"
                        else -> "Roteada via malha mesh ($hopText)"
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isMine) Color.Black.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Hub,
                                contentDescription = "Mesh Relay",
                                tint = if (isMine) Color(0xFFFFD54F) else Color(0xFF00E676),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "🌐 $label",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMine) Color(0xFFFFD54F) else Color(0xFF00E676),
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // CONTEÚDO DA MENSAGEM: DEAD DROP OU MÍDIA NORMAL
                if (isBurnedLocal) {
                    // MENSAGEM AUTODESTRUÍDA (CINZAS)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF334155),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🔥 Mensagem Dead Drop destruída",
                                fontSize = 12.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                } else if (msg.isDeadDrop) {
                    // MENSAGEM DEAD DROP ATIVA: SEGURE PARA REVELAR (5s)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isPressingDeadDrop) Color(0xFF7F1D1D) else Color(0xFF991B1B).copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isPressingDeadDrop = true
                                        tryAwaitRelease()
                                        isPressingDeadDrop = false
                                    }
                                )
                            }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPressingDeadDrop) "🔥 Destruição em ${deadDropCountdown}s..." else "🔥 Dead Drop Confidencial",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFCA5A5)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            if (isPressingDeadDrop) {
                                Text(
                                    text = msg.conteudo,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = "Segure pressionado para ler o segredo",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFCA5A5).copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                } else {
                    // MÍDIA NORMAL
                    when (msg.mediaType) {
                        "image" -> {
                            Column {
                                if (imageBitmap != null) {
                                    Image(
                                        bitmap = imageBitmap,
                                        contentDescription = "Foto na malha",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showFullImageDialog = true },
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp)
                                            .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray)
                                    }
                                }

                                if (msg.conteudo.isNotBlank() && msg.conteudo != "📷 Foto") {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = msg.conteudo,
                                        fontSize = 13.sp,
                                        color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        "audio" -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isMine) Color.Black.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val data = msg.mediaData
                                                if (!data.isNullOrBlank()) {
                                                    isPlayingAudio = true
                                                    MeshMediaHelper.playAudioBytes(context, data) {
                                                        isPlayingAudio = false
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                if (isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = "Ouvir áudio",
                                                tint = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (isPlayingAudio) "▶ Reproduzindo..." else "Nota de voz offline",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "AMR-NB 8kHz • 12.2 kbps",
                                                fontSize = 10.sp,
                                                color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // TRANSCRIÇÃO IA ON-DEVICE
                                if (transcribedText != null) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isMine) Color.Black.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(6.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Transcrição On-Device:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(transcribedText ?: "", fontSize = 12.sp, color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                val data = msg.mediaData
                                                if (!data.isNullOrBlank()) {
                                                    isTranscribingAudio = true
                                                    scope.launch {
                                                        LocalAiHelper.transcribeAudioNote(context, data) { result ->
                                                            isTranscribingAudio = false
                                                            transcribedText = result
                                                        }
                                                    }
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            if (isTranscribingAudio) {
                                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Color(0xFFFFD54F))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Transcrevendo...", fontSize = 10.sp, color = Color(0xFFFFD54F))
                                            } else {
                                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFFFFD54F))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Transcrever", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            Text(
                                text = msg.conteudo,
                                fontSize = 14.sp,
                                color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // TIMESTAMP E STATUS DE ENTREGA
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

    // MODAL DE ZOOM DE IMAGEM
    if (showFullImageDialog && imageBitmap != null) {
        Dialog(onDismissRequest = { showFullImageDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Visualização expandida",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { showFullImageDialog = false }) {
                        Text("Fechar")
                    }
                }
            }
        }
    }
}
