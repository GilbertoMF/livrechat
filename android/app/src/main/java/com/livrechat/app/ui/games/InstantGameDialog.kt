package com.livrechat.app.ui.games

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
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
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

class GameJsBridge(
    private val onCloseGame: () -> Unit,
    private val onShareCode: (String) -> Unit,
    private val onToggleOrientation: () -> Unit
) {
    @JavascriptInterface
    fun closeGame() {
        onCloseGame()
    }

    @JavascriptInterface
    fun shareRoom(code: String) {
        onShareCode(code)
    }

    @JavascriptInterface
    fun toggleOrientation() {
        onToggleOrientation()
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
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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

    val toggleOrientation = {
        activity?.let { act ->
            val newTarget = if (act.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE ||
                act.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                isLandscape
            ) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            act.requestedOrientation = newTarget
        }
    }

    // Persist WebView across recomposition / rotation changes so game does NOT reload!
    val webView = remember {
        WebView(context).apply {
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
                    },
                    onToggleOrientation = {
                        post { toggleOrientation() }
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
        }
    }

    BackHandler {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDismiss()
    }

    Dialog(
        onDismissRequest = {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            onDismiss()
        },
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
            // WebGL Canvas Container: detach from previous parent and attach without reloading!
            AndroidView(
                factory = {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView
                },
                update = {
                    // Do NOT reload here! The game state in WASM is fully retained
                },
                modifier = Modifier.fillMaxSize()
            )

            // Minimalist Top Bar Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (isLandscape) 8.dp else 28.dp,
                        start = 12.dp,
                        end = 12.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Exit Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xCC151A23),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF)),
                    modifier = Modifier.clickable {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        onDismiss()
                    }
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

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // ROTATE / ORIENTATION TOGGLE (Horizontal / Vertical without reloading)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xCC151A23),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x5538BDF8)),
                        modifier = Modifier.clickable { toggleOrientation() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.ScreenRotation,
                                contentDescription = "Girar Tela",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isLandscape) "Vertical" else "Horizontal",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7DD3FC)
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
                            webView.reload()
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
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.apply {
                loadUrl("about:blank")
                stopLoading()
                webChromeClient = null
                destroy()
            }
        }
    }
}
