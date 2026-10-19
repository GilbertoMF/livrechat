package com.livrechat.app.ui.livretube

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.livrechat.app.data.remote.LivreTubeVideoDto

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LivreTubePlayerOverlay(
    modifier: Modifier = Modifier
) {
    val video = LivreTubePlayerManager.currentVideo ?: return
    val context = LocalContext.current
    val activity = context as? Activity
    val isMinimized = LivreTubePlayerManager.isMinimized
    val isLandscape = LivreTubePlayerManager.isLandscape

    var isPlayerLoading by remember(video.videoId) { mutableStateOf(true) }

    // Intercept back button when player is active
    BackHandler(enabled = !isMinimized) {
        if (isLandscape) {
            LivreTubePlayerManager.toggleLandscape(activity)
        } else {
            LivreTubePlayerManager.minimize(activity)
        }
    }

    Box(modifier = modifier) {
        if (isLandscape) {
            // FULLSCREEN LANDSCAPE MODE
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            createConfiguredWebView(ctx, video.videoId) { isPlayerLoading = false }
                        },
                        update = { webView ->
                            updateWebViewVideo(webView, video.videoId)
                        }
                    )

                    if (isPlayerLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }

                    // TOP OVERLAY CONTROLS (Floating in landscape)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .align(Alignment.TopCenter),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { LivreTubePlayerManager.toggleLandscape(activity) },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Sair de tela cheia", tint = Color.White)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = video.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Rotate back to Portrait
                        IconButton(
                            onClick = { LivreTubePlayerManager.toggleLandscape(activity) },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.ScreenLockPortrait, contentDescription = "Modo Vertical", tint = Color.White)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Minimize
                        IconButton(
                            onClick = { LivreTubePlayerManager.minimize(activity) },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimizar", tint = Color.White)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Close
                        IconButton(
                            onClick = { LivreTubePlayerManager.close(activity) },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                        }
                    }
                }
            }
        } else if (!isMinimized) {
            // EXPANDED PORTRAIT MODE
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // TOP BAR
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // MINIMIZE BUTTON
                            IconButton(onClick = { LivreTubePlayerManager.minimize(activity) }) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Minimizar Reprodutor",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = video.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = video.author,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // ROTATE / FULLSCREEN BUTTON
                            IconButton(onClick = { LivreTubePlayerManager.toggleLandscape(activity) }) {
                                Icon(
                                    Icons.Default.ScreenRotation,
                                    contentDescription = "Girar Tela (Horizontal)",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // SHARE BUTTON
                            IconButton(onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Assista '${video.title}' no LivreTube: https://youtu.be/${video.videoId}")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Compartilhar"))
                            }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Compartilhar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // CLOSE BUTTON
                            IconButton(onClick = { LivreTubePlayerManager.close(activity) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Fechar Vídeo",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 16:9 VIDEO CONTAINER
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                createConfiguredWebView(ctx, video.videoId) { isPlayerLoading = false }
                            },
                            update = { webView ->
                                updateWebViewVideo(webView, video.videoId)
                            }
                        )

                        if (isPlayerLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    // VIDEO DETAILS & ACTIONS
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = video.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = buildString {
                                append(video.author)
                                if (!video.viewsText.isNullOrBlank()) {
                                    append(" • ")
                                    append(video.viewsText)
                                }
                                if (!video.publishedText.isNullOrBlank()) {
                                    append(" • ")
                                    append(video.publishedText)
                                }
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // QUICK ACTION BUTTONS ROW
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { LivreTubePlayerManager.minimize(activity) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Minimizar",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            OutlinedButton(
                                onClick = { LivreTubePlayerManager.toggleLandscape(activity) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.ScreenRotation,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Girar Tela", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // SHIELD PRIVACY CARD
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("LivreTube Shield Ativo", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        "Reprodução limpa sem anúncios, sem rastreamento de perfil e com dados protegidos.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // CLOSE PLAYER BUTTON
                        OutlinedButton(
                            onClick = { LivreTubePlayerManager.close(activity) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fechar Vídeo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            // MINIMIZED MODE: DOCKED MINI-PLAYER BAR
            // Keep WebView attached to window in 1dp Box so audio never stops playing in background!
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0.01f)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        createConfiguredWebView(ctx, video.videoId) { isPlayerLoading = false }
                    },
                    update = { webView ->
                        updateWebViewVideo(webView, video.videoId)
                    }
                )
            }

            // MINI-PLAYER BAR (Floating above bottom navigation)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { LivreTubePlayerManager.expand() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        // MINI THUMBNAIL
                        Box(
                            modifier = Modifier
                                .width(74.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = video.thumbnailUrl,
                                contentDescription = video.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .align(Alignment.Center),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // TITLE & AUTHOR
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp)
                        ) {
                            Text(
                                text = video.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = video.author,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // EXPAND BUTTON
                        IconButton(
                            onClick = { LivreTubePlayerManager.expand() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.OpenInFull,
                                contentDescription = "Expandir Reprodutor",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // CLOSE BUTTON
                        IconButton(
                            onClick = { LivreTubePlayerManager.close(activity) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Fechar Reprodutor",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: Context,
    videoId: String,
    onLoaded: () -> Unit
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                super.onShowCustomView(view, callback)
                val act = context as? Activity
                LivreTubePlayerManager.setLandscape(act, true)
            }

            override fun onHideCustomView() {
                super.onHideCustomView()
                val act = context as? Activity
                LivreTubePlayerManager.setLandscape(act, false)
            }
        }

        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onLoaded()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        tag = videoId
        loadDataWithBaseURL("https://www.youtube-nocookie.com", buildEmbedHtml(videoId), "text/html", "UTF-8", null)
    }
}

private fun updateWebViewVideo(webView: WebView, videoId: String) {
    if (webView.tag != videoId) {
        webView.tag = videoId
        webView.loadDataWithBaseURL("https://www.youtube-nocookie.com", buildEmbedHtml(videoId), "text/html", "UTF-8", null)
    }
}

private fun buildEmbedHtml(videoId: String): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            * { margin: 0; padding: 0; box-sizing: border-box; background: #000; }
            body, html { width: 100%; height: 100%; overflow: hidden; background: #000; display: flex; align-items: center; justify-content: center; }
            iframe { width: 100%; height: 100%; border: none; }
          </style>
        </head>
        <body>
          <iframe 
            src="https://www.youtube-nocookie.com/embed/${videoId}?autoplay=1&modestbranding=1&rel=0&playsinline=1"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
            allowfullscreen>
          </iframe>
        </body>
        </html>
    """.trimIndent()
}
