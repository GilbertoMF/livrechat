package com.livrechat.app.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.livrechat.app.ui.common.PhotoAdjustDialog
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

@Composable
fun CustomCameraDialog(
    onDismiss: () -> Unit,
    showViewOnceOption: Boolean = true,
    initialMode: String = "Foto",
    onMediaCaptured: (file: File, mediaType: String, caption: String, isViewOnce: Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(context, "Permissão de câmera necessária para capturar mídia", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) return

    var lensFacing by remember {
        mutableIntStateOf(
            if (initialMode == "Recado de vídeo") CameraSelector.LENS_FACING_FRONT
            else CameraSelector.LENS_FACING_BACK
        )
    }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var selectedMode by remember { mutableStateOf(initialMode) } // "Vídeo", "Foto", "Recado de vídeo"
    var torchEnabled by remember { mutableStateOf(false) }
    var isViewOnce by remember { mutableStateOf(false) }

    var capturedFile by remember { mutableStateOf<File?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    var captionText by remember { mutableStateOf("") }
    var isCapturing by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    // Timer for video recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    // Cleanup recording and executor on exit
    DisposableEffect(Unit) {
        onDispose {
            try {
                activeRecording?.stop()
                cameraExecutor.shutdown()
            } catch (e: Exception) {}
        }
    }

    // Bind camera based on selectedMode and lensFacing
    fun bindCamera() {
        val provider = cameraProvider ?: return
        val pv = previewViewRef ?: return

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(pv.surfaceProvider)
        }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            provider.unbindAll()

            if (selectedMode == "Vídeo" || selectedMode == "Recado de vídeo") {
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                    .build()
                val vidCap = VideoCapture.withOutput(recorder)
                videoCapture = vidCap
                imageCapture = null

                val cam = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    vidCap
                )
                cameraControl = cam.cameraControl
                if (torchEnabled) {
                    cameraControl?.enableTorch(true)
                }
            } else {
                val capture = ImageCapture.Builder()
                    .setFlashMode(flashMode)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture
                videoCapture = null

                val cam = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    capture
                )
                cameraControl = cam.cameraControl
                if (torchEnabled) {
                    cameraControl?.enableTorch(true)
                }
            }
        } catch (e: Exception) {
            Log.e("CustomCamera", "Falha ao vincular câmera", e)
        }
    }

    LaunchedEffect(cameraProvider, previewViewRef, lensFacing, selectedMode) {
        bindCamera()
    }

    // Gallery Picker launcher with EXIF correction
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val tempFile = File(context.cacheDir, "cam_gallery_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { inStream ->
                        FileOutputStream(tempFile).use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    val exif = ExifInterface(tempFile.absolutePath)
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    val rotationDegrees = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                    val raw = BitmapFactory.decodeFile(tempFile.absolutePath)
                    if (raw != null) {
                        val oriented = if (rotationDegrees != 0f) {
                            val matrix = Matrix().apply { postRotate(rotationDegrees) }
                            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                        } else {
                            raw
                        }
                        val out = FileOutputStream(tempFile)
                        oriented.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        out.flush()
                        out.close()

                        launch(Dispatchers.Main) {
                            capturedFile = tempFile
                            capturedBitmap = oriented
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CustomCamera", "Erro ao carregar da galeria", e)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (isRecording) {
                activeRecording?.stop()
            }
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (capturedBitmap != null && capturedFile != null) {
                // PREVIEW SCREEN AFTER CAPTURE
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                capturedBitmap = null
                                capturedFile = null
                                captionText = ""
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                        }

                        Text(
                            text = "Prévia",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val curr = capturedBitmap ?: return@IconButton
                                    val matrix = Matrix().apply { postRotate(90f) }
                                    val rotated = Bitmap.createBitmap(curr, 0, 0, curr.width, curr.height, matrix, true)
                                    capturedBitmap = rotated
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val f = capturedFile ?: File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                                        val out = FileOutputStream(f)
                                        rotated.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                        out.flush()
                                        out.close()
                                        capturedFile = f
                                    }
                                }
                            ) {
                                Icon(Icons.Default.RotateRight, contentDescription = "Girar 90°", tint = Color.White)
                            }

                            IconButton(
                                onClick = {
                                    showAdjustDialog = true
                                }
                            ) {
                                Icon(Icons.Default.Crop, contentDescription = "Ajustar / Recortar", tint = Color.White)
                            }
                        }
                    }

                    // Image Display
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = capturedBitmap!!.asImageBitmap(),
                            contentDescription = "Foto capturada",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Bottom Bar with Caption Input and Send Button
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = captionText,
                                onValueChange = { captionText = it },
                                placeholder = { Text("Adicione uma legenda...", color = Color.Gray, fontSize = 14.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.DarkGray
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )

                            if (showViewOnceOption) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isViewOnce) Color(0xFF25D366)
                                            else Color.White.copy(alpha = 0.15f)
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            color = if (isViewOnce) Color(0xFF25D366) else Color.White.copy(alpha = 0.6f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            isViewOnce = !isViewOnce
                                            if (isViewOnce) {
                                                Toast.makeText(context, "Foto configurada para visualização única", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "1",
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    val f = capturedFile
                                    if (f != null && f.exists()) {
                                        onMediaCaptured(f, "image", captionText.trim(), isViewOnce)
                                        onDismiss()
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.size(50.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Enviar",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            } else {
                // LIVE CAMERA VIEWFINDER
                AndroidView(
                    factory = { ctx ->
                        val pv = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        previewViewRef = pv

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            cameraProvider = cameraProviderFuture.get()
                        }, ContextCompat.getMainExecutor(ctx))

                        pv
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Top Controls: Close (left), Timer (center if recording), Torch & Flash (right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (isRecording) {
                                activeRecording?.stop()
                            }
                            onDismiss()
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(42.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                    }

                    if (isRecording) {
                        Surface(
                            color = Color(0xFFE53935),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.White, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val mins = recordingSeconds / 60
                                val secs = recordingSeconds % 60
                                Text(
                                    text = String.format("%02d:%02d", mins, secs),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Torch Toggle
                        IconButton(
                            onClick = {
                                torchEnabled = !torchEnabled
                                cameraControl?.enableTorch(torchEnabled)
                            },
                            modifier = Modifier
                                .background(
                                    if (torchEnabled) Color(0xFFFFEB3B).copy(alpha = 0.35f)
                                    else Color.Black.copy(alpha = 0.5f),
                                    CircleShape
                                )
                                .size(42.dp)
                        ) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                contentDescription = "Lanterna",
                                tint = if (torchEnabled) Color(0xFFFFEB3B) else Color.White
                            )
                        }

                        // Flash Mode (for photo mode only)
                        if (selectedMode == "Foto") {
                            IconButton(
                                onClick = {
                                    flashMode = when (flashMode) {
                                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                                        else -> ImageCapture.FLASH_MODE_AUTO
                                    }
                                    imageCapture?.flashMode = flashMode
                                },
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .size(42.dp)
                            ) {
                                val (icon, color) = when (flashMode) {
                                    ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn to Color(0xFFFFEB3B)
                                    ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff to Color.White.copy(alpha = 0.6f)
                                    else -> Icons.Default.FlashAuto to Color.White
                                }
                                Icon(icon, contentDescription = "Flash", tint = color)
                            }
                        }
                    }
                }

                // Bottom Controls: Perfectly balanced 3-item row
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gallery Button (Left)
                        IconButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                .size(50.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Galeria", tint = Color.White)
                        }

                        // Shutter Button (Center)
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .border(4.dp, Color.White, CircleShape)
                                .padding(6.dp)
                                .clip(if (isRecording) RoundedCornerShape(12.dp) else CircleShape)
                                .background(
                                    if (selectedMode == "Vídeo" || selectedMode == "Recado de vídeo" || isRecording)
                                        Color(0xFFE53935)
                                    else Color.White
                                )
                                .clickable(enabled = !isCapturing) {
                                    if (selectedMode == "Vídeo" || selectedMode == "Recado de vídeo") {
                                        if (isRecording) {
                                            activeRecording?.stop()
                                            activeRecording = null
                                        } else {
                                            val vidCap = videoCapture ?: return@clickable
                                            val videoFile = File(context.cacheDir, "cam_vid_${System.currentTimeMillis()}.mp4")
                                            val outputOptions = FileOutputOptions.Builder(videoFile).build()
                                            var pending = vidCap.output.prepareRecording(context, outputOptions)
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                pending = pending.withAudioEnabled()
                                            }
                                            val recording = pending.start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                                                when (recordEvent) {
                                                    is VideoRecordEvent.Start -> {
                                                        isRecording = true
                                                    }
                                                    is VideoRecordEvent.Finalize -> {
                                                        isRecording = false
                                                        if (!recordEvent.hasError()) {
                                                            val mediaType = if (selectedMode == "Recado de vídeo") "video_note" else "video"
                                                            onMediaCaptured(videoFile, mediaType, "", false)
                                                            onDismiss()
                                                        } else {
                                                            Toast.makeText(context, "Erro ao gravar vídeo", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                            activeRecording = recording
                                        }
                                    } else {
                                        val cap = imageCapture ?: return@clickable
                                        isCapturing = true
                                        val photoFile = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                                         cap.takePicture(
                                            outputOptions,
                                            cameraExecutor,
                                            object : ImageCapture.OnImageSavedCallback {
                                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        try {
                                                            val exif = ExifInterface(photoFile.absolutePath)
                                                            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                                                            val rotationDegrees = when (orientation) {
                                                                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                                                                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                                                                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                                                                else -> 0f
                                                            }
                                                            val raw = BitmapFactory.decodeFile(photoFile.absolutePath)
                                                            if (raw != null) {
                                                                val matrix = Matrix()
                                                                if (rotationDegrees != 0f) {
                                                                    matrix.postRotate(rotationDegrees)
                                                                }
                                                                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                                                    matrix.postScale(-1f, 1f)
                                                                }
                                                                val oriented = if (rotationDegrees != 0f || lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                                                    Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                                                                } else {
                                                                    raw
                                                                }
                                                                val out = FileOutputStream(photoFile)
                                                                oriented.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                                                out.flush()
                                                                out.close()

                                                                launch(Dispatchers.Main) {
                                                                    isCapturing = false
                                                                    capturedBitmap = oriented
                                                                    capturedFile = photoFile
                                                                }
                                                            } else {
                                                                launch(Dispatchers.Main) { isCapturing = false }
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e("CustomCamera", "Erro ao processar foto", e)
                                                            launch(Dispatchers.Main) { isCapturing = false }
                                                        }
                                                    }
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    coroutineScope.launch(Dispatchers.Main) {
                                                        isCapturing = false
                                                        Toast.makeText(context, "Erro ao capturar foto: ${exception.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCapturing) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        }

                        // Flip Camera Button (Right)
                        IconButton(
                            onClick = {
                                if (!isRecording) {
                                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                        CameraSelector.LENS_FACING_FRONT
                                    } else {
                                        CameraSelector.LENS_FACING_BACK
                                    }
                                }
                            },
                            enabled = !isRecording,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                .size(50.dp)
                        ) {
                            Icon(Icons.Default.Cameraswitch, contentDescription = "Trocar câmera", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Modes row: "Vídeo", "Foto", "Recado de vídeo"
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("Vídeo", "Foto", "Recado de vídeo").forEach { mode ->
                            val isSelected = selectedMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                                    .clickable(enabled = !isRecording) { selectedMode = mode }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = mode,
                                    color = if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.7f),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            if (showAdjustDialog && capturedBitmap != null) {
                PhotoAdjustDialog(
                    sourceBitmap = capturedBitmap!!,
                    onDismiss = { showAdjustDialog = false },
                    onCropFinished = { adjusted ->
                        capturedBitmap = adjusted
                        showAdjustDialog = false
                        coroutineScope.launch(Dispatchers.IO) {
                            val f = capturedFile ?: File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                            val out = FileOutputStream(f)
                            adjusted.compress(Bitmap.CompressFormat.JPEG, 92, out)
                            out.flush()
                            out.close()
                            capturedFile = f
                        }
                    }
                )
            }
        }
    }
}
