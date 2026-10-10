package com.livrechat.app.ui.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrechat.app.LivreChatApp
import com.livrechat.app.data.local.entity.ConversationEntity
import com.livrechat.app.data.remote.model.AddContactRequest
import com.livrechat.app.data.remote.model.UserDto
import com.livrechat.app.ui.theme.BluePrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContactScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String, String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val apiService = LivreChatApp.instance.apiClient.apiService
    val db = LivreChatApp.instance.database

    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun performSearch() {
        if (query.trim().length < 2) return
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val res = apiService.searchUsers(query.trim())
                if (res.isSuccessful && res.body() != null) {
                    searchResults = res.body()!!
                    if (searchResults.isEmpty()) {
                        errorMessage = "Nenhum usuário encontrado com este nome ou ID."
                    }
                } else {
                    errorMessage = "Nenhum resultado."
                }
            } catch (e: Exception) {
                errorMessage = "Erro ao buscar: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    fun startChat(user: UserDto) {
        scope.launch {
            isLoading = true
            try {
                val res = apiService.getOrCreateConversation(mapOf("targetUserId" to user.id))
                if (res.isSuccessful && res.body() != null) {
                    val conv = res.body()!!
                    // Insert into local db
                    withContext(Dispatchers.IO) {
                        db.conversationDao().insertOrUpdate(
                            ConversationEntity(
                                id = conv.conversationId,
                                otherUserId = user.id,
                                otherUsername = user.username,
                                otherDisplayName = user.displayName,
                                otherAvatarUrl = user.avatarUrl,
                                otherPublicKey = user.publicKey,
                                lastMessageContent = null,
                                lastMessageTimestamp = System.currentTimeMillis()
                            )
                        )
                    }
                    onNavigateToChat(conv.conversationId, user.id, user.displayName)
                }
            } catch (e: Exception) {
                errorMessage = "Erro ao iniciar conversa: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adicionar Contato") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar por @nome_de_usuario ou ID") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { performSearch() }) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Buscar", tint = BluePrimary)
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BluePrimary)
                }
            }

            errorMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResults) { user ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = BluePrimary.copy(alpha = 0.2f),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = user.displayName.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = BluePrimary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = user.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "@${user.username}",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = { startChat(user) },
                                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Conversar", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
