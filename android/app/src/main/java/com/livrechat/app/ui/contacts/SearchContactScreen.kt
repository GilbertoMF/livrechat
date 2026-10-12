package com.livrechat.app.ui.contacts

import android.widget.Toast
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
import coil.compose.AsyncImage
import com.livrechat.app.LivreChatApp
import com.livrechat.app.data.local.entity.ConversationEntity
import com.livrechat.app.data.remote.model.AddContactRequest
import com.livrechat.app.data.remote.model.UserDto
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

    fun loadContacts() {
        scope.launch {
            isLoading = true
            try {
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

    LaunchedEffect(Unit) {
        loadContacts()
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
                    Toast.makeText(context, "Não foi possível abrir a conversa", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
                                    mapOf(
                                        "title" to title,
                                        "memberIds" to selectedGroupMembers.toList()
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
                                    onNavigateToChat(dto.conversationId, dto.title ?: title, dto.title ?: title)
                                } else {
                                    Toast.makeText(context, "Erro ao criar grupo", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Falha de conexão ao criar grupo", Toast.LENGTH_SHORT).show()
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
                                    text = { Text("Atualizar") },
                                    onClick = {
                                        showMenu = false
                                        loadContacts()
                                        Toast.makeText(context, "Lista atualizada", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Convidar amigos") },
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
            // Action Tiles (Shown when not searching)
            if (!isSearchActive || searchQuery.isEmpty()) {
                item {
                    // 1. Novo grupo
                    ActionContactTile(
                        icon = Icons.Default.GroupAdd,
                        title = "Novo grupo",
                        onClick = {
                            newGroupTitle = ""
                            selectedGroupMembers = emptySet()
                            showCreateGroupDialog = true
                        }
                    )
                }

                item {
                    // 2. Novo contato (com botão QR Code)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAddContactDialog = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF25D366),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Novo contato",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        }

                        IconButton(onClick = onNavigateToQRCode) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "Escanear QR Code",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    // 3. Nova comunidade
                    ActionContactTile(
                        icon = Icons.Default.Groups,
                        title = "Nova comunidade",
                        onClick = {
                            Toast.makeText(context, "Comunidades em desenvolvimento", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Contatos no LivreChat",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Contact List
            items(filteredContacts, key = { it.userId }) { contact ->
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

            // Additional Search Results from Server (if actively searching)
            if (isSearchActive && searchQuery.length >= 2 && remoteSearchResults.isNotEmpty()) {
                val nonLocalResults = remoteSearchResults.filter { r ->
                    filteredContacts.none { it.userId == r.id }
                }
                if (nonLocalResults.isNotEmpty()) {
                    item {
                        Text(
                            text = "Outros usuários encontrados",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
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
fun ActionContactTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF25D366),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
    }
}

@Composable
fun ContactRowItem(
    contact: ContactUiModel,
    onClick: () -> Unit,
    onEditClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Surface(
            shape = CircleShape,
            color = if (contact.isSelf) Color(0xFF25D366).copy(alpha = 0.2f) else BluePrimary.copy(alpha = 0.15f),
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!contact.avatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = contact.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Text(
                        text = contact.displayName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (contact.isSelf) Color(0xFF25D366) else BluePrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (contact.isSelf) "${contact.displayName} (Você)" else contact.displayName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = contact.bio.ifBlank { "@${contact.username}" },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!contact.isSelf && onEditClick != null) {
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Editar contato",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (!contact.isSelf && onDeleteClick != null) {
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Excluir contato",
                    tint = DangerRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
