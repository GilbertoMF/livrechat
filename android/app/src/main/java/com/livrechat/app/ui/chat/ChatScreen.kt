package com.livrechat.app.ui.chat

import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.zIndex
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
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.EditText
import androidx.core.view.ViewCompat
import com.livrechat.app.LivreChatApp
import com.livrechat.app.util.TimeUtils
import coil.compose.AsyncImage
import com.livrechat.app.data.local.model.ReactionUtils
import com.livrechat.app.data.remote.model.GroupMemberDto
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrechat.app.data.local.entity.MessageEntity
import com.livrechat.app.media.AudioPlayerHelper
import com.livrechat.app.media.AudioRecorderHelper
import com.livrechat.app.ui.theme.BlueAccent
import com.livrechat.app.ui.theme.DangerRed
import com.livrechat.app.ui.common.UserAvatarView
import com.livrechat.app.sync.ContactSyncHelper
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
import android.media.MediaMetadataRetriever
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
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val isContactRecordingAudio by viewModel.isContactRecordingAudio.collectAsState()
    val recordingAudioMap by viewModel.recordingAudioStatus.collectAsState()
    val isRecordingAudio = isContactRecordingAudio || (recordingAudioMap[conversationId] == true)
    val chatErrorMessage by viewModel.chatErrorMessage.collectAsState()

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
                viewModel.sendRecordingAudio(false, otherUserId)
                recordingAmplitudes.clear()
            }
            viewModel.sendTyping(false, otherUserId)
            viewModel.unselectConversation()
        }
    }

    var initialScrollCompleted by remember { mutableStateOf(false) }
    var unreadBelowCount by remember { mutableIntStateOf(0) }
    var showScrollToBottomButton by remember { mutableStateOf(false) }
    var showEmojiStickerSheet by remember { mutableStateOf(false) }

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
    val lastSeenUsers by app.connectionManager.lastSeenUsers.collectAsState()
    val typingUserMap by app.connectionManager.typingUserByConv.collectAsState()
    val recordingUserMap by app.connectionManager.recordingUserByConv.collectAsState()
    val activeTypingUser = typingUserMap[conversationId]
    val activeRecordingUser = recordingUserMap[conversationId]
    var isContactBlocked by remember(otherUserId) { mutableStateOf(prefs.isUserBlocked(otherUserId)) }
    val isContactOnline = if (isContactBlocked) false else (onlineUsers[otherUserId] == true)
    val contactLastSeen = if (isContactBlocked) null else lastSeenUsers[otherUserId]

    LaunchedEffect(otherUserId) {
        if (otherUserId.isNotBlank()) {
            isContactBlocked = prefs.isUserBlocked(otherUserId)
            try {
                val blockedRes = app.apiClient.apiService.getBlockedUsers()
                if (blockedRes.isSuccessful && blockedRes.body() != null) {
                    val isBlockedOnServer = blockedRes.body()!!.any { it.id == otherUserId }
                    prefs.setUserBlocked(otherUserId, isBlockedOnServer)
                    isContactBlocked = isBlockedOnServer
                }
            } catch (e: Exception) {}
            if (!isContactBlocked) {
                app.connectionManager.queryUserStatus(otherUserId)
            }
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

    val scrollToBottomSmoothly: () -> Unit = {
        scope.launch {
            if (messages.isNotEmpty()) {
                val lastIdx = messages.size - 1
                listState.scrollToItem(lastIdx)
                delay(60)
                listState.animateScrollToItem(lastIdx)
                delay(120)
                listState.scrollToItem(lastIdx)
                delay(150)
                listState.animateScrollToItem(lastIdx)
                delay(180)
                listState.scrollToItem(lastIdx)
                showScrollToBottomButton = false
                unreadBelowCount = 0
            }
        }
    }

    val isImeVisible = WindowInsets.isImeVisible
    val imeBottom = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)
    LaunchedEffect(isImeVisible, imeBottom) {
        if ((isImeVisible || imeBottom > 0) && messages.isNotEmpty()) {
            val lastIdx = messages.size - 1
            delay(40)
            listState.scrollToItem(lastIdx)
            delay(120)
            listState.animateScrollToItem(lastIdx)
            delay(150)
            listState.scrollToItem(lastIdx)
            delay(150)
            listState.animateScrollToItem(lastIdx)
            delay(180)
            listState.scrollToItem(lastIdx)
            showScrollToBottomButton = false
            unreadBelowCount = 0
        }
    }

    LaunchedEffect(showEmojiStickerSheet) {
        if (messages.isNotEmpty()) {
            val lastIdx = messages.size - 1
            delay(40)
            listState.scrollToItem(lastIdx)
            delay(100)
            listState.animateScrollToItem(lastIdx)
            delay(140)
            listState.scrollToItem(lastIdx)
            delay(160)
            listState.animateScrollToItem(lastIdx)
            delay(180)
            listState.scrollToItem(lastIdx)
            showScrollToBottomButton = false
            unreadBelowCount = 0
        }
    }

    var clipboardImageUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        try {
            val clipManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            if (clipManager != null && clipManager.hasPrimaryClip()) {
                val clip = clipManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val item = clip.getItemAt(0)
                    val uri = item.uri
                    val desc = clipManager.primaryClipDescription
                    if (uri != null && (desc?.hasMimeType("image/*") == true || context.contentResolver.getType(uri)?.startsWith("image/") == true)) {
                        clipboardImageUri = uri
                    }
                }
            }
        } catch (e: Exception) {}
    }

    fun handlePastedImage(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                val scaled = MediaFileManager.loadGalleryImageCorrected(context, uri, 1280)
                if (scaled != null) {
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                    val bytes = baos.toByteArray()
                    val localFile = MediaFileManager.saveBytesToLocalFile(context, bytes, "paste_${System.currentTimeMillis()}.jpg")
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val dataUri = "data:image/jpeg;base64,$b64"
                    viewModel.sendMediaMessage(
                        conversationId = conversationId,
                        content = localFile.absolutePath,
                        networkPayload = dataUri,
                        messageType = "image",
                        fileName = "imagem_colada.jpg",
                        fileSize = bytes.size.toLong(),
                        targetUserId = otherUserId
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Imagem colada e enviada!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Não foi possível carregar a imagem colada", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatScreen", "Erro ao processar imagem colada", e)
            }
        }
    }

    val currentConv by remember(conversationId) {
        app.database.conversationDao().getConversationByIdFlow(conversationId)
    }.collectAsState(initial = null)
    val savedContacts by app.database.contactDao().getAllContactsFlow().collectAsState(initial = emptyList())
    var groupMembers by remember(conversationId) { mutableStateOf<List<GroupMemberDto>>(emptyList()) }
    val resolvedUserNames = remember { mutableStateMapOf<String, String>() }
    val resolvedSenderAvatars = remember { mutableStateMapOf<String, String?>() }
    var viewingMemberUserId by remember { mutableStateOf<String?>(null) }

    val isCurrentUserAdmin = remember(groupMembers, prefs.currentUserId) {
        groupMembers.any { it.id == prefs.currentUserId && it.isAdmin }
    }

    LaunchedEffect(conversationId, currentConv?.isGroup) {
        ContactSyncHelper.autoSyncIfPermitted(context)
        if (currentConv?.isGroup == true) {
            scope.launch(Dispatchers.IO) {
                try {
                    val res = app.apiClient.apiService.getGroupMembers(conversationId)
                    if (res.isSuccessful && res.body() != null) {
                        val members = res.body()!!
                        withContext(Dispatchers.Main) {
                            groupMembers = members
                        }
                    }
                } catch (e: Exception) {
                    Log.w("ChatScreen", "Erro ao carregar membros do grupo: ${e.message}")
                }
            }
        }
    }

    val resolveSenderName: (String) -> String = remember(savedContacts, groupMembers, resolvedUserNames) {
        { senderId ->
            if (senderId == prefs.currentUserId) {
                "Você"
            } else {
                // 1. Check custom name set by user in preferences
                val customName = prefs.getContactCustomName(senderId)
                if (!customName.isNullOrBlank()) {
                    customName
                } else {
                    // 2. Find in group members or saved contacts to get candidate username / phone
                    val memberMatch = groupMembers.firstOrNull { it.id == senderId || it.username == senderId }
                    val contactMatch = savedContacts.firstOrNull { it.contactUserId == senderId || it.id == senderId || it.username == senderId }

                    val rawPhoneOrUser = memberMatch?.username ?: contactMatch?.username ?: senderId

                    // 3. Look up in Android Contacts by phone/number (WhatsApp style)
                    val phoneBookName = ContactSyncHelper.getContactNameByPhoneOrUsername(context, rawPhoneOrUser)

                    if (!phoneBookName.isNullOrBlank()) {
                        phoneBookName
                    } else if (!contactMatch?.displayName.isNullOrBlank()) {
                        contactMatch!!.displayName
                    } else if (memberMatch != null && memberMatch.displayName.isNotBlank()) {
                        memberMatch.displayName
                    } else if (memberMatch != null && memberMatch.username.isNotBlank()) {
                        memberMatch.username
                    } else if (resolvedUserNames.containsKey(senderId)) {
                        resolvedUserNames[senderId] ?: "Membro"
                    } else {
                        // Background fetch for unknown sender
                        scope.launch(Dispatchers.IO) {
                            try {
                                val profileRes = app.apiClient.apiService.getUserProfile(senderId)
                                if (profileRes.isSuccessful && profileRes.body() != null) {
                                    val u = profileRes.body()!!
                                    val resolved = u.displayName?.takeIf { it.isNotBlank() } ?: u.username
                                    withContext(Dispatchers.Main) {
                                        resolvedUserNames[senderId] = resolved
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                        rawPhoneOrUser.takeIf { it.isNotBlank() } ?: "Membro"
                    }
                }
            }
        }
    }

    val resolveSenderAvatar: (String) -> String? = remember(savedContacts, groupMembers, resolvedSenderAvatars) {
        { senderId ->
            if (senderId == prefs.currentUserId) {
                prefs.userAvatarUrl
            } else {
                val memberMatch = groupMembers.firstOrNull { it.id == senderId || it.username == senderId }
                val contactMatch = savedContacts.firstOrNull { it.contactUserId == senderId || it.id == senderId || it.username == senderId }
                val rawPhoneOrUser = memberMatch?.username ?: contactMatch?.username ?: senderId

                // 1. Phonebook photo
                val phoneBookPhoto = ContactSyncHelper.getContactPhotoByPhoneOrUsername(context, rawPhoneOrUser)
                if (!phoneBookPhoto.isNullOrBlank()) {
                    phoneBookPhoto
                } else if (!contactMatch?.avatarUrl.isNullOrBlank()) {
                    contactMatch!!.avatarUrl
                } else if (!memberMatch?.avatarUrl.isNullOrBlank()) {
                    memberMatch!!.avatarUrl
                } else if (resolvedSenderAvatars.containsKey(senderId)) {
                    resolvedSenderAvatars[senderId]
                } else {
                    // Background fetch
                    scope.launch(Dispatchers.IO) {
                        try {
                            val profileRes = app.apiClient.apiService.getUserProfile(senderId)
                            if (profileRes.isSuccessful && profileRes.body() != null) {
                                val u = profileRes.body()!!
                                withContext(Dispatchers.Main) {
                                    resolvedSenderAvatars[senderId] = u.avatarUrl
                                }
                            }
                        } catch (e: Exception) {}
                    }
                    null
                }
            }
        }
    }
    var isConvLocked by remember { mutableStateOf(prefs.isConversationLocked(conversationId)) }
    var showMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showContactProfile by remember { mutableStateOf(false) }
    var showCryptoInfoDialog by remember { mutableStateOf(false) }
    var selectedMessageForAction by remember { mutableStateOf<MessageEntity?>(null) }
    var messageActionTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var replyingToMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var showCameraDialog by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    var editTextRef by remember { mutableStateOf<EditText?>(null) }
    val showKeyboardAndFocusInput = {
        try {
            val et = editTextRef
            if (et != null) {
                et.requestFocus()
                val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            } else {
                keyboardController?.show()
            }
        } catch (e: Exception) {
            keyboardController?.show()
        }
        scrollToBottomSmoothly()
    }
    val hideKeyboard = {
        try {
            val et = editTextRef
            if (et != null) {
                val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(et.windowToken, 0)
            }
            keyboardController?.hide()
        } catch (e: Exception) {
            keyboardController?.hide()
        }
    }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var viewingImageBase64 by remember { mutableStateOf<String?>(null) }
    var viewingVideoMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var viewingViewOnceMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var cameraInitialMode by remember { mutableStateOf("Foto") }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showUnblockDialog by remember { mutableStateOf(false) }
    var currentWallpaper by remember(conversationId) {
        mutableStateOf(prefs.getChatWallpaper(conversationId))
    }

    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val wpDir = File(context.filesDir, "wallpapers").apply { mkdirs() }
                    val destFile = File(wpDir, "wp_${conversationId}_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val path = destFile.absolutePath
                    prefs.setChatWallpaper(conversationId, path)
                    withContext(Dispatchers.Main) {
                        currentWallpaper = path
                        Toast.makeText(context, "Papel de parede atualizado!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Erro ao carregar imagem", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val scaled = MediaFileManager.loadGalleryImageCorrected(context, uri, 1280)
                    if (scaled != null) {
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
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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

    if (showUnblockDialog) {
        AlertDialog(
            onDismissRequest = { showUnblockDialog = false },
            title = { Text("Desbloquear contato?", fontWeight = FontWeight.Bold) },
            text = { Text("Deseja desbloquear este contato para voltar a trocar mensagens e fazer chamadas?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnblockDialog = false
                    scope.launch(Dispatchers.IO) {
                        try {
                            app.apiClient.apiService.unblockUser(otherUserId)
                            prefs.setUserBlocked(otherUserId, false)
                            withContext(Dispatchers.Main) {
                                isContactBlocked = false
                                Toast.makeText(context, "Contato desbloqueado", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Erro ao desbloquear contato", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }) {
                    Text("Desbloquear", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnblockDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showWallpaperDialog) {
        var applyToAll by remember { mutableStateOf(false) }
        val presetColors = listOf(
            "#0b141a" to "WhatsApp Escuro",
            "#111b21" to "Carvão",
            "#0f172a" to "Ardósia",
            "#1e1b4b" to "Índigo",
            "#064e3b" to "Verde Floresta",
            "#450a0a" to "Vinho",
            "#efeae2" to "WhatsApp Claro",
            "#f1f5f9" to "Neve",
            "#e0f2fe" to "Azul Suave"
        )

        AlertDialog(
            onDismissRequest = { showWallpaperDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Papel de Parede", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Personalize o fundo das conversas com desenhos, padrões exclusivos, cores ou foto da galeria:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Padrões com Desenhos (Estilo WhatsApp):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (preset in WALLPAPER_PATTERNS) {
                            val isSelected = currentWallpaper == preset.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = preset.previewColor,
                                border = if (isSelected) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(72.dp)
                                    .clickable {
                                        if (applyToAll) {
                                            prefs.setChatWallpaper(null, preset.id)
                                        }
                                        prefs.setChatWallpaper(conversationId, preset.id)
                                        currentWallpaper = preset.id
                                        showWallpaperDialog = false
                                        Toast.makeText(context, "${preset.name} aplicado!", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Box(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                                    Text(
                                        text = preset.name,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.BottomStart)
                                    )
                                    if (isSelected) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp).align(Alignment.TopEnd)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            showWallpaperDialog = false
                            wallpaperPickerLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Escolher da Galeria")
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Cores Sólidas:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for ((hex, _) in presetColors) {
                            val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray }
                            val isSelected = currentWallpaper == hex
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        if (applyToAll) {
                                            prefs.setChatWallpaper(null, hex)
                                        }
                                        prefs.setChatWallpaper(conversationId, hex)
                                        currentWallpaper = hex
                                        showWallpaperDialog = false
                                        Toast.makeText(context, "Cor aplicada!", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (hex.startsWith("#e") || hex.startsWith("#f")) Color.Black else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { applyToAll = !applyToAll },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = applyToAll,
                            onCheckedChange = { applyToAll = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Aplicar a todas as conversas", fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!currentWallpaper.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                if (applyToAll) {
                                    prefs.setChatWallpaper(null, null)
                                }
                                prefs.setChatWallpaper(conversationId, null)
                                currentWallpaper = null
                                showWallpaperDialog = false
                                Toast.makeText(context, "Fundo padrão restaurado", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restaurar Padrão", color = DangerRed)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWallpaperDialog = false }) {
                    Text("Fechar")
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
        val isGroup = currentConv?.isGroup == true
        ContactProfileDialog(
            conversationId = conversationId,
            otherUserId = otherUserId,
            otherDisplayName = otherDisplayName,
            otherUsername = currentConv?.otherUsername ?: "",
            otherAvatarUrl = currentConv?.otherAvatarUrl,
            messages = messages,
            isLocked = isConvLocked,
            isGroup = isGroup,
            groupTitle = currentConv?.groupTitle,
            groupAvatar = currentConv?.groupAvatar,
            onDismiss = { showContactProfile = false },
            onLeaveGroup = {
                showContactProfile = false
                scope.launch(Dispatchers.IO) {
                    try {
                        val currentUserId = prefs.currentUserId
                        if (currentUserId != null) {
                            app.apiClient.apiService.removeGroupMember(conversationId, currentUserId)
                        }
                        app.database.conversationDao().deleteConversation(conversationId)
                    } catch (e: Exception) {}
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Você saiu do grupo", Toast.LENGTH_SHORT).show()
                        onNavigateBack()
                    }
                }
            },
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

    if (viewingMemberUserId != null) {
        val targetId = viewingMemberUserId!!
        val memberMatch = groupMembers.firstOrNull { it.id == targetId || it.username == targetId }
        val contactMatch = savedContacts.firstOrNull { it.contactUserId == targetId || it.id == targetId }
        val resolvedName = resolveSenderName(targetId)
        val resolvedAvatar = resolveSenderAvatar(targetId)
        val username = memberMatch?.username ?: contactMatch?.username ?: ""

        ContactProfileDialog(
            conversationId = "",
            otherUserId = targetId,
            otherDisplayName = resolvedName,
            otherUsername = username,
            otherAvatarUrl = resolvedAvatar,
            messages = emptyList(),
            isLocked = false,
            isGroup = false,
            groupTitle = null,
            groupAvatar = null,
            onDismiss = { viewingMemberUserId = null },
            onLeaveGroup = {},
            onStartCall = {
                viewingMemberUserId = null
                onStartCall(
                    targetId,
                    resolvedName,
                    username,
                    resolvedAvatar
                )
            },
            onToggleLock = {},
            onClearChat = {},
            onBlockContact = {
                scope.launch(Dispatchers.IO) {
                    try {
                        app.apiClient.apiService.blockUser(mapOf("blockedUserId" to targetId, "targetUserId" to targetId))
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
                    val headerTitle = if (isGroup) {
                        currentConv?.groupTitle ?: "Grupo"
                    } else {
                        prefs.getContactCustomName(otherUserId) ?: currentConv?.otherDisplayName ?: otherDisplayName
                    }
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
                            if (isGroup) {
                                if (activeRecordingUser != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = null,
                                            tint = Color(0xFF25D366),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "$activeRecordingUser está gravando áudio...",
                                            fontSize = 12.sp,
                                            color = Color(0xFF25D366),
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else if (activeTypingUser != null) {
                                    Text(
                                        text = "$activeTypingUser está digitando...",
                                        fontSize = 12.sp,
                                        color = Color(0xFF25D366),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    val memberSummary = if (groupMembers.isNotEmpty()) {
                                        val names = groupMembers.map { m ->
                                            if (m.id == prefs.currentUserId) "Você"
                                            else resolveSenderName(m.id)
                                        }.take(4).joinToString(", ")
                                        if (groupMembers.size > 4) "$names, +${groupMembers.size - 4}" else names
                                    } else {
                                        "Toque aqui para dados do grupo"
                                    }
                                    Text(
                                        text = memberSummary,
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else if (isRecordingAudio || activeRecordingUser != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = Color(0xFF25D366),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "gravando áudio...",
                                        fontSize = 12.sp,
                                        color = Color(0xFF25D366),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else if (isTyping || activeTypingUser != null) {
                                Text(
                                    text = "digitando...",
                                    fontSize = 12.sp,
                                    color = Color(0xFF25D366),
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (isContactOnline) {
                                Text(
                                    text = "Online",
                                    fontSize = 12.sp,
                                    color = Color(0xFF4CAF50)
                                )
                            } else {
                                Text(
                                    text = formatChatLastSeen(contactLastSeen),
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    val isGroup = currentConv?.isGroup == true
                    if (isGroup) {
                        IconButton(onClick = {
                            showContactProfile = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "Adicionar pessoas",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        com.livrechat.app.ui.common.TooltipIconButton(
                            tooltip = "Chamada de Voz WebRTC",
                            onClick = {
                                if (isContactBlocked) {
                                    Toast.makeText(context, "Desbloqueie o contato para fazer chamadas", Toast.LENGTH_SHORT).show()
                                    return@TooltipIconButton
                                }
                                onStartCall(
                                    otherUserId,
                                    otherDisplayName,
                                    currentConv?.otherUsername ?: "",
                                    currentConv?.otherAvatarUrl
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Chamada de Voz WebRTC",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Box {
                        com.livrechat.app.ui.common.TooltipIconButton(
                            tooltip = "Opções da conversa",
                            onClick = { showMenu = true }
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Opções")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isGroup) "Dados do grupo" else "Dados do contato") },
                                onClick = {
                                    showMenu = false
                                    showContactProfile = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isGroup) Icons.Default.Group else Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                            if (isGroup) {
                                DropdownMenuItem(
                                    text = { Text("Adicionar pessoas") },
                                    onClick = {
                                        showMenu = false
                                        showContactProfile = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Papel de parede") },
                                onClick = {
                                    showMenu = false
                                    showWallpaperDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                                    Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (isContactBlocked) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 4.dp,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showUnblockDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Você bloqueou este contato. Toque para desbloquear.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else if (currentConv?.isGroup == true && currentConv?.adminOnlyMessage == true && !isCurrentUserAdmin) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 4.dp,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Apenas os administradores podem enviar mensagens.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
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
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (replyingToMessage!!.isOutgoing) "Você" else if (currentConv?.isGroup == true) resolveSenderName(replyingToMessage!!.senderId) else otherDisplayName,
                                        color = MaterialTheme.colorScheme.primary,
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
                                viewModel.sendRecordingAudio(false, otherUserId)
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
                                viewModel.sendRecordingAudio(false, otherUserId)
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
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
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
                    // Clipboard Copied Image Pill Banner
                    if (clipboardImageUri != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    AsyncImage(
                                        model = clipboardImageUri,
                                        contentDescription = "Prévia da imagem copiada",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Imagem na Área de Transferência",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Toque em Colar para enviar na conversa",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Button(
                                    onClick = {
                                        val uriToSend = clipboardImageUri
                                        clipboardImageUri = null
                                        if (uriToSend != null) {
                                            handlePastedImage(uriToSend)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Colar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = { clipboardImageUri = null },
                                    modifier = Modifier.size(26.dp).padding(start = 4.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Fechar", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Standard Text & Mic Mode with Rich Image Paste Support
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val textColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
                        val hintColorArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    if (showEmojiStickerSheet) {
                                        showEmojiStickerSheet = false
                                        showKeyboardAndFocusInput()
                                    } else {
                                        hideKeyboard()
                                        showEmojiStickerSheet = true
                                    }
                                    scrollToBottomSmoothly()
                                }) {
                                    Icon(
                                        imageVector = if (showEmojiStickerSheet) Icons.Default.Keyboard else Icons.Default.SentimentSatisfied,
                                        contentDescription = if (showEmojiStickerSheet) "Teclado" else "Emojis e Figurinhas",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                AndroidView(
                                    factory = { ctx ->
                                        val et = EditText(ctx)
                                        editTextRef = et
                                        et.background = null
                                        et.hint = "Mensagem"
                                        et.setHintTextColor(hintColorArgb)
                                        et.setTextColor(textColorArgb)
                                        et.textSize = 15f
                                        et.maxLines = 4
                                        et.isSingleLine = false
                                        et.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NONE
                                        et.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                                                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                                                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE

                                        // Rich content receiver for Gboard clipboard and system clipboard
                                        ViewCompat.setOnReceiveContentListener(
                                            et,
                                            arrayOf("image/*", "image/png", "image/jpeg", "image/webp", "image/gif")
                                        ) { _, payload ->
                                            val split = payload.partition { it.uri != null }
                                            val img = split.first
                                            val rem = split.second
                                            if (img != null && img.clip.itemCount > 0) {
                                                val uri = img.clip.getItemAt(0)?.uri
                                                if (uri != null) {
                                                    handlePastedImage(uri)
                                                }
                                            }
                                            rem
                                        }

                                        et.addTextChangedListener(object : android.text.TextWatcher {
                                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                                val str = s?.toString() ?: ""
                                                if (str != textInput) {
                                                    textInput = str
                                                    if (str.isNotBlank()) {
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
                                                }
                                            }
                                            override fun afterTextChanged(s: android.text.Editable?) {}
                                        })

                                        et.setOnFocusChangeListener { _, hasFocus ->
                                            if (hasFocus) {
                                                if (showEmojiStickerSheet) {
                                                    showEmojiStickerSheet = false
                                                }
                                                scrollToBottomSmoothly()
                                            }
                                        }
                                        et.setOnClickListener {
                                            if (showEmojiStickerSheet) {
                                                showEmojiStickerSheet = false
                                            }
                                            scrollToBottomSmoothly()
                                        }
                                        et
                                    },
                                    update = { et: EditText ->
                                        editTextRef = et
                                        if (et.text.toString() != textInput) {
                                            et.setText(textInput)
                                            et.setSelection(et.text?.length ?: 0)
                                        }
                                        et.setTextColor(textColorArgb)
                                        et.setHintTextColor(hintColorArgb)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    com.livrechat.app.ui.common.TooltipIconButton(
                                        tooltip = "Anexar arquivo ou mídia",
                                        onClick = { showAttachmentSheet = true }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AttachFile,
                                            contentDescription = "Anexar",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (textInput.isBlank()) {
                                        com.livrechat.app.ui.common.TooltipIconButton(
                                            tooltip = "Tirar foto rápida",
                                            onClick = {
                                                cameraInitialMode = "Foto"
                                                showCameraDialog = true
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CameraAlt,
                                                contentDescription = "Câmera",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        com.livrechat.app.ui.common.TooltipIconButton(
                                            tooltip = "Recado de Vídeo (Bolinha)",
                                            onClick = {
                                                cameraInitialMode = "Recado de vídeo"
                                                showCameraDialog = true
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Videocam,
                                                contentDescription = "Recado de Vídeo",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

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
                                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
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
                                        viewModel.sendRecordingAudio(true, otherUserId)
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
                                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
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
                            onDismiss = {
                                showEmojiStickerSheet = false
                                scrollToBottomSmoothly()
                            },
                            onEmojiSelected = { emoji ->
                                textInput += emoji
                                scrollToBottomSmoothly()
                            },
                            onSendSticker = { stickerContent ->
                                viewModel.sendSticker(conversationId, stickerContent, targetUserId = otherUserId)
                                scrollToBottomSmoothly()
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
}
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Version incompatibility / Chat Error Banner
            if (!chatErrorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .align(Alignment.TopCenter)
                        .zIndex(20f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Aviso de Compatibilidade",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = chatErrorMessage!!,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.clearChatError() },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Fechar",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Custom chat wallpaper
            if (!currentWallpaper.isNullOrBlank()) {
                if (currentWallpaper!!.startsWith("pattern:")) {
                    ChatWallpaperCanvas(currentWallpaper!!, modifier = Modifier.fillMaxSize())
                } else if (currentWallpaper!!.startsWith("#")) {
                    val color = try {
                        Color(android.graphics.Color.parseColor(currentWallpaper))
                    } catch (e: Exception) {
                        null
                    }
                    if (color != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(color)
                        )
                    }
                } else {
                    val file = File(currentWallpaper!!)
                    if (file.exists() && file.length() > 0L) {
                        val bitmap = remember(currentWallpaper) {
                            try {
                                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Papel de parede",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.2f))
                            )
                        }
                    }
                }
            }

            // Pinned Message Banner
            if (!currentConv?.pinnedMessageId.isNullOrBlank()) {
                val pinnedId = currentConv?.pinnedMessageId!!
                val pinnedSnippet = currentConv?.pinnedMessageSnippet ?: "Mensagem Fixada"
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 6.dp,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .align(Alignment.TopCenter)
                        .zIndex(15f)
                        .clickable {
                            val targetIndex = messages.indexOfFirst { it.id == pinnedId }
                            if (targetIndex >= 0) {
                                scope.launch {
                                    listState.animateScrollToItem(targetIndex)
                                }
                            } else {
                                Toast.makeText(context, "Mensagem fixada não encontrada no histórico visível", Toast.LENGTH_SHORT).show()
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(26.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Mensagem Fixada",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mensagem Fixada",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = pinnedSnippet,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.unpinMessage(conversationId, pinnedId)
                                Toast.makeText(context, "Mensagem desafixada", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Desafixar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(
                    top = if (!currentConv?.pinnedMessageId.isNullOrBlank()) 56.dp else 8.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        audioPlayerHelper = audioPlayerHelper,
                        onLongPress = { messageActionTarget = it },
                        onSwipeToReply = {
                            replyingToMessage = it
                            showKeyboardAndFocusInput()
                        },
                        onReactionClick = { viewModel.toggleReaction(it.id, "❤️") },
                        onImageClick = { viewingImageBase64 = it },
                        onVideoClick = { viewingVideoMessage = it },
                        onViewOnceClick = { viewingViewOnceMessage = it },
                        isGroup = currentConv?.isGroup == true,
                        senderDisplayName = if (currentConv?.isGroup == true) resolveSenderName(message.senderId) else "",
                        senderAvatarUrl = if (currentConv?.isGroup == true) resolveSenderAvatar(message.senderId) else null,
                        onSenderClick = { senderId ->
                            if (senderId != prefs.currentUserId) {
                                viewingMemberUserId = senderId
                            }
                        }
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
                    contentColor = MaterialTheme.colorScheme.primary,
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
                                containerColor = MaterialTheme.colorScheme.primary,
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

                    AttachmentItem(
                        icon = Icons.Default.PlayCircle,
                        label = "Recado Vídeo",
                        color = Color(0xFF00E676)
                    ) {
                        showAttachmentSheet = false
                        cameraInitialMode = "Recado de vídeo"
                        showCameraDialog = true
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showCameraDialog) {
        CustomCameraDialog(
            onDismiss = { showCameraDialog = false },
            initialMode = cameraInitialMode,
            showViewOnceOption = true,
            onMediaCaptured = { file, mediaType, caption, isViewOnce ->
                scope.launch(Dispatchers.IO) {
                    try {
                        val isVideoNote = mediaType == "video_note"
                        if (isVideoNote) {
                            viewModel.sendVideoNote(
                                conversationId = conversationId,
                                videoFile = file,
                                targetUserId = otherUserId
                            )
                            return@launch
                        }

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
                            targetUserId = otherUserId,
                            isViewOnce = isViewOnce
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
            onPostToFlash = {
                messageActionTarget = null
                scope.launch {
                    try {
                        val isVideo = target.messageType == "video"
                        val clean = if (target.content.contains(",")) target.content.substringAfter(",") else target.content
                        val dataUri = if (isVideo) "data:video/mp4;base64,$clean" else "data:image/jpeg;base64,$clean"
                        val res = app.apiClient.apiService.createStatus(
                            mapOf(
                                "contentType" to if (isVideo) "video" else "image",
                                "content" to dataUri,
                                "caption" to (target.iv?.split("|")?.getOrNull(2) ?: ""),
                                "avatarUrl" to (prefs.userAvatarUrl ?: ""),
                                "displayName" to (prefs.currentDisplayName ?: prefs.currentUsername ?: "Usuário")
                            )
                        )
                        if (res.isSuccessful) {
                            Toast.makeText(context, "Publicado no Flash! ⚡", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Erro ao publicar no Flash", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Erro ao publicar no Flash: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onCreateSticker = {
                messageActionTarget = null
                viewModel.sendStickerMessage(conversationId, target.content, otherUserId)
                Toast.makeText(context, "Figurinha criada e enviada! ✨", Toast.LENGTH_SHORT).show()
            },
            onSaveMedia = {
                messageActionTarget = null
                scope.launch(Dispatchers.IO) {
                    try {
                        if (target.messageType == "image") {
                            val bmp = MediaFileManager.decodeAndCorrectBitmap(context, target.content)
                            if (bmp != null) {
                                saveBitmapToGallery(context, bmp)
                            }
                        } else {
                            val parts = target.iv?.split("|") ?: emptyList()
                            val fName = parts.getOrNull(0)?.ifBlank { null } ?: "midia_${System.currentTimeMillis()}"
                            val mime = when (target.messageType) {
                                "video" -> "video/mp4"
                                "audio" -> "audio/mp4"
                                else -> "application/octet-stream"
                            }
                            openMediaFile(context, target.content, fName, mime)
                        }
                    } catch (e: Exception) {}
                }
            },
            onCopy = {
                messageActionTarget = null
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Mensagem LivreChat", target.content)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copiado para a área de transferência! 📋", Toast.LENGTH_SHORT).show()
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
                messageActionTarget = null
                if (target.isPinned) {
                    viewModel.unpinMessage(conversationId, target.id)
                    Toast.makeText(context, "Mensagem desafixada", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.pinMessage(conversationId, target)
                    Toast.makeText(context, "Mensagem fixada 📌", Toast.LENGTH_SHORT).show()
                }
            },
            onInfo = {
                showCryptoInfoDialog = true
                messageActionTarget = null
            }
        )
    }

    if (viewingImageBase64 != null) {
        val imgToView = viewingImageBase64!!
        FullscreenImageViewerDialog(
            base64Content = imgToView,
            onDismiss = { viewingImageBase64 = null },
            onPostToFlash = { b64 ->
                scope.launch {
                    try {
                        val clean = if (b64.contains(",")) b64.substringAfter(",") else b64
                        val dataUri = "data:image/jpeg;base64,$clean"
                        val res = app.apiClient.apiService.createStatus(
                            mapOf(
                                "contentType" to "image",
                                "content" to dataUri,
                                "caption" to "",
                                "avatarUrl" to (prefs.userAvatarUrl ?: ""),
                                "displayName" to (prefs.currentDisplayName ?: prefs.currentUsername ?: "Usuário")
                            )
                        )
                        if (res.isSuccessful) {
                            Toast.makeText(context, "Publicado no Flash! ⚡", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Erro ao publicar no Flash", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onCreateSticker = { b64 ->
                viewModel.sendStickerMessage(conversationId, b64, otherUserId)
                Toast.makeText(context, "Figurinha criada e enviada! ✨", Toast.LENGTH_SHORT).show()
                viewingImageBase64 = null
            }
        )
    }

    if (viewingVideoMessage != null) {
        FullscreenVideoViewerDialog(
            message = viewingVideoMessage!!,
            onDismiss = { viewingVideoMessage = null }
        )
    }

    if (viewingViewOnceMessage != null) {
        ViewOnceMediaViewerDialog(
            message = viewingViewOnceMessage!!,
            onDismiss = {
                val targetId = viewingViewOnceMessage?.id
                viewingViewOnceMessage = null
                if (targetId != null) {
                    viewModel.markViewOnceOpened(targetId)
                }
            }
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
    onVideoClick: (MessageEntity) -> Unit,
    onViewOnceClick: (MessageEntity) -> Unit = {},
    isGroup: Boolean = false,
    senderDisplayName: String = "",
    senderAvatarUrl: String? = null,
    onSenderClick: ((String) -> Unit)? = null
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

    val draggableState = rememberDraggableState { delta ->
        if (delta > 0 || dragOffset > 0) {
            dragOffset = (dragOffset + delta * 0.7f).coerceIn(0f, 130f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    if (dragOffset >= 115f) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSwipeToReply(message)
                    }
                    dragOffset = 0f
                }
            )
    ) {
        // Reply circle icon appears on left while dragging right
        if (animatedOffset > 8f) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = (animatedOffset * 0.35f - 18f).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = (animatedOffset / 115f).coerceIn(0.2f, 1f))),
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = animatedOffset.dp),
            horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (isGroup && !isOutgoing) {
                UserAvatarView(
                    avatarUrl = senderAvatarUrl,
                    displayName = senderDisplayName.ifBlank { "M" },
                    size = 30.dp,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(end = 6.dp, bottom = 4.dp)
                        .clickable { onSenderClick?.invoke(message.senderId) }
                )
            }

            Column(
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
                        if (isGroup && !isOutgoing) {
                            Text(
                                text = senderDisplayName.ifBlank { "Membro" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = getSenderColor(message.senderId),
                                modifier = Modifier
                                    .clickable { onSenderClick?.invoke(message.senderId) }
                                    .padding(start = 4.dp, bottom = 2.dp)
                            )
                        }
                        StickerMessageBubbleContent(
                            message = message,
                            isOutgoing = isOutgoing
                        )
                        Row(
                            modifier = Modifier.align(Alignment.End).padding(end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = TimeUtils.formatTime(message.createdAt),
                                color = OfflineGray,
                                fontSize = 11.sp
                            )
                            if (isOutgoing && !message.isDeleted) {
                                Spacer(modifier = Modifier.width(4.dp))
                                MessageStatusIcon(status = message.status)
                            }
                        }
                    }
                } else if (message.messageType == "video_note") {
                    // Video Note (Bolinha de Vídeo) without speech bubble container
                    Column(
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { onLongPress(message) }
                        )
                    ) {
                        if (isGroup && !isOutgoing) {
                            Text(
                                text = senderDisplayName.ifBlank { "Membro" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = getSenderColor(message.senderId),
                                modifier = Modifier
                                    .clickable { onSenderClick?.invoke(message.senderId) }
                                    .padding(start = 4.dp, bottom = 2.dp)
                            )
                        }
                        VideoNoteMessageBubbleContent(
                            message = message,
                            isOutgoing = isOutgoing,
                            onLongPress = { onLongPress(message) }
                        )
                    }
                } else {
                val isAudioMsg = message.messageType == "audio" ||
                    message.content.startsWith("data:audio") ||
                    message.content.endsWith(".m4a") ||
                    message.content.endsWith(".mp3") ||
                    message.content.contains("voice_") ||
                    message.content.contains("audio_msg_")

                val isImageMsg = message.messageType == "image" || message.content.startsWith("data:image")
                val isVideoMsg = message.messageType == "video" || message.content.startsWith("data:video")

                val videoParts = if (isVideoMsg) (message.iv?.split("|") ?: emptyList()) else emptyList()
                val videoCaption = videoParts.getOrNull(2)?.ifBlank { null }
                val imageCaption = if (isImageMsg) message.iv?.ifBlank { null } else null

                val hasMediaCaption = (!videoCaption.isNullOrBlank()) || (!imageCaption.isNullOrBlank())
                val isMediaMsg = isImageMsg || isVideoMsg
                val isMediaWithoutCaption = isMediaMsg && !hasMediaCaption

                val bubblePadding = when {
                    isMediaWithoutCaption -> PaddingValues(3.dp)
                    isMediaMsg && hasMediaCaption -> PaddingValues(start = 3.dp, top = 3.dp, end = 3.dp, bottom = 4.dp)
                    isAudioMsg -> PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    else -> PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                }

                Surface(
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isOutgoing) 16.dp else 4.dp,
                        bottomEnd = if (isOutgoing) 4.dp else 16.dp
                    ),
                    color = if (isOutgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .widthIn(max = 295.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { onLongPress(message) }
                        )
                ) {
                    Column(modifier = Modifier.padding(bubblePadding)) {
                        // Sender identifier in group conversation if incoming
                        if (isGroup && !isOutgoing) {
                            Text(
                                text = senderDisplayName.ifBlank { "Membro" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = getSenderColor(message.senderId),
                                modifier = Modifier
                                    .clickable { onSenderClick?.invoke(message.senderId) }
                                    .padding(
                                        start = if (isMediaMsg) 6.dp else 0.dp,
                                        bottom = 3.dp
                                    )
                            )
                        }

                        // Render quoted reply banner inside bubble if replying
                        if (!message.replyToContent.isNullOrBlank() || message.replyToId != null) {
                            Surface(
                                color = if (isOutgoing) Color.Black.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = if (isMediaMsg) 4.dp else 0.dp,
                                        end = if (isMediaMsg) 4.dp else 0.dp,
                                        bottom = 6.dp
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(28.dp)
                                            .background(if (isOutgoing) Color.White else MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Resposta",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (isOutgoing) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary
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
                        } else if (message.isViewOnce || message.messageType.startsWith("view_once")) {
                            ViewOnceMessageBubbleContent(
                                message = message,
                                isOutgoing = isOutgoing,
                                onOpenClick = { onViewOnceClick(message) }
                            )
                        } else if (isAudioMsg) {
                            AudioMessageBubbleContent(
                                message = message,
                                audioPlayerHelper = audioPlayerHelper,
                                isOutgoing = isOutgoing
                            )
                        } else if (isImageMsg) {
                            ImageMessageBubbleContent(
                                message = message,
                                isOutgoing = isOutgoing,
                                hasCaption = hasMediaCaption,
                                onImageClick = { onImageClick(message.content) }
                            )
                        } else if (isVideoMsg) {
                            VideoMessageBubbleContent(
                                message = message,
                                isOutgoing = isOutgoing,
                                hasCaption = hasMediaCaption,
                                onVideoClick = { onVideoClick(message) }
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

                        // WhatsApp-style timestamp:
                        // Only show bottom row if NOT media-without-caption and NOT audio (they have timestamps inside!)
                        if (!isMediaWithoutCaption && !isAudioMsg) {
                            Spacer(modifier = Modifier.height(if (hasMediaCaption) 2.dp else 4.dp))

                            Row(
                                modifier = Modifier.align(Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (message.isPinned) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "Fixada",
                                        tint = if (isOutgoing) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                }

                                Text(
                                    text = TimeUtils.formatTime(message.createdAt),
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
            val activeColor = if (isOutgoing) Color.White else MaterialTheme.colorScheme.primary
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
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                audioPlayerHelper.playAudio(message.id, message.content)
            },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (isOutgoing) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ),
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isThisPlaying) "Pausar" else "Reproduzir",
                tint = if (isOutgoing) Color.White else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            // WhatsApp-style Waveform Bars
            AudioWaveformVisualizer(
                bars = waveformBars,
                progress = if (playingId == message.id) progress else 0f,
                isOutgoing = isOutgoing,
                modifier = Modifier.fillMaxWidth().height(26.dp)
            )

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (isOutgoing) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isThisPlaying) "Reproduzindo..." else "Áudio",
                        fontSize = 11.sp,
                        color = if (isOutgoing) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = TimeUtils.formatTime(message.createdAt),
                        color = if (isOutgoing) Color.White.copy(alpha = 0.75f) else OfflineGray,
                        fontSize = 11.sp
                    )
                    if (isOutgoing && !message.isDeleted) {
                        Spacer(modifier = Modifier.width(3.dp))
                        MessageStatusIcon(status = message.status)
                    }
                }
            }
        }
    }
}

@Composable
fun MessageStatusIcon(status: String) {
    val prefs = LivreChatApp.instance.preferencesManager
    val showReadBlue = prefs.readReceiptsEnabled
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
                contentDescription = if (showReadBlue) "Lido" else "Entregue",
                tint = if (showReadBlue) Color(0xFF34B7F1) else Color.White.copy(alpha = 0.7f),
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
    hasCaption: Boolean,
    onImageClick: () -> Unit
) {
    var decodedBitmap by remember(message.content) { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(message.content) {
        withContext(Dispatchers.IO) {
            val bmp = MediaFileManager.decodeAndCorrectBitmap(context, message.content)
            withContext(Dispatchers.Main) {
                decodedBitmap = bmp
            }
        }
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .clickable(onClick = onImageClick)
        ) {
            if (decodedBitmap != null) {
                Image(
                    bitmap = decodedBitmap!!.asImageBitmap(),
                    contentDescription = "Foto",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 280.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // WhatsApp-style floating timestamp pill inside photo when no caption
            if (!hasCaption) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.52f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = TimeUtils.formatTime(message.createdAt),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        if (isOutgoing && !message.isDeleted) {
                            Spacer(modifier = Modifier.width(3.dp))
                            MessageStatusIcon(status = message.status)
                        }
                    }
                }
            }
        }

        if (hasCaption && !message.iv.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.iv,
                color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun VideoMessageBubbleContent(
    message: MessageEntity,
    isOutgoing: Boolean,
    hasCaption: Boolean,
    onVideoClick: () -> Unit
) {
    val context = LocalContext.current
    val parts = remember(message.iv) { message.iv?.split("|") ?: emptyList() }
    val fileName = parts.getOrNull(0)?.ifBlank { null } ?: "video_${message.id.take(8)}.mp4"
    val fileSize = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    val caption = parts.getOrNull(2)?.ifBlank { null }

    var thumbnailBitmap by remember(message.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(message.content) {
        withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                if (MediaFileManager.isLocalFilePath(message.content)) {
                    val path = message.content.removePrefix("file://")
                    retriever.setDataSource(path)
                } else {
                    val clean = if (message.content.contains(",")) message.content.substringAfter(",") else message.content
                    val bytes = Base64.decode(clean, Base64.DEFAULT)
                    val temp = File(context.cacheDir, "thumb_${message.id}.mp4")
                    FileOutputStream(temp).use { it.write(bytes) }
                    retriever.setDataSource(temp.absolutePath)
                }
                val frame = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                withContext(Dispatchers.Main) {
                    thumbnailBitmap = frame
                }
                retriever.release()
            } catch (e: Exception) {}
        }
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable { onVideoClick() },
            contentAlignment = Alignment.Center
        ) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap!!.asImageBitmap(),
                    contentDescription = "Vídeo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Dark tint overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )

            // Play button circle
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Reproduzir Vídeo",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // WhatsApp-style Bottom indicators
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.52f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (fileSize > 0) formatFileSize(fileSize) else "Vídeo",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }

            if (!hasCaption) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.52f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = TimeUtils.formatTime(message.createdAt),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        if (isOutgoing && !message.isDeleted) {
                            Spacer(modifier = Modifier.width(3.dp))
                            MessageStatusIcon(status = message.status)
                        }
                    }
                }
            }
        }

        if (hasCaption && !caption.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = caption,
                color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                color = if (ext == "PDF") Color(0xFFF44336) else if (ext in listOf("ZIP", "RAR")) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary,
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
    onDismiss: () -> Unit,
    onPostToFlash: ((String) -> Unit)? = null,
    onCreateSticker: ((String) -> Unit)? = null
) {
    var decodedBitmap by remember(base64Content) { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    var rotationDegrees by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(base64Content) {
        withContext(Dispatchers.IO) {
            val bmp = MediaFileManager.decodeAndCorrectBitmap(context, base64Content)
            withContext(Dispatchers.Main) {
                decodedBitmap = bmp
            }
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
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = rotationDegrees },
                    contentScale = ContentScale.Fit
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            // Top Bar: Back & Options
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        rotationDegrees = (rotationDegrees + 90f) % 360f
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Girar 90°", tint = Color.White)
                    }

                    IconButton(onClick = {
                        openMediaFile(context, base64Content, "foto_${System.currentTimeMillis()}.jpg", "image/jpeg")
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Compartilhar", tint = Color.White)
                    }
                }
            }

            // Bottom Action Bar: WhatsApp-style Action Strip
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Postar no Flash
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            onPostToFlash?.invoke(base64Content)
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = "Postar no Flash", tint = Color(0xFF25D366), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Flash", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // 2. Criar Figurinha
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            onCreateSticker?.invoke(base64Content)
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Criar Figurinha", tint = Color(0xFF7F66FF), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Figurinha", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // 3. Girar
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            rotationDegrees = (rotationDegrees + 90f) % 360f
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Girar", tint = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Girar", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // 4. Salvar na Galeria
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            decodedBitmap?.let { bmp ->
                                saveBitmapToGallery(context, bmp)
                            }
                        }
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = "Salvar na Galeria", tint = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Salvar", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // 5. Compartilhar
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            openMediaFile(context, base64Content, "foto_${System.currentTimeMillis()}.jpg", "image/jpeg")
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Compartilhar", tint = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Enviar", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
    try {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "livrechat_${System.currentTimeMillis()}.jpg")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LivreChat")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                values.clear()
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            Toast.makeText(context, "Imagem salva na Galeria com sucesso!", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Erro ao salvar na Galeria: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun FullscreenVideoViewerDialog(
    message: MessageEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    var videoViewRef by remember { mutableStateOf<android.widget.VideoView?>(null) }
    var tempVideoFile by remember { mutableStateOf<File?>(null) }

    val parts = remember(message.iv) { message.iv?.split("|") ?: emptyList() }
    val fileName = parts.getOrNull(0)?.ifBlank { null } ?: "video_${message.id.take(8)}.mp4"

    LaunchedEffect(message.content) {
        withContext(Dispatchers.IO) {
            try {
                val file = if (MediaFileManager.isLocalFilePath(message.content)) {
                    File(message.content.removePrefix("file://"))
                } else {
                    val clean = if (message.content.contains(",")) message.content.substringAfter(",") else message.content
                    val bytes = Base64.decode(clean, Base64.DEFAULT)
                    val f = File(context.cacheDir, "play_${fileName}")
                    FileOutputStream(f).use { it.write(bytes) }
                    f
                }
                withContext(Dispatchers.Main) {
                    tempVideoFile = file
                }
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            videoViewRef?.let { vv ->
                if (vv.isPlaying) {
                    currentPosition = vv.currentPosition
                    if (vv.duration > 0) duration = vv.duration
                }
            }
            delay(250)
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
                .clickable { showControls = !showControls }
        ) {
            if (tempVideoFile != null) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            setVideoPath(tempVideoFile!!.absolutePath)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                duration = mp.duration
                                start()
                                isPlaying = true
                            }
                            setOnCompletionListener {
                                isPlaying = false
                            }
                            videoViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            if (showControls) {
                // Center Play/Pause toggle
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(64.dp)
                            .clickable {
                                videoViewRef?.let { vv ->
                                    if (vv.isPlaying) {
                                        vv.pause()
                                        isPlaying = false
                                    } else {
                                        vv.start()
                                        isPlaying = true
                                    }
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                    }

                    Text(
                        text = fileName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    )

                    IconButton(onClick = {
                        openMediaFile(context, message.content, fileName, "video/mp4")
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Compartilhar", tint = Color.White)
                    }
                }

                // Bottom Scrubber & Timers
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    val curSec = currentPosition / 1000
                    val durSec = duration / 1000
                    val curStr = "%02d:%02d".format(curSec / 60, curSec % 60)
                    val durStr = "%02d:%02d".format(durSec / 60, durSec % 60)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(curStr, color = Color.White, fontSize = 12.sp)
                        Text(durStr, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }

                    Slider(
                        value = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = { ratio ->
                            val newPos = (ratio * duration).toInt()
                            currentPosition = newPos
                            videoViewRef?.seekTo(newPos)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF25D366),
                            activeTrackColor = Color(0xFF25D366),
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
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

fun formatChatLastSeen(ts: Long?): String {
    if (ts == null || ts <= 0L) return "Visto por último recentemente"
    val now = java.util.Calendar.getInstance()
    val time = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    val timeStr = com.livrechat.app.util.TimeUtils.formatTime(ts)
    return if (now.get(java.util.Calendar.YEAR) == time.get(java.util.Calendar.YEAR) &&
               now.get(java.util.Calendar.DAY_OF_YEAR) == time.get(java.util.Calendar.DAY_OF_YEAR)) {
        "Visto por último hoje às $timeStr"
    } else if (now.get(java.util.Calendar.YEAR) == time.get(java.util.Calendar.YEAR) &&
               now.get(java.util.Calendar.DAY_OF_YEAR) - time.get(java.util.Calendar.DAY_OF_YEAR) == 1) {
        "Visto por último ontem às $timeStr"
    } else {
        val dateStr = com.livrechat.app.util.TimeUtils.formatDateTimeWithWord(ts)
        "Visto por último em $dateStr"
    }
}

fun getSenderColor(senderId: String): Color {
    val colors = listOf(
        Color(0xFF25D366), // WhatsApp Green
        Color(0xFF00A884), // Teal
        Color(0xFF34B7F1), // Sky Blue
        Color(0xFFE91E63), // Pink
        Color(0xFF9C27B0), // Purple
        Color(0xFFFF9800), // Orange
        Color(0xFFFF5722), // Deep Orange
        Color(0xFF00BCD4), // Light Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFFFB300)  // Amber
    )
    val hash = Math.abs(senderId.hashCode())
    return colors[hash % colors.size]
}

@Composable
fun ViewOnceMessageBubbleContent(
    message: MessageEntity,
    isOutgoing: Boolean,
    onOpenClick: () -> Unit
) {
    val isOpened = message.viewOnceOpened
    val isVideo = message.messageType == "view_once_video" || (message.isViewOnce && message.messageType == "video")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isOpened) { onOpenClick() }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isOpened) Color.Transparent
                    else if (isOutgoing) Color.White.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
                .border(
                    width = 1.5.dp,
                    color = if (isOpened) {
                        if (isOutgoing) Color.White.copy(alpha = 0.4f) else OfflineGray.copy(alpha = 0.5f)
                    } else {
                        if (isOutgoing) Color.White else MaterialTheme.colorScheme.primary
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "1",
                color = if (isOpened) {
                    if (isOutgoing) Color.White.copy(alpha = 0.4f) else OfflineGray
                } else {
                    if (isOutgoing) Color.White else MaterialTheme.colorScheme.primary
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(
                text = if (isOpened) {
                    if (isVideo) "Vídeo reproduzido" else "Foto aberta"
                } else {
                    if (isVideo) "Vídeo (Visualização única)" else "Foto (Visualização única)"
                },
                fontWeight = if (isOpened) FontWeight.Normal else FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isOpened) {
                    if (isOutgoing) Color.White.copy(alpha = 0.5f) else OfflineGray
                } else {
                    if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (!isOpened) {
                Text(
                    text = "Toque para abrir",
                    fontSize = 11.sp,
                    color = if (isOutgoing) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ViewOnceMediaViewerDialog(
    message: MessageEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isVideo = message.messageType == "view_once_video" || message.content.startsWith("data:video") || message.content.endsWith(".mp4")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            securePolicy = SecureFlagPolicy.SecureOn
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (isVideo) {
                var videoFile by remember { mutableStateOf<File?>(null) }
                LaunchedEffect(message.content) {
                    withContext(Dispatchers.IO) {
                        try {
                            val f = if (MediaFileManager.isLocalFilePath(message.content)) {
                                File(message.content.removePrefix("file://"))
                            } else {
                                val clean = if (message.content.contains(",")) message.content.substringAfter(",") else message.content
                                val bytes = Base64.decode(clean, Base64.DEFAULT)
                                val tmp = File(context.cacheDir, "vo_${message.id.take(8)}.mp4")
                                FileOutputStream(tmp).use { it.write(bytes) }
                                tmp
                            }
                            withContext(Dispatchers.Main) { videoFile = f }
                        } catch (e: Exception) {}
                    }
                }

                if (videoFile != null && videoFile!!.exists()) {
                    AndroidView(
                        factory = { ctx ->
                            android.widget.VideoView(ctx).apply {
                                setVideoPath(videoFile!!.absolutePath)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            } else {
                var bitmap by remember { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(message.content) {
                    withContext(Dispatchers.IO) {
                        try {
                            val bmp = MediaFileManager.decodeAndCorrectBitmap(context, message.content)
                            withContext(Dispatchers.Main) { bitmap = bmp }
                        } catch (e: Exception) {}
                    }
                }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Visualização única",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // Top Header: ① Badge, "Visualização única", Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF25D366)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "1",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Visualização única",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Esta mídia desaparecerá ao fechar",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoNoteMessageBubbleContent(
    message: MessageEntity,
    isOutgoing: Boolean,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(false) }
    var videoFile by remember(message.id) { mutableStateOf<File?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    LaunchedEffect(message.content) {
        withContext(Dispatchers.IO) {
            try {
                val f = if (MediaFileManager.isLocalFilePath(message.content)) {
                    File(message.content.removePrefix("file://"))
                } else {
                    val clean = if (message.content.contains(",")) message.content.substringAfter(",") else message.content
                    val bytes = Base64.decode(clean, Base64.DEFAULT)
                    val tmp = File(context.cacheDir, "vn_${message.id.take(8)}.mp4")
                    FileOutputStream(tmp).use { it.write(bytes) }
                    tmp
                }
                withContext(Dispatchers.Main) {
                    videoFile = f
                }
            } catch (e: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayerRef?.release()
            mediaPlayerRef = null
        }
    }

    Column(
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .border(
                    width = 2.dp,
                    color = if (isOutgoing) MaterialTheme.colorScheme.primary else Color(0xFF00B0FF),
                    shape = CircleShape
                )
                .combinedClickable(
                    onClick = {
                        isMuted = !isMuted
                        mediaPlayerRef?.let { mp ->
                            val vol = if (isMuted) 0f else 1f
                            mp.setVolume(vol, vol)
                        }
                    },
                    onLongClick = onLongPress
                ),
            contentAlignment = Alignment.Center
        ) {
            if (videoFile != null && videoFile!!.exists()) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            setVideoPath(videoFile!!.absolutePath)
                            setOnPreparedListener { mp ->
                                mediaPlayerRef = mp
                                mp.isLooping = true
                                val vol = if (isMuted) 0f else 1f
                                mp.setVolume(vol, vol)
                                start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (isMuted) "Sem som" else "Com som",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Row(
            modifier = Modifier.padding(top = 4.dp, end = 4.dp, start = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (message.isPinned) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "Fixada",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }

            Text(
                text = TimeUtils.formatTime(message.createdAt),
                color = OfflineGray,
                fontSize = 11.sp
            )
            if (isOutgoing && !message.isDeleted) {
                Spacer(modifier = Modifier.width(4.dp))
                MessageStatusIcon(status = message.status)
            }
        }
    }
}
