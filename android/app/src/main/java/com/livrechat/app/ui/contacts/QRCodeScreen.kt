package com.livrechat.app.ui.contacts

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.livrechat.app.LivreChatApp
import com.livrechat.app.data.local.entity.ConversationEntity
import com.livrechat.app.ui.theme.BluePrimary
import com.livrechat.app.ui.theme.DarkBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String, String, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = LivreChatApp.instance.preferencesManager
    val apiService = LivreChatApp.instance.apiClient.apiService
    val db = LivreChatApp.instance.database

    val username = prefs.currentUsername ?: "livrechat_user"
    val userId = prefs.currentUserId ?: "00000000"
    val displayName = prefs.currentDisplayName ?: username

    val qrContent = "livrechat://user/$userId?username=$username"

    val qrBitmap = remember(qrContent) {
        generateQrBitmap(qrContent, 512)
    }

    var selectedTab by remember { mutableStateOf(0) } // 0 = Meu Código, 1 = Escanear Código
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(context, "Permissão da câmera necessária para escanear QR Code.", Toast.LENGTH_SHORT).show()
        }
    }

    var isProcessingScan by remember { mutableStateOf(false) }

    fun handleScannedCode(rawCode: String) {
        if (isProcessingScan) return
        isProcessingScan = true

        scope.launch {
            try {
                // Parse URI livrechat://user/{userId}?username={username}
                var targetUserId = ""
                var targetUsername = ""

                if (rawCode.startsWith("livrechat://user/")) {
                    val uri = Uri.parse(rawCode)
                    targetUserId = uri.lastPathSegment ?: ""
                    targetUsername = uri.getQueryParameter("username") ?: ""
                } else if (rawCode.contains("/")) {
                    val parts = rawCode.split("/")
                    targetUserId = parts.last()
                } else {
                    targetUserId = rawCode.trim()
                }

                if (targetUserId == userId) {
                    Toast.makeText(context, "Este é o seu próprio código QR!", Toast.LENGTH_SHORT).show()
                    isProcessingScan = false
                    return@launch
                }

                Toast.makeText(context, "Contato detectado! Abrindo conversa...", Toast.LENGTH_SHORT).show()

                val res = apiService.getOrCreateConversation(mapOf("targetUserId" to targetUserId))
                if (res.isSuccessful && res.body() != null) {
                    val conv = res.body()!!
                    val finalDisplayName = conv.otherUser?.displayName?.takeIf { it.isNotBlank() } ?: targetUsername.ifBlank { "Contato" }

                    withContext(Dispatchers.IO) {
                        db.conversationDao().insertOrUpdate(
                            ConversationEntity(
                                id = conv.conversationId,
                                otherUserId = targetUserId,
                                otherUsername = targetUsername,
                                otherDisplayName = finalDisplayName,
                                otherAvatarUrl = conv.otherUser?.avatarUrl,
                                otherPublicKey = conv.otherUser?.publicKey,
                                lastMessageContent = "Iniciou conversa via QR Code",
                                lastMessageTimestamp = System.currentTimeMillis(),
                                unreadCount = 0
                            )
                        )
                    }

                    onNavigateToChat(conv.conversationId, targetUserId, finalDisplayName)
                } else {
                    Toast.makeText(context, "Usuário não encontrado.", Toast.LENGTH_SHORT).show()
                    isProcessingScan = false
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao conectar com contato: ${e.message}", Toast.LENGTH_SHORT).show()
                isProcessingScan = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Código QR") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs: Meu Código | Escanear Código
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Meu Código") },
                    icon = { Icon(Icons.Default.QrCode, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        if (!hasCameraPermission) {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    text = { Text("Escanear Código") },
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) }
                )
            }

            if (selectedTab == 0) {
                // TAB 0: MEU CÓDIGO
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = displayName,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "@$username",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            qrBitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier.size(240.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Aponte a câmera do LivreChat para iniciar uma conversa instantânea",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Seu ID de Usuário", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = userId,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("LivreChat ID", userId))
                                Toast.makeText(context, "ID copiado!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar ID", tint = BluePrimary)
                            }
                        }
                    }
                }
            } else {
                // TAB 1: ESCANEAR CÓDIGO VIA CÂMERA
                if (!hasCameraPermission) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = BluePrimary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Permissão da Câmera Necessária",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Para escanear códigos QR de amigos e iniciar conversas, permita o acesso à câmera.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                            ) {
                                Text("Permitir Câmera")
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CameraQrScannerView(
                            onCodeScanned = { code ->
                                handleScannedCode(code)
                            }
                        )

                        // Viewfinder Overlay
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Aponte para o código QR do seu amigo",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Scanner Square
                            Box(
                                modifier = Modifier
                                    .size(260.dp)
                                    .border(3.dp, BluePrimary, RoundedCornerShape(16.dp))
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            if (isProcessingScan) {
                                CircularProgressIndicator(color = BluePrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraQrScannerView(
    onCodeScanned: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val reader = MultiFormatReader()
                var hasScanned = false

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (hasScanned) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    try {
                        val buffer = imageProxy.planes[0].buffer
                        val data = ByteArray(buffer.remaining())
                        buffer.get(data)

                        val width = imageProxy.width
                        val height = imageProxy.height
                        val source = PlanarYUVLuminanceSource(
                            data, width, height, 0, 0, width, height, false
                        )
                        val bitmap = BinaryBitmap(HybridBinarizer(source))
                        val result = reader.decodeWithState(bitmap)

                        if (result != null && result.text.isNotBlank()) {
                            hasScanned = true
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                onCodeScanned(result.text)
                            }
                        }
                    } catch (e: Exception) {
                        // Não é QR code neste frame
                    } finally {
                        imageProxy.close()
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    // Erro ao iniciar câmera
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
