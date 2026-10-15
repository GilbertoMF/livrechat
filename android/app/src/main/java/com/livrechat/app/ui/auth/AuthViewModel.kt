package com.livrechat.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livrechat.app.LivreChatApp
import com.livrechat.app.data.local.entity.UserEntity
import com.livrechat.app.data.remote.model.LoginRequest
import com.livrechat.app.data.remote.model.RecoverRequest
import com.livrechat.app.data.remote.model.RegisterRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class RegisterSuccess(val user: UserEntity, val recoveryKey: String) : AuthUiState()
    data class LoginSuccess(val user: UserEntity) : AuthUiState()
    data class VirtualNumberCodeSent(val virtualNumber: String, val code: String) : AuthUiState()
    data class RecoverySuccess(val message: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel : ViewModel() {
    private val app = LivreChatApp.instance
    private val apiService = app.apiClient.apiService
    private val prefs = app.preferencesManager
    private val userDao = app.database.userDao()

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun register(username: String, password: String, displayName: String) {
        if (username.isBlank() || password.isBlank() || displayName.isBlank()) {
            _uiState.value = AuthUiState.Error("Preencha todos os campos obrigatórios.")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = apiService.register(
                    RegisterRequest(
                        username = username.trim().lowercase(),
                        password = password,
                        displayName = displayName.trim()
                    )
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!

                    val prevUserId = prefs.currentUserId
                    if (prevUserId != null && prevUserId != body.user.id) {
                        try {
                            app.database.clearAllTables()
                        } catch (e: Exception) {}
                    }

                    // Save auth tokens
                    prefs.accessToken = body.tokens.accessToken
                    prefs.refreshToken = body.tokens.refreshToken
                    prefs.currentUserId = body.user.id
                    prefs.currentUsername = body.user.username
                    prefs.currentDisplayName = body.user.displayName
                    prefs.userAvatarUrl = body.user.avatarUrl
                    prefs.userBio = "Disponível no LivreChat"

                    try {
                        java.io.File(app.filesDir, "avatar_current.jpg").delete()
                        app.filesDir.listFiles()?.forEach { f ->
                            if (f.name.startsWith("avatar_")) {
                                f.delete()
                            }
                        }
                    } catch (e: Exception) {}

                    // Save in local Room database
                    val userEntity = UserEntity(
                        id = body.user.id,
                        username = body.user.username,
                        displayName = body.user.displayName,
                        avatarUrl = body.user.avatarUrl,
                        publicKey = body.user.publicKey,
                        recoveryKey = body.recoveryKey,
                        isCurrentUser = true
                    )
                    userDao.insertOrUpdate(userEntity)

                    app.connectionManager.connect()

                    _uiState.value = AuthUiState.RegisterSuccess(userEntity, body.recoveryKey)
                } else {
                    val err = response.errorBody()?.string() ?: "Erro ao registrar usuário."
                    _uiState.value = AuthUiState.Error(err)
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Erro de conexão: ${e.localizedMessage}")
            }
        }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Digite o nome de usuário e a senha.")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = apiService.login(
                    LoginRequest(
                        username = username.trim().lowercase(),
                        password = password
                    )
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!

                    val prevUserId = prefs.currentUserId
                    if (prevUserId != null && prevUserId != body.user.id) {
                        try {
                            app.database.clearAllTables()
                        } catch (e: Exception) {}
                    }

                    prefs.accessToken = body.tokens.accessToken
                    prefs.refreshToken = body.tokens.refreshToken
                    prefs.currentUserId = body.user.id
                    prefs.currentUsername = body.user.username
                    prefs.currentDisplayName = body.user.displayName
                    prefs.userAvatarUrl = body.user.avatarUrl

                    if (body.user.avatarUrl.isNullOrBlank()) {
                        try {
                            java.io.File(app.filesDir, "avatar_current.jpg").delete()
                            java.io.File(app.filesDir, "avatar_${body.user.id}.jpg").delete()
                        } catch (e: Exception) {}
                    }

                    val userEntity = UserEntity(
                        id = body.user.id,
                        username = body.user.username,
                        displayName = body.user.displayName,
                        avatarUrl = body.user.avatarUrl,
                        publicKey = body.user.publicKey,
                        isCurrentUser = true
                    )
                    userDao.insertOrUpdate(userEntity)

                    app.connectionManager.connect()

                    _uiState.value = AuthUiState.LoginSuccess(userEntity)
                } else {
                    val err = response.errorBody()?.string() ?: "Credenciais incorretas."
                    _uiState.value = AuthUiState.Error(err)
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Erro ao conectar ao servidor: ${e.localizedMessage}")
            }
        }
    }

    fun recoverAccount(username: String, recoveryKey: String, newPassword: String) {
        if (username.isBlank() || recoveryKey.isBlank() || newPassword.isBlank()) {
            _uiState.value = AuthUiState.Error("Preencha todos os campos da recuperação.")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = apiService.recover(
                    RecoverRequest(
                        username = username.trim().lowercase(),
                        recoveryKey = recoveryKey.trim(),
                        newPassword = newPassword
                    )
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    _uiState.value = AuthUiState.RecoverySuccess(
                        response.body()?.message ?: "Senha redefinida com sucesso! Faça login."
                    )
                } else {
                    _uiState.value = AuthUiState.Error(response.body()?.error ?: "Chave de recuperação incorreta.")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Falha na recuperação: ${e.localizedMessage}")
            }
        }
    }

    fun setServerUrl(url: String) {
        prefs.serverUrl = url
    }

    fun requestVirtualNumberCode(virtualNumber: String, onCodeGenerated: ((String) -> Unit)? = null) {
        val cleanNumber = virtualNumber.replace(Regex("\\D"), "")
        if (cleanNumber.length != 8) {
            _uiState.value = AuthUiState.Error("O número virtual deve conter exatamente 8 dígitos.")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val res = apiService.requestVirtualNumberCode(mapOf("virtualNumber" to cleanNumber))
                if (res.isSuccessful && res.body() != null) {
                    val code = res.body()!!.code
                    _uiState.value = AuthUiState.VirtualNumberCodeSent(cleanNumber, code)
                    onCodeGenerated?.invoke(code)
                } else {
                    val err = res.errorBody()?.string() ?: "Erro ao gerar código SMS para o número virtual."
                    _uiState.value = AuthUiState.Error(err)
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Erro de conexão: ${e.localizedMessage}")
            }
        }
    }

    fun verifyVirtualNumberCode(virtualNumber: String, code: String) {
        val cleanNumber = virtualNumber.replace(Regex("\\D"), "")
        val cleanCode = code.trim()
        if (cleanNumber.length != 8) {
            _uiState.value = AuthUiState.Error("O número virtual deve ter 8 dígitos.")
            return
        }
        if (cleanCode.length != 4) {
            _uiState.value = AuthUiState.Error("O código SMS deve conter 4 dígitos.")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val res = apiService.verifyVirtualNumberCode(
                    mapOf(
                        "virtualNumber" to cleanNumber,
                        "code" to cleanCode,
                        "deviceName" to "Android ${android.os.Build.MODEL}"
                    )
                )
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!

                    val prevUserId = prefs.currentUserId
                    if (prevUserId != null && prevUserId != body.user.id) {
                        try {
                            app.database.clearAllTables()
                        } catch (e: Exception) {}
                    }

                    prefs.accessToken = body.tokens.accessToken
                    prefs.refreshToken = body.tokens.refreshToken
                    prefs.currentUserId = body.user.id
                    prefs.currentUsername = body.user.username
                    prefs.currentDisplayName = body.user.displayName
                    prefs.userAvatarUrl = body.user.avatarUrl

                    if (body.user.avatarUrl.isNullOrBlank()) {
                        try {
                            java.io.File(app.filesDir, "avatar_current.jpg").delete()
                            java.io.File(app.filesDir, "avatar_${body.user.id}.jpg").delete()
                        } catch (e: Exception) {}
                    }

                    val userEntity = UserEntity(
                        id = body.user.id,
                        username = body.user.username,
                        displayName = body.user.displayName,
                        avatarUrl = body.user.avatarUrl,
                        publicKey = body.user.publicKey,
                        isCurrentUser = true
                    )
                    userDao.insertOrUpdate(userEntity)

                    app.connectionManager.connect()

                    _uiState.value = AuthUiState.LoginSuccess(userEntity)
                } else {
                    val err = res.errorBody()?.string() ?: "Código de verificação incorreto."
                    _uiState.value = AuthUiState.Error(err)
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Erro ao verificar código: ${e.localizedMessage}")
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
