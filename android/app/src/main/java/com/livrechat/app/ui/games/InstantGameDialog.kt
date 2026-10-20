package com.livrechat.app.ui.games

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.livrechat.app.data.remote.InstantGameDto
import java.net.URLEncoder

class GameJsBridge(
    private val onCloseGame: () -> Unit,
    private val onShareCode: (String) -> Unit
) {
    @JavascriptInterface
    fun closeGame() {
        onCloseGame()
    }

    @JavascriptInterface
    fun shareRoom(code: String) {
        onShareCode(code)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InstantGameDialog(
    game: InstantGameDto,
    playerName: String,
    initialRoomCode: String? = null,
    initialMode: String? = null, // "host", "join", or null
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val encodedPlayer = remember(playerName) {
        try {
            URLEncoder.encode(playerName, "UTF-8")
        } catch (e: Exception) {
            "Jogador"
        }
    }

    val gameUrl = remember(game.id, encodedPlayer, initialRoomCode, initialMode) {
        val base = "https://164-152-40-160.sslip.io/instant.html?game=${game.id}&player=$encodedPlayer"
        val roomPart = if (!initialRoomCode.isNullOrBlank()) "&room=${initialRoomCode.trim()}" else ""
        val modePart = if (!initialMode.isNullOrBlank()) "&mode=${initialMode.trim()}" else ""
        "$base$roomPart$modePart"
    }

    BackHandler {
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // WebGL Canvas Container
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(AndroidColor.BLACK)
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            allowFileAccess = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }

                        addJavascriptInterface(
                            GameJsBridge(
                                onCloseGame = {
                                    post { onDismiss() }
                                },
                                onShareCode = { code ->
                                    post {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "🎮 Bora jogar ${game.title} comigo no LivreChat? Entra na sala: $code"
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Convidar no LivreChat"))
                                    }
                                }
                            ),
                            "Android"
                        )

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                if (newProgress >= 90) {
                                    isLoading = false
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                            }
                        }

                        loadUrl(gameUrl)
                        webViewRef = this
                    }
                },
                update = {
                    webViewRef = it
                },
                modifier = Modifier.fillMaxSize()
            )

            // Minimalist Top Bar Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Exit Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xCC151A23),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF)),
                    modifier = Modifier.clickable { onDismiss() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Fechar Jogo",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Sair",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF8A80)
                        )
                    }
                }

                // Game Title & Tag
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xCC151A23),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x338B5CF6))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⚡ ${game.title}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA78BFA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Reload Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xCC151A23),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF)),
                    modifier = Modifier.clickable {
                        isLoading = true
                        webViewRef?.reload()
                    }
                ) {
                    Box(
                        modifier = Modifier.padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Recarregar",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Loading Indicator
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF8B5CF6),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Iniciando ${game.title} na nuvem...",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Carregando WebAssembly a 60 FPS",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                loadUrl("about:blank")
                stopLoading()
                webChromeClient = null
                destroy()
            }
            webViewRef = null
        }
    }
}
