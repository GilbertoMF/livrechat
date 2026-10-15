package com.livrechat.app.ui.contacts

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.livrechat.app.LivreChatApp
import com.livrechat.app.data.local.entity.ConversationEntity
import com.livrechat.app.data.remote.model.AddContactRequest
import com.livrechat.app.data.remote.model.CreateGroupRequest
import com.livrechat.app.data.remote.model.UserDto
import com.livrechat.app.sync.ContactSyncHelper
import com.livrechat.app.ui.common.UserAvatarView
import com.livrechat.app.ui.theme.BluePrimary
import com.livrechat.app.ui.theme.DangerRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContactUiModel(
    val userId: String,
    val displayName: String,
    val username: String,
    val avatarUrl: String? = null,
    val bio: String = "",
    val isSelf: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContactScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String, String, String) -> Unit,
    onNavigateToQRCode: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = LivreChatApp.instance
    val apiService = app.apiClient.apiService
    val db = app.database
    val prefs = app.preferencesManager

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var contactsList by remember { mutableStateOf<List<ContactUiModel>>(emptyList()) }
    var remoteSearchResults by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var newContactUsername by remember { mutableStateOf("") }
    var isAddingContact by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<ContactUiModel?>(null) }
    var contactToEdit by remember { mutableStateOf<ContactUiModel?>(null) }
    var editNicknameText by remember { mutableStateOf("") }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var newGroupTitle by remember { mutableStateOf("") }
    var selectedGroupMembers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isCreatingGroup by remember { mutableStateOf(false) }
    var isSyncingPhoneContacts by remember { mutableStateOf(false) }
    var pendingSyncTrigger by remember { mutableStateOf(0) }

    fun loadContacts() {
        scope.launch {
            isLoading = true
            try {
                // Background sync device contacts if permission is already granted
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        ContactSyncHelper.syncDeviceContacts(context)
                    } catch (syncEx: Exception) {}
                }

                val currentUserId = prefs.currentUserId
                val list = mutableListOf<ContactUiModel>()

                // 1. Add current user as "(Você) - Mensagens para mim"
                if (currentUserId != null) {
                    val myUsername = prefs.currentUsername ?: "eu"
                    val myName = prefs.currentDisplayName ?: "Você"
                    list.add(
                        ContactUiModel(
                            userId = currentUserId,
                            displayName = myName,
                            username = myUsername,
                            avatarUrl = null,
                            bio = "Mensagens para mim",
                            isSelf = true
                        )
                    )
                }

                // 2. Fetch server contacts
                val contactsRes = try { apiService.getContacts() } catch (e: Exception) { null }
                if (contactsRes?.isSuccessful == true && contactsRes.body() != null) {
                    contactsRes.body()!!.forEach { c ->
                        if (c.contactUserId != currentUserId && list.none { it.userId == c.contactUserId }) {
                            list.add(
                                ContactUiModel(
                                    userId = c.contactUserId,
                                    displayName = c.displayName,
                                    username = c.username,
                                    avatarUrl = c.avatarUrl,
                                    bio = c.bio.ifBlank { "Disponível" }
                                )
                            )
                        }
                    }
                }

                contactsList = list
            } catch (e: Exception) {
                // Silently ignore or show cached
            } finally {
                isLoading = false
            }
        }
    }

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val readGranted = perms[Manifest.permission.READ_CONTACTS] == true
        if (readGranted) {
            pendingSyncTrigger++
        } else {
            Toast.makeText(context, "Permissão para ler contatos necessária para sincronizar a agenda.", Toast.LENGTH_SHORT).show()
        }
    }

    fun syncPhoneContacts() {
        val hasRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val hasWrite = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (!hasRead || !hasWrite) {
            contactPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
            )
            return
        }

        isSyncingPhoneContacts = true
        scope.launch {
            try {
                val (count, _) = ContactSyncHelper.syncDeviceContacts(context)
                if (count > 0) {
                    Toast.makeText(context, "$count contato(s) da agenda sincronizado(s) com sucesso!", Toast.LENGTH_SHORT).show()
                    loadContacts()
                } else {
                    Toast.makeText(context, "Nenhum novo contato encontrado na agenda.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao sincronizar contatos: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isSyncingPhoneContacts = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadContacts()
    }

    LaunchedEffect(pendingSyncTrigger) {
        if (pendingSyncTrigger > 0) {
            syncPhoneContacts()
        }
    }

    fun startChatWithUser(targetUserId: String, targetDisplayName: String, targetUsername: String = "", avatarUrl: String? = null) {
        scope.launch {
            isLoading = true
            try {
                val res = apiService.getOrCreateConversation(mapOf("targetUserId" to targetUserId))
                if (res.isSuccessful && res.body() != null) {
                    val conv = res.body()!!
                    withContext(Dispatchers.IO) {
                        db.conversationDao().insertOrUpdate(
                            ConversationEntity(
                                id = conv.conversationId,
                                otherUserId = targetUserId,
                                otherUsername = targetUsername,
                                otherDisplayName = targetDisplayName,
                                otherAvatarUrl = avatarUrl,
                                otherPublicKey = null,
                                lastMessageContent = null,
                                lastMessageTimestamp = System.currentTimeMillis()
                            )
                        )
                    }
                    onNavigateToChat(conv.conversationId, targetUserId, targetDisplayName)
                } else {
                    val err = res.errorBody()?.string() ?: "Não foi possível abrir a conversa"
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                val errorMsg = if (e is java.net.SocketTimeoutException) {
                    "Servidor iniciando... Tente novamente em instantes"
                } else {
                    "Erro: ${e.localizedMessage}"
                }
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    fun searchRemoteUsers(q: String) {
        if (q.trim().length < 2) {
            remoteSearchResults = emptyList()
            return
        }
        scope.launch {
            try {
                val res = apiService.searchUsers(q.trim())
                if (res.isSuccessful && res.body() != null) {
                    remoteSearchResults = res.body()!!
                }
            } catch (e: Exception) {}
        }
    }

    // Filter contacts based on search query
    val filteredContacts = remember(contactsList, searchQuery) {
        if (searchQuery.isBlank()) {
            contactsList
        } else {
            contactsList.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.username.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Add Contact Dialog
    if (showAddContactDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isAddingContact) showAddContactDialog = false
            },
            title = { Text("Novo contato", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Digite o @nome_de_usuario ou ID do contato:", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newContactUsername,
                        onValueChange = { newContactUsername = it },
                        placeholder = { Text("Ex: joaosilva") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newContactUsername.isNotBlank() && !isAddingContact,
                    onClick = {
                        scope.launch {
                            isAddingContact = true
                            try {
                                val searchRes = apiService.searchUsers(newContactUsername.trim().removePrefix("@"))
                                val user = searchRes.body()?.firstOrNull()
                                if (user != null) {
                                    apiService.addContact(AddContactRequest(contactUserId = user.id))
                                    Toast.makeText(context, "${user.displayName} adicionado aos contatos!", Toast.LENGTH_SHORT).show()
                                    showAddContactDialog = false
                                    newContactUsername = ""
                                    loadContacts()
                                    startChatWithUser(user.id, user.displayName, user.username, user.avatarUrl)
                                } else {
                                    Toast.makeText(context, "Usuário não encontrado", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Erro: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isAddingContact = false
                            }
                        }
                    }
                ) {
                    Text(if (isAddingContact) "Buscando..." else "Adicionar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddContactDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (contactToDelete != null) {
        val target = contactToDelete!!
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = { Text("Excluir contato?", fontWeight = FontWeight.Bold) },
            text = { Text("Deseja realmente excluir ${target.displayName} da sua lista de contatos? O contato e a conversa local serão removidos.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDel = target
                        contactToDelete = null
                        scope.launch {
                            try {
                                apiService.removeContact(toDel.userId)
                                withContext(Dispatchers.IO) {
                                    db.contactDao().deleteContact(toDel.userId)
                                    val conv = db.conversationDao().getConversationByUserId(toDel.userId)
                                    if (conv != null) {
                                        db.conversationDao().deleteConversation(conv.id)
                                        db.messageDao().clearConversationMessages(conv.id)
                                    }
                                }
                                contactsList = contactsList.filter { it.userId != toDel.userId }
                                Toast.makeText(context, "Contato excluído com sucesso", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Erro ao excluir contato", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Excluir", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // DIALOG: Editar Contato
    if (contactToEdit != null) {
        val target = contactToEdit!!
        AlertDialog(
            onDismissRequest = { contactToEdit = null },
            title = { Text("Editar Nome do Contato", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Defina como ${target.username} será exibido:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editNicknameText,
                        onValueChange = { editNicknameText = it },
                        label = { Text("Nome / Apelido") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newNick = editNicknameText.trim()
                        if (newNick.isNotBlank()) {
                            val toEdit = target
                            contactToEdit = null
                            scope.launch {
                                try {
                                    apiService.updateContact(toEdit.userId, mapOf("nickname" to newNick))
                                    withContext(Dispatchers.IO) {
                                        val conv = db.conversationDao().getConversationByUserId(toEdit.userId)
                                        if (conv != null) {
                                            db.conversationDao().insertOrUpdate(conv.copy(otherDisplayName = newNick))
                                        }
                                    }
                                    loadContacts()
                                    Toast.makeText(context, "Contato atualizado com sucesso", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Erro ao atualizar contato", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToEdit = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // DIALOG: Criar Novo Grupo
    if (showCreateGroupDialog) {
        val nonSelfContacts = contactsList.filter { !it.isSelf }
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Novo Grupo", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newGroupTitle,
                        onValueChange = { newGroupTitle = it },
                        label = { Text("Nome do Grupo") },
                        placeholder = { Text("Ex: Família, Trabalho...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Participantes selecionados (${selectedGroupMembers.size}):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (nonSelfContacts.isEmpty()) {
                        Text(
                            text = "Nenhum contato adicionado ainda. Adicione contatos para criar grupos.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                        ) {
                            items(nonSelfContacts, key = { it.userId }) { c ->
                                val isSelected = selectedGroupMembers.contains(c.userId)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedGroupMembers = if (isSelected) {
                                                selectedGroupMembers - c.userId
                                            } else {
                                                selectedGroupMembers + c.userId
                                            }
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedGroupMembers = if (checked) {
                                                selectedGroupMembers + c.userId
                                            } else {
                                                selectedGroupMembers - c.userId
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(c.displayName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                        Text("@${c.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = newGroupTitle.isNotBlank() && !isCreatingGroup,
                    onClick = {
                        val title = newGroupTitle.trim()
                        isCreatingGroup = true
                        scope.launch {
                            try {
                                val res = apiService.createGroup(
                                    CreateGroupRequest(
                                        title = title,
                                        memberIds = selectedGroupMembers.toList()
                                    )
                                )
                                if (res.isSuccessful && res.body() != null) {
                                    val dto = res.body()!!
                                    withContext(Dispatchers.IO) {
                                        db.conversationDao().insertOrUpdate(
                                            ConversationEntity(
                                                id = dto.conversationId,
                                                otherUserId = dto.conversationId,
                                                otherUsername = dto.title ?: title,
                                                otherDisplayName = dto.title ?: title,
                                                isGroup = true,
                                                groupTitle = dto.title ?: title,
                                                groupAvatar = dto.avatarUrl,
                                                lastMessageTimestamp = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                    showCreateGroupDialog = false
                                    Toast.makeText(context, "Grupo criado com sucesso!", Toast.LENGTH_SHORT).show()
                                    onNavigateToChat(dto.conversationId, dto.conversationId, dto.title ?: title)
                                } else {
                                    val err = res.errorBody()?.string() ?: "Erro ao criar grupo"
                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                val errorMsg = if (e is java.net.SocketTimeoutException) {
                                    "Servidor iniciando... Tente novamente em instantes"
                                } else {
                                    e.localizedMessage ?: "Falha ao criar grupo"
                                }
                                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                            } finally {
                                isCreatingGroup = false
                            }
                        }
                    }
                ) {
                    if (isCreatingGroup) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Criar Grupo")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                searchRemoteUsers(it)
                            },
                            placeholder = { Text("Pesquisar contatos...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                            remoteSearchResults = emptyList()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Fechar busca")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                remoteSearchResults = emptyList()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpar")
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Contatos", fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                            Text(
                                "${filteredContacts.size} contatos",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Pesquisar")
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sincronizar agenda") },
                                    leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null, tint = BluePrimary) },
                                    onClick = {
                                        showMenu = false
                                        syncPhoneContacts()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Salvar na agenda") },
                                    leadingIcon = { Icon(Icons.Default.ContactPhone, contentDescription = null, tint = Color(0xFF1565C0)) },
                                    onClick = {
                                        showMenu = false
                                        ContactSyncHelper.openAddContactInPhoneBook(context)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Atualizar lista") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        loadContacts()
                                        Toast.makeText(context, "Lista atualizada", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Convidar amigos") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        Toast.makeText(context, "Compartilhe o LivreChat com seus amigos!", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Action Hub (Shown when not searching)
            if (!isSearchActive || searchQuery.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LivreActionCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.GroupAdd,
                                    iconTint = BluePrimary,
                                    title = "Novo Grupo",
                                    subtitle = "Criar sala",
                                    onClick = {
                                        newGroupTitle = ""
                                        selectedGroupMembers = emptySet()
                                        showCreateGroupDialog = true
                                    }
                                )
                                LivreActionCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.PersonAddAlt1,
                                    iconTint = Color(0xFF8E24AA),
                                    title = "Adicionar",
                                    subtitle = "Por Username / ID",
                                    onClick = { showAddContactDialog = true }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LivreActionCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.QrCodeScanner,
                                    iconTint = Color(0xFF00897B),
                                    title = "Ler QR Code",
                                    subtitle = "Conectar rápido",
                                    onClick = onNavigateToQRCode
                                )
                                val selfContact = contactsList.firstOrNull { it.isSelf }
                                LivreActionCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.BookmarkBorder,
                                    iconTint = Color(0xFFF57C00),
                                    title = "Meu Espaço",
                                    subtitle = "Notas e Mídia",
                                    onClick = {
                                        if (selfContact != null) {
                                            startChatWithUser(selfContact.userId, selfContact.displayName, selfContact.username, null)
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LivreActionCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Sync,
                                    iconTint = Color(0xFF2E7D32),
                                    title = "Sincronizar",
                                    subtitle = if (isSyncingPhoneContacts) "Puxando contatos..." else "Agenda do Celular",
                                    onClick = { syncPhoneContacts() }
                                )
                                LivreActionCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.ContactPhone,
                                    iconTint = Color(0xFF1565C0),
                                    title = "Nova Agenda",
                                    subtitle = "Salvar no Celular",
                                    onClick = { ContactSyncHelper.openAddContactInPhoneBook(context) }
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Meus Contatos",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val otherCount = filteredContacts.count { !it.isSelf }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BluePrimary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "$otherCount salvo(s)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BluePrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            val contactsToRender = if (isSearchActive && searchQuery.isNotEmpty()) filteredContacts else filteredContacts.filter { !it.isSelf }

            if (contactsToRender.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PersonSearch,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Nenhum contato encontrado para \"$searchQuery\"" else "Nenhum contato salvo ainda",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Tente buscar pelo username exato ou ID" else "Use os botões acima para adicionar amigos pelo ID ou QR Code",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                // Contact List
                items(contactsToRender, key = { it.userId }) { contact ->
                    ContactRowItem(
                        contact = contact,
                        onClick = {
                            startChatWithUser(
                                targetUserId = contact.userId,
                                targetDisplayName = contact.displayName,
                                targetUsername = contact.username,
                                avatarUrl = contact.avatarUrl
                            )
                        },
                        onEditClick = if (!contact.isSelf) {
                            {
                                contactToEdit = contact
                                editNicknameText = contact.displayName
                            }
                        } else null,
                        onDeleteClick = if (!contact.isSelf) { { contactToDelete = contact } } else null
                    )
                }
            }

            // Additional Search Results from Server (if actively searching)
            if (isSearchActive && searchQuery.length >= 2 && remoteSearchResults.isNotEmpty()) {
                val nonLocalResults = remoteSearchResults.filter { r ->
                    filteredContacts.none { it.userId == r.id }
                }
                if (nonLocalResults.isNotEmpty()) {
                    item {
                        Text(
                            text = "Outros usuários encontrados no LivreChat",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BluePrimary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                    items(nonLocalResults, key = { it.id }) { user ->
                        ContactRowItem(
                            contact = ContactUiModel(
                                userId = user.id,
                                displayName = user.displayName,
                                username = user.username,
                                avatarUrl = user.avatarUrl,
                                bio = "@${user.username}"
                            ),
                            onClick = {
                                startChatWithUser(user.id, user.displayName, user.username, user.avatarUrl)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LivreActionCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconTint.copy(alpha = 0.14f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                Text(text = subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
fun ContactRowItem(
    contact: ContactUiModel,
    onClick: () -> Unit,
    onEditClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Modern LivreChat Avatar
            UserAvatarView(
                avatarUrl = contact.avatarUrl,
                displayName = contact.displayName,
                size = 46.dp,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (contact.isSelf) "${contact.displayName} (Você)" else contact.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.bio.ifBlank { "@${contact.username}" },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Quick Chat Button
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = "Conversar",
                    tint = BluePrimary,
                    modifier = Modifier.size(19.dp)
                )
            }

            if (!contact.isSelf && onEditClick != null) {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar contato",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (!contact.isSelf && onDeleteClick != null) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Excluir contato",
                        tint = DangerRed.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
