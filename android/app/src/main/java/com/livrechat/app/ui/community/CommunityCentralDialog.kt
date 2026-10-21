package com.livrechat.app.ui.community

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.livrechat.app.LivreChatApp
import com.livrechat.app.data.remote.CommunityStatsDto
import com.livrechat.app.data.remote.CommunityUserItemDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityCentralDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<CommunityStatsDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("todos") } // todos, online, ativos

    fun loadStats() {
        isLoading = true
        errorMessage = null
        scope.launch(Dispatchers.IO) {
            try {
                val api = LivreChatApp.instance.apiService
                val response = api.getCommunityStats()
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    withContext(Dispatchers.Main) {
                        stats = body
                        isLoading = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Erro ao carregar dados do servidor (${response.code()})"
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.localizedMessage ?: "Falha na conexão com o servidor"
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadStats()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F131C),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Analytics,
                                    contentDescription = null,
                                    tint = Color(0xFFA78BFA),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Central de Usuários",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Métricas em tempo real da rede LivreChat",
                                fontSize = 11.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF1E2433), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isLoading && stats == null) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF8B5CF6))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Carregando métricas da comunidade...",
                                fontSize = 13.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                    }
                } else if (errorMessage != null && stats == null) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = errorMessage!!,
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { loadStats() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                            ) {
                                Text("Tentar Novamente")
                            }
                        }
                    }
                } else if (stats != null) {
                    val currentStats = stats!!

                    // 4 Main Metric KPI Cards (2x2 Grid)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Card 1: Total Criados
                            KpiCard(
                                title = "Contas Criadas",
                                value = currentStats.totalAccounts.toString(),
                                subtitle = "Usuários cadastrados",
                                icon = "👥",
                                valueColor = Color(0xFFA78BFA),
                                borderColor = Color(0xFF8B5CF6).copy(alpha = 0.35f),
                                modifier = Modifier.weight(1f)
                            )

                            // Card 2: Online Agora
                            KpiCard(
                                title = "Online Agora",
                                value = currentStats.onlineUsers.toString(),
                                subtitle = "Conexões ativas (WS)",
                                icon = "🟢",
                                valueColor = Color(0xFF34D399),
                                borderColor = Color(0xFF10B981).copy(alpha = 0.4f),
                                isPulse = currentStats.onlineUsers > 0,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Card 3: Offline
                            KpiCard(
                                title = "Usuários Offline",
                                value = currentStats.offlineUsers.toString(),
                                subtitle = "Desconectados",
                                icon = "⚪",
                                valueColor = Color(0xFF9CA3AF),
                                borderColor = Color(0xFF374151).copy(alpha = 0.4f),
                                modifier = Modifier.weight(1f)
                            )

                            // Card 4: Ativos Hoje
                            KpiCard(
                                title = "Ativos Hoje",
                                value = currentStats.activeToday.toString(),
                                subtitle = "Nas últimas 24 horas",
                                icon = "⚡",
                                valueColor = Color(0xFF60A5FA),
                                borderColor = Color(0xFF3B82F6).copy(alpha = 0.35f),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary Activity Banner
                    Surface(
                        color = Color(0xFF151A24),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232B3B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "💬 ${currentStats.totalMessages} mensagens • 👥 ${currentStats.totalConversations} chats",
                                fontSize = 11.sp,
                                color = Color(0xFF9CA3AF),
                                fontWeight = FontWeight.Medium
                            )

                            val uptimeStr = formatUptime(currentStats.uptimeSeconds)
                            Text(
                                text = "⏱️ Uptime: $uptimeStr",
                                fontSize = 11.sp,
                                color = Color(0xFF60A5FA),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Filter Chips & Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Pesquisar por nome ou @username...",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Limpar",
                                        tint = Color(0xFF9CA3AF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = Color(0xFF232B3B),
                            focusedContainerColor = Color(0xFF151A24),
                            unfocusedContainerColor = Color(0xFF151A24),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Filter selector chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterPill(
                            label = "Todos (${currentStats.users.size})",
                            isSelected = selectedFilter == "todos",
                            onClick = { selectedFilter = "todos" }
                        )
                        FilterPill(
                            label = "🟢 Online (${currentStats.onlineUsers})",
                            isSelected = selectedFilter == "online",
                            onClick = { selectedFilter = "online" }
                        )
                        FilterPill(
                            label = "⚡ Ativos Hoje (${currentStats.activeToday})",
                            isSelected = selectedFilter == "ativos",
                            onClick = { selectedFilter = "ativos" }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Users List
                    val filteredList = remember(currentStats.users, searchQuery, selectedFilter) {
                        currentStats.users.filter { u ->
                            val matchesSearch = searchQuery.isBlank() ||
                                    u.displayName.contains(searchQuery, ignoreCase = true) ||
                                    u.username.contains(searchQuery, ignoreCase = true)

                            val matchesFilter = when (selectedFilter) {
                                "online" -> u.isOnline
                                "ativos" -> {
                                    if (u.isOnline) true
                                    else {
                                        val act = u.lastActive
                                        if (act != null) {
                                            try {
                                                val t = parseIsoTime(act)
                                                (System.currentTimeMillis() - t) <= (24 * 60 * 60 * 1000)
                                            } catch (e: Exception) { false }
                                        } else false
                                    }
                                }
                                else -> true
                            }

                            matchesSearch && matchesFilter
                        }
                    }

                    if (filteredList.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhum usuário corresponde ao filtro.",
                                fontSize = 13.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredList, key = { it.id }) { user ->
                                UserCommunityCard(user = user)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bottom Actions Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://livrechat.duckdns.org/central"))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA78BFA)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Painel Web", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { loadStats() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Atualizar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: String,
    valueColor: Color,
    borderColor: Color,
    isPulse: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF151A24),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = icon, fontSize = 18.sp)
                if (isPulse) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF10B981),
                        modifier = Modifier.size(8.dp)
                    ) {}
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = Color(0xFF9CA3AF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color(0xFF8B5CF6) else Color(0xFF151A24),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF8B5CF6) else Color(0xFF232B3B)
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else Color(0xFF9CA3AF),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun UserCommunityCard(
    user: CommunityUserItemDto
) {
    val context = LocalContext.current

    Surface(
        color = Color(0xFF151A24),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (user.isOnline) Color(0xFF10B981).copy(alpha = 0.35f) else Color(0xFF232B3B)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF232B3B)),
                contentAlignment = Alignment.Center
            ) {
                if (!user.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(user.avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = user.displayName,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val initial = user.displayName.ifBlank { user.username }.take(1).uppercase()
                    Text(
                        text = initial,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // User Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName.ifBlank { user.username },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@${user.username} • Cadastrado em ${formatCreatedAt(user.createdAt)}",
                    fontSize = 10.sp,
                    color = Color(0xFF9CA3AF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Status Badge
            if (user.isOnline) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981),
                            modifier = Modifier.size(6.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ONLINE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399)
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF374151).copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF374151).copy(alpha = 0.3f))
                ) {
                    Text(
                        text = formatTimeAgo(user.lastActive),
                        fontSize = 9.sp,
                        color = Color(0xFF9CA3AF),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

private fun parseIsoTime(isoString: String): Long {
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd HH:mm:ss"
    )
    for (fmt in formats) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val d = sdf.parse(isoString)
            if (d != null) return d.time
        } catch (_: Exception) {}
    }
    return 0L
}

private fun formatTimeAgo(isoString: String?): String {
    if (isoString.isNullOrBlank()) return "Offline"
    val time = parseIsoTime(isoString)
    if (time == 0L) return "Offline"
    val diffMs = System.currentTimeMillis() - time
    val diffSec = diffMs / 1000
    if (diffSec < 60) return "Agora mesmo"
    val diffMin = diffSec / 60
    if (diffMin < 60) return "${diffMin}m atrás"
    val diffHr = diffMin / 60
    if (diffHr < 24) return "${diffHr}h atrás"
    val diffDays = diffHr / 24
    return "${diffDays}d atrás"
}

private fun formatCreatedAt(isoString: String?): String {
    if (isoString.isNullOrBlank()) return "--"
    val time = parseIsoTime(isoString)
    if (time == 0L) return "--"
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return sdf.format(Date(time))
}

private fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "0m"
    val d = seconds / 86400
    val h = (seconds % 86400) / 3600
    val m = (seconds % 3600) / 60
    if (d > 0) return "${d}d ${h}h"
    if (h > 0) return "${h}h ${m}m"
    return "${m}m"
}
