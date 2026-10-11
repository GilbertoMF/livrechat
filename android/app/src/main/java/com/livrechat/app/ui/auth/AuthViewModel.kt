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

                    // Save auth tokens
                    prefs.accessToken = body.tokens.accessToken
                    prefs.refreshToken = body.tokens.refreshToken
                    prefs.currentUserId = body.user.id
                    prefs.currentUsername = body.user.username
                    prefs.currentDisplayName = body.user.displayName

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
                    com.livrechat.app.service.LivreChatBackgroundService.start(app)

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

                    prefs.accessToken = body.tokens.accessToken
                    prefs.refreshToken = body.tokens.refreshToken
                    prefs.currentUserId = body.user.id
                    prefs.currentUsername = body.user.username
                    prefs.currentDisplayName = body.user.displayName

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
                    com.livrechat.app.service.LivreChatBackgroundService.start(app)

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

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
