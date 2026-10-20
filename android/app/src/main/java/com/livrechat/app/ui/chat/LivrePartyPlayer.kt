package com.livrechat.app.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Builds HTML for YouTube IFrame API or native HTML5 video with synchronized JS controls
 */
fun buildPartyPlayerHtml(url: String, isPlaying: Boolean, currentTime: Long): String {
    val cleanUrl = url.trim()
    val ytRegex = """(?:youtu\.be\/|youtube\.com\/(?:embed\/|v\/|watch\?v=|watch\?.+&v=|shorts\/))([\w-]{11})""".toRegex()
    val match = ytRegex.find(cleanUrl)
    val youtubeId = match?.groupValues?.get(1) ?: if (cleanUrl.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) cleanUrl else null

    return if (youtubeId != null) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            * { margin:0; padding:0; box-sizing:border-box; background:#000; }
            html, body, #player-container, #player { width:100%; height:100%; overflow:hidden; background:#000; }
            iframe { width:100% !important; height:100% !important; border:none; }
          </style>
        </head>
        <body>
          <div id="player-container"><div id="player"></div></div>
          <script src="https://www.youtube.com/iframe_api"></script>
          <script>
            var player;
            var isReady = false;
            function onYouTubeIframeAPIReady() {
              player = new YT.Player('player', {
                height: '100%',
                width: '100%',
                videoId: '$youtubeId',
                playerVars: {
                  autoplay: 1,
                  playsinline: 1,
                  controls: 1,
                  rel: 0,
                  modestbranding: 1
                },
                events: {
                  'onReady': onPlayerReady
                }
              });
            }
            function onPlayerReady(event) {
              isReady = true;
              if (${isPlaying}) {
                try { player.playVideo(); } catch(e){}
              } else {
                try { player.pauseVideo(); } catch(e){}
              }
              if (${currentTime} > 0) {
                try { player.seekTo(${currentTime}, true); } catch(e){}
              }
            }
            function setPlayState(play) {
              if (!player || !isReady) return;
              try {
                if (play) player.playVideo(); else player.pauseVideo();
              } catch(e){}
            }
            function seekVideo(seconds) {
              if (!player || !isReady) return;
              try { player.seekTo(seconds, true); } catch(e){}
            }
          </script>
        </body>
        </html>
        """.trimIndent()
    } else {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            * { margin:0; padding:0; box-sizing:border-box; background:#000; }
            html, body { width:100%; height:100%; overflow:hidden; background:#000; display:flex; align-items:center; justify-content:center; }
            video { width:100%; height:100%; max-height:100%; object-fit:contain; }
          </style>
        </head>
        <body>
          <video id="vid" src="$cleanUrl" controls autoplay playsinline></video>
          <script>
            var vid = document.getElementById('vid');
            if (${isPlaying}) {
              vid.play().catch(function(){});
            } else {
              vid.pause();
            }
            if (${currentTime} > 0) {
              try { vid.currentTime = ${currentTime}; } catch(e){}
            }
            function setPlayState(play) {
              if (!vid) return;
              if (play) vid.play().catch(function(){}); else vid.pause();
            }
            function seekVideo(seconds) {
              if (!vid) return;
              try { vid.currentTime = seconds; } catch(e){}
            }
          </script>
        </body>
        </html>
        """.trimIndent()
    }
}

/**
 * Facebook-Messenger-style "Watch Together" (Assistir Juntos) synchronized video player.
 * Pinned at the top of the chat view while messages continue scrolling underneath.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LivrePartyWatchTogetherPlayer(
    party: ChatViewModel.PartySession,
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    var isMinimized by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember(party.videoUrl) { mutableStateOf(true) }

    // Sync playback state when remote updates or local action occurs
    LaunchedEffect(party.isPlaying) {
        val cmd = if (party.isPlaying) "setPlayState(true);" else "setPlayState(false);"
        webViewRef?.evaluateJavascript(cmd, null)
    }

    LaunchedEffect(party.currentTime) {
        if (party.currentTime > 0) {
            webViewRef?.evaluateJavascript("seekVideo(${party.currentTime});", null)
        }
    }

    AnimatedContent(
        targetState = isMinimized,
        transitionSpec = {
            fadeIn() + expandVertically() togetherWith fadeOut() + shrinkVertically()
        },
        label = "WatchTogetherTransition",
        modifier = modifier.fillMaxWidth()
    ) { minimized ->
        if (minimized) {
            // MINIMIZED COMPACT BAR (Allows maximum chat view while listening/watching)
            Surface(
                color = Color(0xFF1E1B4B),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickable { isMinimized = false }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF6366F1), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🍿 Assistindo Juntos",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA5B4FC)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (party.isPlaying) Color(0xFF10B981) else Color(0xFFF59E0B)
                            ) {
                                Text(
                                    text = if (party.isPlaying) "AO VIVO" else "PAUSADO",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = party.videoTitle.ifBlank { party.videoUrl },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Play/Pause Quick Toggle
                    IconButton(
                        onClick = {
                            viewModel.syncPartyAction(if (party.isPlaying) "PAUSE" else "PLAY", party.currentTime)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (party.isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Expand to Video Player
                    IconButton(
                        onClick = { isMinimized = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expandir Vídeo",
                            tint = Color(0xFFA5B4FC),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Close Session
                    IconButton(
                        onClick = { viewModel.closePartySession() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Sair do Assistir Juntos",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        } else {
            // EXPANDED VIDEO PLAYER (Like Facebook Messenger Watch Together)
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header Bar with status & action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF1E1B4B), Color(0xFF312E81))
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFF6366F1), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🍿 Assistir Juntos",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (party.isPlaying) Color(0xFF10B981) else Color(0xFFF59E0B)
                                ) {
                                    Text(
                                        text = if (party.isPlaying) "AO VIVO" else "PAUSADO",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = party.videoTitle.ifBlank { party.videoUrl },
                                fontSize = 11.sp,
                                color = Color(0xFFC7D2FE),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Minimize Button (🔽)
                        IconButton(
                            onClick = { isMinimized = true },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Minimizar Vídeo",
                                tint = Color(0xFFA5B4FC),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Close Button (✕)
                        IconButton(
                            onClick = { viewModel.closePartySession() },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar Assistir Juntos",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Video Player View (16:9 ratio, ~210.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        mediaPlaybackRequiresUserGesture = false
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                        cacheMode = WebSettings.LOAD_DEFAULT
                                    }

                                    webChromeClient = object : WebChromeClient() {}
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            isLoading = false
                                        }
                                    }

                                    loadDataWithBaseURL(
                                        "https://www.youtube-nocookie.com",
                                        buildPartyPlayerHtml(party.videoUrl, party.isPlaying, party.currentTime),
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
                                    webViewRef = this
                                }
                            },
                            update = { view ->
                                webViewRef = view
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.8f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFF8B5CF6), modifier = Modifier.size(36.dp))
                            }
                        }
                    }

                    // Synchronized Playback Control Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1B4B))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Rewind -10s
                        IconButton(
                            onClick = { viewModel.seekParty(-10) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Voltar 10s",
                                tint = Color(0xFFA5B4FC),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Play/Pause Main Toggle
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF6366F1),
                            modifier = Modifier
                                .size(40.dp)
                                .clickable {
                                    viewModel.syncPartyAction(
                                        if (party.isPlaying) "PAUSE" else "PLAY",
                                        party.currentTime
                                    )
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (party.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Forward +10s
                        IconButton(
                            onClick = { viewModel.seekParty(10) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Avançar 10s",
                                tint = Color(0xFFA5B4FC),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Peer status tag
                        Text(
                            text = if (party.isPlaying) "Sincronizado • ${party.updatedBy}" else "Pausado • ${party.updatedBy}",
                            fontSize = 11.sp,
                            color = Color(0xFFC7D2FE),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
