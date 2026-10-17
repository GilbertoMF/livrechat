package com.livrechat.app.ui.call

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrechat.app.ui.common.UserAvatarView
import com.livrechat.app.ui.theme.DangerRed
import com.livrechat.app.ui.theme.DarkBackground
import com.livrechat.app.webrtc.CallState

@Composable
fun ActiveCallScreen(
    viewModel: CallViewModel,
    onCallFinished: () -> Unit,
    onMinimize: () -> Unit = onCallFinished,
    onNavigateToChat: ((targetUserId: String, targetDisplayName: String) -> Unit)? = null
) {
    val callState by viewModel.callState.collectAsState()
    val callInfo by viewModel.callInfo.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeaker by viewModel.isSpeaker.collectAsState()
    val durationSeconds by viewModel.durationSeconds.collectAsState()
    val context = LocalContext.current

    var hasFinished by remember { mutableStateOf(false) }
    var isVideoMuted by remember { mutableStateOf(true) }

    // Intercept back button to minimize rather than hang up accidentally
    BackHandler {
        onMinimize()
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(callState) {
        if (!hasFinished && (callState == CallState.ENDED || callState == CallState.IDLE)) {
            hasFinished = true
            onCallFinished()
        }
    }

    // Concentric Wave Animation for calling/ringing state
    val isCallingOrRinging = callState == CallState.CALLING || callState == CallState.RINGING
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha1"
    )

    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 2.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2"
    )
    val pulseAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha2"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F2027),
                        Color(0xFF203A43),
                        Color(0xFF0F172A)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP BAR: Minimize, E2E Encryption Pill, Security
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMinimize,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimizar Chamada",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF25D366),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Criptografia de ponta a ponta",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(
                    onClick = {
                        Toast.makeText(context, "Chamada protegida com WebRTC E2EE", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = "Segurança",
                        tint = Color(0xFF25D366),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // CENTER: Avatar, concentric waves, contact info & status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 20.dp)
            ) {
                // Avatar with Animated Pulse Waves
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(200.dp)
                ) {
                    if (isCallingOrRinging) {
                        // Expanding Ring 2
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .scale(pulseScale2)
                                .background(
                                    Color(0xFF25D366).copy(alpha = pulseAlpha2),
                                    CircleShape
                                )
                        )
                        // Expanding Ring 1
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .scale(pulseScale1)
                                .background(
                                    Color(0xFF25D366).copy(alpha = pulseAlpha1),
                                    CircleShape
                                )
                        )
                    }

                    // Avatar
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(
                            3.dp,
                            if (callState == CallState.CONNECTED) Color(0xFF25D366)
                            else Color.White.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.size(130.dp)
                    ) {
                        UserAvatarView(
                            avatarUrl = callInfo?.contactAvatarUrl,
                            displayName = callInfo?.contactDisplayName ?: "C",
                            size = 130.dp,
                            fontSize = 52.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = callInfo?.contactDisplayName ?: "Contato LivreChat",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                val stateLabel = when (callState) {
                    CallState.CALLING -> "Ligando..."
                    CallState.RINGING -> "Chamando..."
                    CallState.CONNECTING -> "Conectando..."
                    CallState.CONNECTED -> formatTimer(durationSeconds)
                    CallState.RECONNECTING -> "Reconectando..."
                    CallState.ENDED -> "Chamada encerrada"
                    else -> ""
                }

                if (callState == CallState.CONNECTED) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF25D366),
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stateLabel,
                            fontSize = 18.sp,
                            color = Color(0xFF25D366),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• HD Áudio",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    Text(
                        text = stateLabel,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // BOTTOM CONTROLS GRID: WhatsApp Style
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Mute Button
                        CallActionButton(
                            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = "Mudo",
                            isActive = isMuted,
                            onClick = { viewModel.toggleMute() }
                        )

                        // 2. Speaker Button
                        CallActionButton(
                            icon = if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                            label = "Alto-falante",
                            isActive = isSpeaker,
                            onClick = { viewModel.toggleSpeaker() }
                        )

                        // 3. Video Button
                        CallActionButton(
                            icon = if (isVideoMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            label = "Vídeo",
                            isActive = !isVideoMuted,
                            onClick = {
                                isVideoMuted = !isVideoMuted
                                Toast.makeText(
                                    context,
                                    if (isVideoMuted) "Câmera desativada" else "Câmera ativada",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )

                        // 4. Message / Chat Button
                        CallActionButton(
                            icon = Icons.Default.Chat,
                            label = "Mensagem",
                            isActive = false,
                            onClick = {
                                val targetId = callInfo?.contactUserId
                                val targetName = callInfo?.contactDisplayName ?: "Contato"
                                if (!targetId.isNullOrBlank() && onNavigateToChat != null) {
                                    onNavigateToChat(targetId, targetName)
                                } else {
                                    onMinimize()
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // End Call Red Button
                FloatingActionButton(
                    onClick = {
                        viewModel.hangupCall()
                        onCallFinished()
                    },
                    containerColor = DangerRed,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Encerrar Chamada",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isActive) Color.White else Color.White.copy(alpha = 0.15f),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) DarkBackground else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTimer(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
