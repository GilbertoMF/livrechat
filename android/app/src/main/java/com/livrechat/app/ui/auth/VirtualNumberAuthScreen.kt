package com.livrechat.app.ui.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrechat.app.notification.LivreChatNotificationManager
import com.livrechat.app.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirtualNumberAuthScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val uiState by viewModel.uiState.collectAsState()

    var step by remember { mutableStateOf(1) } // 1: Inserir/Gerar número 8 dígitos, 2: Confirmar SMS 4 dígitos
    var virtualNumber by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var incomingSmsPreview by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableStateOf(30) }

    fun generateRandom8Digits(): String {
        val firstDigit = Random.nextInt(1, 10)
        val rest = Random.nextInt(1000000, 9999999)
        return "$firstDigit$rest"
    }

    LaunchedEffect(Unit) {
        if (virtualNumber.isBlank()) {
            virtualNumber = generateRandom8Digits()
        }
    }

    // Gerencia o temporizador de reenvio
    LaunchedEffect(step, incomingSmsPreview) {
        if (step == 2) {
            countdown = 30
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
        }
    }

    // Observa o estado da ViewModel
    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.VirtualNumberCodeSent -> {
                val sentState = uiState as AuthUiState.VirtualNumberCodeSent
                step = 2
                incomingSmsPreview = sentState.code
                // Dispara notificação nativa no topo do Android imitando recebimento de SMS real
                LivreChatNotificationManager.showVirtualSmsNotification(
                    context = context,
                    virtualNumber = sentState.virtualNumber,
                    code = sentState.code
                )
            }
            is AuthUiState.LoginSuccess -> {
                onAuthSuccess()
            }
            is AuthUiState.Error -> {
                val msg = (uiState as AuthUiState.Error).message
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
            else -> Unit
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (step == 2) {
                        step = 1
                        smsCode = ""
                        incomingSmsPreview = null
                    } else {
                        onNavigateBack()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Voltar",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (step == 1) "Número Virtual" else "Verificação SMS",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Simulação de SMS recebido estilo WhatsApp no topo
            AnimatedVisibility(
                visible = incomingSmsPreview != null && step == 2,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Sms,
                                    contentDescription = "SMS",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SMS Recebido • LivreChat",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Seu código é ${incomingSmsPreview ?: ""}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                incomingSmsPreview?.let {
                                    smsCode = it
                                    viewModel.verifyVirtualNumberCode(virtualNumber, it)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Inserir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (step == 1) {
                // ==========================================
                // PASSO 1: NÚMERO VIRTUAL (8 DÍGITOS)
                // ==========================================
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Pin,
                            contentDescription = "Número Virtual",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Gere ou insira seu Número Virtual",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Seu número virtual de 8 dígitos será sua identificação no LivreChat. Você receberá um código SMS para confirmar.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Campo do número virtual (8 dígitos)
                OutlinedTextField(
                    value = virtualNumber,
                    onValueChange = { input ->
                        val digitsOnly = input.filter { it.isDigit() }
                        if (digitsOnly.length <= 8) {
                            virtualNumber = digitsOnly
                        }
                    },
                    label = { Text("Número Virtual (8 Dígitos)") },
                    placeholder = { Text("Ex: 98241735") },
                    leadingIcon = {
                        Text(
                            text = "ID  ",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 14.dp)
                        )
                    },
                    trailingIcon = {
                        if (virtualNumber.length == 8) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Válido",
                                tint = SuccessGreen
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        if (virtualNumber.length == 8) {
                            viewModel.requestVirtualNumberCode(virtualNumber)
                        }
                    }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Botão para gerar número aleatório
                OutlinedButton(
                    onClick = {
                        virtualNumber = generateRandom8Digits()
                        Toast.makeText(context, "Novo número virtual gerado: $virtualNumber", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gerar Número Virtual Aleatório", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Botão de Avançar
                val isButtonEnabled = virtualNumber.length == 8 && uiState !is AuthUiState.Loading

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.requestVirtualNumberCode(virtualNumber)
                    },
                    enabled = isButtonEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (uiState is AuthUiState.Loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = "Avançar e Receber SMS",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

            } else {
                // ==========================================
                // PASSO 2: CONFIRMAÇÃO DO CÓDIGO SMS (4 DÍGITOS)
                // ==========================================
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MarkEmailRead,
                            contentDescription = "Código SMS",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Confirmando seu Número",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Enviamos um SMS para o número virtual ID $virtualNumber com um código de 4 dígitos.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Caixas de 4 dígitos OTP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val digit = smsCode.getOrNull(i)?.toString() ?: ""
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (smsCode.length == i) 2.dp else 1.dp,
                                color = if (smsCode.length == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = digit,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }

                // Campo invisível/oculto de texto para capturar os 4 dígitos
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = smsCode,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        if (digits.length <= 4) {
                            smsCode = digits
                            if (digits.length == 4) {
                                focusManager.clearFocus()
                                viewModel.verifyVirtualNumberCode(virtualNumber, digits)
                            }
                        }
                    },
                    label = { Text("Digite os 4 dígitos do SMS") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.verifyVirtualNumberCode(virtualNumber, smsCode)
                    },
                    enabled = smsCode.length == 4 && uiState !is AuthUiState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (uiState is AuthUiState.Loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = "Confirmar e Entrar",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Botão de Reenviar SMS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        step = 1
                        smsCode = ""
                        incomingSmsPreview = null
                    }) {
                        Text("Editar número", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    TextButton(
                        onClick = {
                            viewModel.requestVirtualNumberCode(virtualNumber)
                            Toast.makeText(context, "Novo SMS enviado!", Toast.LENGTH_SHORT).show()
                        },
                        enabled = countdown == 0
                    ) {
                        Text(
                            text = if (countdown > 0) "Reenviar SMS em ${countdown}s" else "Reenviar SMS",
                            color = if (countdown == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
