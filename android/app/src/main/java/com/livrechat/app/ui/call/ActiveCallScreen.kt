package com.livrechat.app.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrechat.app.ui.theme.DangerRed
import com.livrechat.app.ui.theme.DarkBackground
import com.livrechat.app.webrtc.CallState

@Composable
fun ActiveCallScreen(
    viewModel: CallViewModel,
    onCallFinished: () -> Unit
) {
    val callState by viewModel.callState.collectAsState()
    val callInfo by viewModel.callInfo.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeaker by viewModel.isSpeaker.collectAsState()
    val durationSeconds by viewModel.durationSeconds.collectAsState()

    var hasFinished by remember { mutableStateOf(false) }

    LaunchedEffect(callState) {
        if (!hasFinished && (callState == CallState.ENDED || callState == CallState.IDLE)) {
            hasFinished = true
            onCallFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    modifier = Modifier.size(110.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (callInfo?.contactDisplayName ?: "C").take(1).uppercase(),
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = callInfo?.contactDisplayName ?: "Contato LivreChat",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                val stateLabel = when (callState) {
                    CallState.CALLING -> "Chamando..."
                    CallState.RINGING -> "Tocando..."
                    CallState.CONNECTING -> "Conectando WebRTC..."
                    CallState.CONNECTED -> formatTimer(durationSeconds)
                    CallState.RECONNECTING -> "Reconectando áudio..."
                    CallState.ENDED -> "Chamada encerrada"
                    else -> ""
                }

                Text(
                    text = stateLabel,
                    fontSize = 17.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }

            // Call Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute Button
                    FilledIconButton(
                        onClick = { viewModel.toggleMute() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isMuted) Color.White else Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Microfone",
                            tint = if (isMuted) DarkBackground else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Speaker Button
                    FilledIconButton(
                        onClick = { viewModel.toggleSpeaker() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isSpeaker) Color.White else Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                            contentDescription = "Viva-voz",
                            tint = if (isSpeaker) DarkBackground else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

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

private fun formatTimer(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
