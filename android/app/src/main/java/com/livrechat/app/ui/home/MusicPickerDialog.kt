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
    var selectedCategory by remember { mutableStateOf("Sugestões") }
    var isLoading by remember { mutableStateOf(false) }
    var tracksList by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var playingTrackId by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var favoritesSet by remember { mutableStateOf(setOf<String>()) }

    // Atualiza favoritos
    LaunchedEffect(Unit) {
        val favs = MusicCatalogService.getFavoriteTracks(context).map { it.id }.toSet()
        favoritesSet = favs
    }

    // Carrega músicas por categoria
    fun loadCategoryTracks(category: String) {
        scope.launch {
            isLoading = true
            try {
                if (category == "Favoritas") {
                    tracksList = MusicCatalogService.getFavoriteTracks(context)
                } else {
                    val searchTerm = when (category) {
                        "Sugestões" -> "Top Brasil 2026"
                        "Astral" -> "Good Vibes Acústico"
                        "Gênero musical" -> "Pop Sertanejo"
                        "Sertanejo" -> "Sertanejo 2026"
                        "Forró" -> "Vilões do Forró Wesley Safadão"
                        "Pop" -> "Pop Hits 2026"
                        "Gospel" -> "Gospel Louvor"
                        "Funk" -> "Funk Brasil"
                        "Trap & Rap" -> "Trap Brasil"
                        "Rock" -> "Rock Nacional"
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

    // Carrega primeira categoria ao abrir
    LaunchedEffect(Unit) {
        loadCategoryTracks("Sugestões")
    }

    // Busca com debounce quando o usuário digita
    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.isNotEmpty()) {
            delay(400) // Debounce de 400ms
            isLoading = true
            try {
                val results = MusicCatalogService.searchMusic(query, limit = 30)
                tracksList = results
            } catch (e: Exception) {
                tracksList = emptyList()
            } finally {
                isLoading = false
            }
        } else if (searchQuery.isEmpty() && selectedCategory != "Favoritas") {
            loadCategoryTracks(selectedCategory)
        }
    }

    // Controle de reprodução de prévia
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
        "Sugestões",
        "Astral",
        "Gênero musical",
        "Favoritas",
        "Sertanejo",
        "Forró",
        "Pop",
        "Gospel",
        "Funk",
        "Trap & Rap",
        "Rock"
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
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top drag handle & header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Músicas",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    IconButton(onClick = {
                        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (e: Exception) {}
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                // 1. Barra de Pesquisa (Estilo WhatsApp/Instagram)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Pesquisar por astral, músicas ou cantores",
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Pesquisar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpar")
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
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
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            focusedBorderColor = Color(0xFF25D366),
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    )
                }

                // 2. Chips de Categorias (Horizontal Scroll)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat && searchQuery.isEmpty()
                        val chipBg = if (isSelected) Color(0xFF25D366).copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        val chipText = if (isSelected) Color(0xFF1E8E3E) else MaterialTheme.colorScheme.onSurfaceVariant
                        val chipBorder = if (isSelected) Color(0xFF25D366) else Color.Transparent

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = chipBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, chipBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    searchQuery = ""
                                    selectedCategory = cat
                                    loadCategoryTracks(cat)
                                }
                        ) {
                            Text(
                                text = cat,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = chipText,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Opção de Escolher arquivo local do aparelho
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable {
                            try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (e: Exception) {}
                            onPickLocalFile()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Color(0xFF007AFF),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Escolher arquivo de áudio do meu aparelho",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 3. Conteúdo Principal: Carrossel em Destaque + Lista de Músicas
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF25D366))
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Nenhuma música encontrada",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Tente pesquisar outro cantor ou gênero musical",
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Card em Destaque no topo (Estilo WhatsApp / Instagram banner escuro da foto)
                        val featuredTrack = tracksList.firstOrNull()
                        if (featuredTrack != null && searchQuery.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFF424A32), // Tom verde-oliva elegante do screenshot
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Capa com botão de Play
                                        Box(
                                            modifier = Modifier
                                                .size(62.dp)
                                                .clip(RoundedCornerShape(12.dp))
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
                                                    .background(Color.Black.copy(alpha = 0.35f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (playingTrackId == featuredTrack.id) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(28.dp)
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
                                                color = Color.White.copy(alpha = 0.8f),
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        // Botão Estrela (Favoritos)
                                        val isFav = favoritesSet.contains(featuredTrack.id)
                                        IconButton(onClick = {
                                            val nowFav = MusicCatalogService.toggleFavorite(context, featuredTrack)
                                            favoritesSet = if (nowFav) favoritesSet + featuredTrack.id else favoritesSet - featuredTrack.id
                                        }) {
                                            Icon(
                                                imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = "Favoritar",
                                                tint = if (isFav) Color(0xFFFFD700) else Color.White
                                            )
                                        }

                                        // Botão Próximo / Selecionar ->
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White.copy(alpha = 0.22f),
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clickable {
                                                    try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (e: Exception) {}
                                                    onSelectTrack(featuredTrack)
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = "Selecionar",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Indicador de carrossel (pontinhos)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 14.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    repeat(5) { i ->
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 3.dp)
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (i == 0) Color(0xFF25D366)
                                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                                )
                                        )
                                    }
                                }
                            }
                        }

                        // Lista normal de Músicas
                        val listToShow = if (searchQuery.isEmpty() && featuredTrack != null) tracksList.drop(1) else tracksList
                        items(listToShow, key = { it.id }) { track ->
                            val isPlaying = playingTrackId == track.id
                            val isFav = favoritesSet.contains(track.id)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (e: Exception) {}
                                        onSelectTrack(track)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Capa do Álbum com Play/Pause Overlay
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
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
                                                if (isPlaying) Color(0xFF25D366).copy(alpha = 0.45f)
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

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isPlaying) Color(0xFF25D366) else MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${track.artist} · ${track.durationFormatted}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Botão Estrela (Favoritos)
                                IconButton(onClick = {
                                    val nowFav = MusicCatalogService.toggleFavorite(context, track)
                                    favoritesSet = if (nowFav) favoritesSet + track.id else favoritesSet - track.id
                                }) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Favoritar",
                                        tint = if (isFav) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }

                                // Botão de Selecionar (Seta para a direita cinza circular)
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable {
                                            try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (e: Exception) {}
                                            onSelectTrack(track)
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Selecionar",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
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
