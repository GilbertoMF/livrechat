package com.livrechat.app.ui.home

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.livrechat.app.data.remote.MusicCatalogService
import com.livrechat.app.data.remote.MusicTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BgDarkNavy = Color(0xFF090D1A)
private val CardSurface = Color(0xFF13192B)
private val CardSurfaceHover = Color(0xFF1C2540)
private val AccentSapphire = Color(0xFF2563EB)
private val AccentViolet = Color(0xFF7C3AED)
private val AccentCyan = Color(0xFF38BDF8)
private val GoldStar = Color(0xFFFFC107)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPickerDialog(
    onDismiss: () -> Unit,
    onSelectTrack: (MusicTrack) -> Unit,
    onPickLocalFile: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("🔥 Em Alta") }
    var isLoading by remember { mutableStateOf(false) }
    var tracksList by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var playingTrackId by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var favoritesSet by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        val favs = MusicCatalogService.getFavoriteTracks(context).map { it.id }.toSet()
        favoritesSet = favs
    }

    fun loadCategoryTracks(category: String) {
        scope.launch {
            isLoading = true
            try {
                if (category == "⭐ Favoritas") {
                    tracksList = MusicCatalogService.getFavoriteTracks(context)
                } else {
                    val searchTerm = when (category) {
                        "🔥 Em Alta" -> "Top Brasil 2026"
                        "🇧🇷 Sertanejo" -> "Sertanejo 2026"
                        "⚡ Trap & Rap" -> "Trap Brasil Matue"
                        "🕺 Funk" -> "Funk Brasil Hits"
                        "✨ Pop" -> "Pop Hits Brasil"
                        "🎸 Rock" -> "Rock Nacional"
                        "🙏 Gospel" -> "Gospel Louvor"
                        "🌴 Forró" -> "Forró Wesley Safadão"
                        "🎷 MPB" -> "MPB Acústico"
                        else -> category
                    }
                    val results = MusicCatalogService.searchMusic(searchTerm, limit = 30)
                    tracksList = results
                }
            } catch (e: Exception) {
                tracksList = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadCategoryTracks("🔥 Em Alta")
    }

    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.isNotEmpty()) {
            delay(400)
            isLoading = true
            try {
                val results = MusicCatalogService.searchMusic(query, limit = 30)
                tracksList = results
            } catch (e: Exception) {
                tracksList = emptyList()
            } finally {
                isLoading = false
            }
        } else if (searchQuery.isEmpty() && selectedCategory != "⭐ Favoritas") {
            loadCategoryTracks(selectedCategory)
        }
    }

    fun togglePlayTrack(track: MusicTrack) {
        if (playingTrackId == track.id) {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (e: Exception) {}
            mediaPlayer = null
            playingTrackId = null
        } else {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (e: Exception) {}
            mediaPlayer = null
            playingTrackId = null

            try {
                val mp = MediaPlayer().apply {
                    setDataSource(track.previewUrl)
                    setOnPreparedListener {
                        it.start()
                        playingTrackId = track.id
                    }
                    setOnCompletionListener {
                        playingTrackId = null
                    }
                    setOnErrorListener { _, _, _ ->
                        playingTrackId = null
                        Toast.makeText(context, "Erro ao reproduzir prévia", Toast.LENGTH_SHORT).show()
                        true
                    }
                    prepareAsync()
                }
                mediaPlayer = mp
            } catch (e: Exception) {
                Toast.makeText(context, "Falha na prévia de áudio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (e: Exception) {}
            mediaPlayer = null
            playingTrackId = null
        }
    }

    val categories = listOf(
        "🔥 Em Alta",
        "⭐ Favoritas",
        "🇧🇷 Sertanejo",
        "⚡ Trap & Rap",
        "🕺 Funk",
        "✨ Pop",
        "🎸 Rock",
        "🙏 Gospel",
        "🌴 Forró",
        "🎷 MPB"
    )

    Dialog(
        onDismissRequest = {
            try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (e: Exception) {}
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BgDarkNavy
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Custom LivreChat Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = AccentSapphire.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentSapphire.copy(alpha = 0.5f)),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "LivreChat Music",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Escolha qualquer trilha para seu status",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .size(36.dp)
                            .clickable {
                                try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (e: Exception) {}
                                onDismiss()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // 1. Barra de Busca LivreChat (Futuristic Sapphire Outline)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Buscar artistas, músicas ou lançamentos...",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Buscar", tint = AccentCyan)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpar", tint = Color.White)
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (searchQuery.isNotBlank()) {
                                scope.launch {
                                    isLoading = true
                                    tracksList = MusicCatalogService.searchMusic(searchQuery.trim(), limit = 30)
                                    isLoading = false
                                }
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardSurface,
                            unfocusedContainerColor = CardSurface.copy(alpha = 0.7f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AccentSapphire,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    )
                }

                // 2. Chips de Categorias Originais com Gradiente LivreChat
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat && searchQuery.isEmpty()
                        val chipBrush = if (isSelected) {
                            Brush.horizontalGradient(listOf(AccentSapphire, AccentViolet))
                        } else {
                            Brush.linearGradient(listOf(CardSurface, CardSurface))
                        }
                        val borderColor = if (isSelected) AccentCyan else Color.White.copy(alpha = 0.08f)

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(chipBrush)
                                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                                .clickable {
                                    searchQuery = ""
                                    selectedCategory = cat
                                    loadCategoryTracks(cat)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = cat,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }

                // Opção de Escolher arquivo do aparelho
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable {
                            try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (e: Exception) {}
                            onPickLocalFile()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AccentCyan.copy(alpha = 0.15f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Usar áudio do seu celular",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "MP3, M4A, gravações ou qualquer arquivo local",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 3. Conteúdo Principal
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentSapphire)
                    }
                } else if (tracksList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.MusicOff,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(46.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Nenhuma música encontrada",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Tente pesquisar outro cantor, banda ou título",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Card em Destaque no topo (Estilo LivreChat Luxury Audio Card)
                        val featuredTrack = tracksList.firstOrNull()
                        if (featuredTrack != null && searchQuery.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    Color(0xFF1B2444),
                                                    Color(0xFF11172A),
                                                    Color(0xFF251640)
                                                )
                                            )
                                        )
                                        .border(1.dp, AccentSapphire.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                                        .padding(14.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = AccentSapphire.copy(alpha = 0.25f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = "✨ EM ALTA NO LIVRECHAT",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AccentCyan,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }

                                            val isFav = favoritesSet.contains(featuredTrack.id)
                                            IconButton(
                                                onClick = {
                                                    val nowFav = MusicCatalogService.toggleFavorite(context, featuredTrack)
                                                    favoritesSet = if (nowFav) favoritesSet + featuredTrack.id else favoritesSet - featuredTrack.id
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                                    contentDescription = "Favoritar",
                                                    tint = if (isFav) GoldStar else Color.White.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Capa do Álbum com Play/Pause
                                            Box(
                                                modifier = Modifier
                                                    .size(68.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .clickable { togglePlayTrack(featuredTrack) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AsyncImage(
                                                    model = featuredTrack.albumArtUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(
                                                            if (playingTrackId == featuredTrack.id) AccentSapphire.copy(alpha = 0.6f)
                                                            else Color.Black.copy(alpha = 0.4f)
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (playingTrackId == featuredTrack.id) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(30.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(14.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = featuredTrack.title,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = featuredTrack.artist,
                                                    color = Color.White.copy(alpha = 0.75f),
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Duração • ${featuredTrack.durationFormatted}",
                                                    color = AccentCyan,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Botão Adicionar ao Status
                                        Button(
                                            onClick = {
                                                try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (e: Exception) {}
                                                onSelectTrack(featuredTrack)
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(40.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Brush.horizontalGradient(listOf(AccentSapphire, AccentViolet)))
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Usar esta música no Status", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Lista normal de Músicas
                        val listToShow = if (searchQuery.isEmpty() && featuredTrack != null) tracksList.drop(1) else tracksList
                        items(listToShow, key = { it.id }) { track ->
                            val isPlaying = playingTrackId == track.id
                            val isFav = favoritesSet.contains(track.id)

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isPlaying) CardSurfaceHover else CardSurface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isPlaying) AccentSapphire.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.05f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (e: Exception) {}
                                        onSelectTrack(track)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Capa do Álbum com Play/Pause
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { togglePlayTrack(track) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = track.albumArtUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    if (isPlaying) AccentSapphire.copy(alpha = 0.65f)
                                                    else Color.Black.copy(alpha = 0.35f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isPlaying) AccentCyan else Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${track.artist} • ${track.durationFormatted}",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Favoritar
                                    IconButton(
                                        onClick = {
                                            val nowFav = MusicCatalogService.toggleFavorite(context, track)
                                            favoritesSet = if (nowFav) favoritesSet + track.id else favoritesSet - track.id
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "Favoritar",
                                            tint = if (isFav) GoldStar else Color.White.copy(alpha = 0.4f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Botão Selecionar
                                    Surface(
                                        shape = CircleShape,
                                        color = AccentSapphire.copy(alpha = 0.2f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentSapphire.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clickable {
                                                try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (e: Exception) {}
                                                onSelectTrack(track)
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Selecionar",
                                                tint = AccentCyan,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
