package com.livrechat.app.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.livrechat.app.BuildConfig
import com.livrechat.app.LivreChatApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UpdateInfo(
    val isUpdateAvailable: Boolean = false,
    val isMandatory: Boolean = false,
    val latestVersion: String = "",
    val latestVersionCode: Int = 0,
    val minSupportedVersionCode: Int = 0,
    val title: String = "",
    val message: String = "",
    val apkArm64Url: String = "",
    val apkArmv7Url: String = "",
    val apkUniversalUrl: String = "",
    val downloadPortalUrl: String = ""
)

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _updateState = MutableStateFlow<UpdateInfo?>(null)
    val updateState: StateFlow<UpdateInfo?> = _updateState.asStateFlow()

    fun checkUpdate() {
        scope.launch {
            try {
                val api = LivreChatApp.instance.apiService
                val res = api.checkVersion(BuildConfig.VERSION_CODE)
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    val isAvail = body.latestVersionCode > BuildConfig.VERSION_CODE
                    val isMandatory = body.minSupportedVersionCode > BuildConfig.VERSION_CODE

                    if (isAvail || isMandatory) {
                        _updateState.value = UpdateInfo(
                            isUpdateAvailable = true,
                            isMandatory = isMandatory,
                            latestVersion = body.latestVersion,
                            latestVersionCode = body.latestVersionCode,
                            minSupportedVersionCode = body.minSupportedVersionCode,
                            title = if (isMandatory) "Atualização Obrigatória" else "Atualização Disponível",
                            message = if (body.message.isNotBlank()) body.message else "Versões diferentes não podem se comunicar por segurança. Atualize para a versão mais recente.",
                            apkArm64Url = body.apkArm64Url,
                            apkArmv7Url = body.apkArmv7Url,
                            apkUniversalUrl = body.apkUniversalUrl,
                            downloadPortalUrl = body.downloadPortalUrl
                        )
                        Log.d(TAG, "Atualização encontrada: v${body.latestVersion} (Obrigatória: $isMandatory)")
                    } else {
                        _updateState.value = null
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao verificar atualizações: ${e.message}")
            }
        }
    }

    fun setUpdateFromWebSocket(
        isAvailable: Boolean,
        isMandatory: Boolean,
        latestVersion: String,
        latestVersionCode: Int,
        downloadUrl: String,
        message: String
    ) {
        if (isAvailable || isMandatory) {
            _updateState.value = UpdateInfo(
                isUpdateAvailable = true,
                isMandatory = isMandatory,
                latestVersion = latestVersion,
                latestVersionCode = latestVersionCode,
                title = if (isMandatory) "Atualização Obrigatória" else "Atualização Disponível",
                message = message,
                downloadPortalUrl = downloadUrl
            )
        }
    }

    fun dismiss() {
        if (_updateState.value?.isMandatory != true) {
            _updateState.value = null
        }
    }

    fun startUpdate(context: Context, updateInfo: UpdateInfo) {
        try {
            var baseUrl = LivreChatApp.instance.preferencesManager.serverUrl.trim()
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length - 1)
            }

            // Pick the best APK for this phone's architecture
            val abis = Build.SUPPORTED_ABIS ?: emptyArray()
            val specificApkUrl = when {
                abis.contains("arm64-v8a") && updateInfo.apkArm64Url.isNotBlank() -> updateInfo.apkArm64Url
                abis.contains("armeabi-v7a") && updateInfo.apkArmv7Url.isNotBlank() -> updateInfo.apkArmv7Url
                updateInfo.apkUniversalUrl.isNotBlank() -> updateInfo.apkUniversalUrl
                else -> "/download"
            }

            val fullUrl = if (specificApkUrl.startsWith("http://") || specificApkUrl.startsWith("https://")) {
                specificApkUrl
            } else {
                "$baseUrl$specificApkUrl"
            }

            Log.d(TAG, "Iniciando download da atualização: $fullUrl")

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir link de atualização", e)
        }
    }
}
