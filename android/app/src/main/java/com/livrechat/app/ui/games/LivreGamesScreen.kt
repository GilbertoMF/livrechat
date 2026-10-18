package com.livrechat.app.ui.games

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.livrechat.app.LivreChatApp
import com.livrechat.app.data.remote.LivreGameDto
import com.livrechat.app.data.remote.LivreGameServerInfoDto
import com.livrechat.app.ui.theme.DangerRed
import com.livrechat.app.ui.theme.SuccessGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivreGamesScreen(
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember { GameDownloadManager.getInstance(context) }
    val downloadStates by downloadManager.downloadStates.collectAsState()

    var games by remember { mutableStateOf<List<LivreGameDto>>(emptyList()) }
    var serverInfo by remember { mutableStateOf<LivreGameServerInfoDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var selectedCategory by remember { mutableStateOf("Todos") }
    var searchQuery by remember { mutableStateOf("") }

    var showInstallPpssppDialog by remember { mutableStateOf(false) }
    var showMultiplayerGuideDialog by remember { mutableStateOf(false) }
    var selectedGameForGuide by remember { mutableStateOf<LivreGameDto?>(null) }
    var gameToDelete by remember { mutableStateOf<LivreGameDto?>(null) }

    val categories = listOf("Todos", "Luta", "Ação", "Corrida", "Futebol", "Arcade")

    // Function to load catalog
    fun loadCatalog() {
        isLoading = true
        errorMessage = null
        scope.launch(Dispatchers.IO) {
            try {
                val api = LivreChatApp.instance.apiService
                val response = api.getGames()
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    withContext(Dispatchers.Main) {
                        games = body.games
                        serverInfo = body.server
                        isLoading = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Não foi possível carregar o catálogo de jogos."
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Erro de conexão: ${e.message}"
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadCatalog()
    }

    // Filter games
    val filteredGames = remember(games, selectedCategory, searchQuery) {
        games.filter { game ->
            val matchesCategory = selectedCategory == "Todos" || game.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    game.title.contains(searchQuery, ignoreCase = true) ||
                    game.description.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    tint = Color(0xFFA78BFA),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "LivreGames",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "PSP Multiplayer sob Demanda",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showMultiplayerGuideDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Como Jogar",
                            tint = Color(0xFF2979FF)
                        )
                    }
                    IconButton(onClick = { loadCatalog() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Server Status Banner
            Surface(
                color = Color(0xFF151A23),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232B3B))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = SuccessGreen,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Servidor Ad-Hoc Oficial Ativo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SuccessGreen
                            )
                            Text(
                                text = "Oracle Cloud: ${serverInfo?.proAdhocServer ?: "164.152.40.160"}:${serverInfo?.portOffset ?: 10000}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2979FF).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2979FF).copy(alpha = 0.4f)),
                        modifier = Modifier.clickable { showMultiplayerGuideDialog = true }
                    ) {
                        Text(
                            text = "Guia Multiplayer",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64B5F6),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Pesquisar jogos (DBZ, GTA, Naruto, Tekken...)",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Limpar",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8B5CF6),
                    unfocusedBorderColor = Color(0xFF232B3B),
                    focusedContainerColor = Color(0xFF151A23),
                    unfocusedContainerColor = Color(0xFF151A23)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Category Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat },
                        label = {
                            Text(
                                text = cat,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF8B5CF6),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF151A23),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) Color(0xFF8B5CF6) else Color(0xFF232B3B)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            // Games List
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF8B5CF6))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Carregando catálogo de jogos...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { loadCatalog() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                        ) {
                            Text("Tentar Novamente")
                        }
                    }
                }
            } else if (filteredGames.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Nenhum jogo encontrado.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredGames, key = { it.id }) { game ->
                        GameCard(
                            game = game,
                            downloadProgress = downloadStates[game.id],
                            isDownloaded = downloadManager.isGameDownloaded(game.fileName, game.sizeBytes),
                            onDownloadClick = {
                                downloadManager.startDownload(
                                    gameId = game.id,
                                    downloadUrl = game.downloadUrl,
                                    fileName = game.fileName,
                                    expectedSizeBytes = game.sizeBytes
                                )
                            },
                            onCancelDownload = {
                                downloadManager.cancelDownload(game.id)
                            },
                            onPlayClick = {
                                val gameFile = downloadManager.getGameFile(game.fileName)
                                if (gameFile.exists()) {
                                    // 1. Inject PPSSPP multiplayer network settings
                                    PpssppConfigManager.injectMultiplayerConfig(
                                        context = context,
                                        serverIp = serverInfo?.proAdhocServer ?: "164.152.40.160",
                                        portOffset = serverInfo?.portOffset ?: 10000
                                    )

                                    // 2. Check if PPSSPP is installed
                                    if (!PpssppConfigManager.isPpssppInstalled(context)) {
                                        selectedGameForGuide = game
                                        showInstallPpssppDialog = true
                                    } else {
                                        // 3. Launch game directly
                                        val launched = PpssppConfigManager.launchGame(context, gameFile)
                                        if (launched) {
                                            Toast.makeText(
                                                context,
                                                "Iniciando ${game.title}! Servidor Ad-Hoc conectado.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Abra o PPSSPP e selecione o jogo na pasta do LivreChat.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            },
                            onDeleteClick = {
                                gameToDelete = game
                            }
                        )
                    }
                }
            }
        }
    }

    // Install PPSSPP Dialog
    if (showInstallPpssppDialog) {
        AlertDialog(
            onDismissRequest = { showInstallPpssppDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Emulador PPSSPP Necessário", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        "Para rodar os jogos de PSP em alta definição (60 FPS) e com multiplayer online, você precisa do emulador gratuito PPSSPP instalado no seu aparelho.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "O LivreChat já configurou o servidor Ad-Hoc na Oracle Cloud automaticamente. Assim que instalar o PPSSPP, basta clicar em 'Jogar Multiplayer'!",
                        fontSize = 12.sp,
                        color = SuccessGreen
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        PpssppConfigManager.openPlayStore(context)
                        showInstallPpssppDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF))
                ) {
                    Text("Instalar via Google Play")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        PpssppConfigManager.openDirectApkDownload(context)
                        showInstallPpssppDialog = false
                    }
                ) {
                    Text("Baixar APK Oficial")
                }
            }
        )
    }

    // Multiplayer Guide Dialog
    if (showMultiplayerGuideDialog) {
        AlertDialog(
            onDismissRequest = { showMultiplayerGuideDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WifiTethering,
                        contentDescription = null,
                        tint = SuccessGreen
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Como Jogar em Multiplayer", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "O LivreChat automatiza tudo para você e seus amigos jogarem juntos:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row {
                        Text("1. ", fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                        Text("Baixe o jogo desejado no catálogo do LivreChat.", fontSize = 12.sp)
                    }
                    Row {
                        Text("2. ", fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                        Text("Toque em 'Jogar Multiplayer'. O LivreChat injeta o servidor ProAdhocServer (164.152.40.160:10000) no emulador.", fontSize = 12.sp)
                    }
                    Row {
                        Text("3. ", fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                        Text("Dentro do jogo, entre no menu de Rede / Wireless / Ad-Hoc / Batalha.", fontSize = 12.sp)
                    }
                    Row {
                        Text("4. ", fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                        Text("Um jogador cria a sala e o outro entra. Vocês já estarão conectados na mesma partida!", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showMultiplayerGuideDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                ) {
                    Text("Entendi!")
                }
            }
        )
    }

    // Confirm Delete Dialog
    if (gameToDelete != null) {
        AlertDialog(
            onDismissRequest = { gameToDelete = null },
            title = { Text("Excluir Jogo?") },
            text = {
                Text("Deseja apagar o arquivo de ${gameToDelete!!.title} (${gameToDelete!!.size}) do seu aparelho para liberar espaço?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        downloadManager.deleteGame(gameToDelete!!.id, gameToDelete!!.fileName)
                        Toast.makeText(context, "${gameToDelete!!.title} excluído.", Toast.LENGTH_SHORT).show()
                        gameToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { gameToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun GameCard(
    game: LivreGameDto,
    downloadProgress: GameDownloadProgress?,
    isDownloaded: Boolean,
    onDownloadClick: () -> Unit,
    onCancelDownload: () -> Unit,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isDownloading = downloadProgress?.state == DownloadState.DOWNLOADING
    val hasError = downloadProgress?.state == DownloadState.ERROR

    Surface(
        color = Color(0xFF151A23),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDownloaded) Color(0xFF00E676).copy(alpha = 0.4f) else Color(0xFF232B3B)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Game Cover Image
                AsyncImage(
                    model = game.coverUrl,
                    contentDescription = game.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 85.dp, height = 115.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1F2937))
                )

                Spacer(modifier = Modifier.width(14.dp))

                // Game Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF8B5CF6).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = game.category,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA78BFA),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (isDownloaded) {
                            IconButton(
                                onClick = onDeleteClick,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Excluir",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = game.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!game.subtitle.isNullOrBlank()) {
                        Text(
                            text = game.subtitle,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Meta Row (Size + Players)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF232B3B)
                        ) {
                            Text(
                                text = "💾 ${game.size}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF232B3B)
                        ) {
                            Text(
                                text = "👥 ${game.maxPlayers} Jogadores",
                                fontSize = 10.sp,
                                color = Color(0xFF64B5F6),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Description
            Text(
                text = game.description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Download Progress Bar (When downloading)
            if (isDownloading && downloadProgress != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Baixando: ${downloadProgress.progressPercent}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B5CF6)
                        )

                        val downloadedMb = (downloadProgress.bytesDownloaded.toDouble() / (1024 * 1024)).toInt()
                        val totalMb = (downloadProgress.totalBytes.toDouble() / (1024 * 1024)).toInt()
                        Text(
                            text = "$downloadedMb MB / $totalMb MB",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { downloadProgress.progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF8B5CF6),
                            trackColor = Color(0xFF232B3B)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = onCancelDownload,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancelar",
                                tint = DangerRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else if (hasError) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Erro no download: ${downloadProgress?.errorMessage ?: "Falha de rede"}",
                        fontSize = 11.sp,
                        color = DangerRed,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = onDownloadClick) {
                        Text("Tentar Novamente", fontSize = 11.sp, color = Color(0xFF8B5CF6))
                    }
                }
            } else {
                // Action Buttons: Baixar vs Jogar Multiplayer
                if (isDownloaded) {
                    Button(
                        onClick = onPlayClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E676),
                            contentColor = Color(0xFF0B0E14)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Jogar Multiplayer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Button(
                        onClick = onDownloadClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Baixar Jogo (${game.size})",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
