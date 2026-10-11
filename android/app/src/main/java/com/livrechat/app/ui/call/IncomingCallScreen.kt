package com.livrechat.app.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrechat.app.ui.theme.BluePrimary
import com.livrechat.app.ui.theme.DangerRed
import com.livrechat.app.ui.theme.DarkBackground
import com.livrechat.app.ui.theme.SuccessGreen
import com.livrechat.app.webrtc.CallState

@Composable
fun IncomingCallScreen(
    viewModel: CallViewModel,
    onCallAnswered: () -> Unit,
    onCallRejected: () -> Unit
) {
    val callInfo by viewModel.callInfo.collectAsState()
    val callState by viewModel.callState.collectAsState()

    LaunchedEffect(callState) {
        if (callState == CallState.CONNECTED || callState == CallState.CONNECTING) {
            onCallAnswered()
        } else if (callState == CallState.ENDED || callState == CallState.IDLE) {
            onCallRejected()
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 64.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = BluePrimary.copy(alpha = 0.3f),
                    modifier = Modifier.size(120.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (callInfo?.contactDisplayName ?: "C").take(1).uppercase(),
                            fontSize = 50.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = callInfo?.contactDisplayName ?: "Contato LivreChat",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Chamada de Voz LivreChat Recebida",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            // Answer & Reject buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reject Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = { viewModel.rejectIncomingCall() },
                        containerColor = DangerRed,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(68.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Recusar",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Recusar", color = Color.White, fontSize = 13.sp)
                }

                // Answer Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = {
                            viewModel.answerIncomingCall()
                            onCallAnswered()
                        },
                        containerColor = SuccessGreen,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(68.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Atender",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Atender", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}
