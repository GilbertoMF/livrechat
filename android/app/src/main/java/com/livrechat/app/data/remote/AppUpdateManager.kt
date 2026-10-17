package com.livrechat.app.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.livrechat.app.BuildConfig
import com.livrechat.app.LivreChatApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

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

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(
        val progress: Float, // 0.0 to 1.0 or -1f if unknown
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _updateState = MutableStateFlow<UpdateInfo?>(null)
    val updateState: StateFlow<UpdateInfo?> = _updateState.asStateFlow()

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

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
            // Refresh via REST API to ensure we have architecture-specific APK links
            checkUpdate()
        }
    }

    fun dismiss() {
        if (_updateState.value?.isMandatory != true) {
            cancelDownload()
            _updateState.value = null
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _downloadState.value = UpdateDownloadState.Idle
    }

    fun getTargetApkUrl(updateInfo: UpdateInfo): String {
        val abis = Build.SUPPORTED_ABIS ?: emptyArray()
        val specificApkUrl = when {
            abis.contains("arm64-v8a") && updateInfo.apkArm64Url.isNotBlank() -> updateInfo.apkArm64Url
            abis.contains("armeabi-v7a") && updateInfo.apkArmv7Url.isNotBlank() -> updateInfo.apkArmv7Url
            updateInfo.apkUniversalUrl.isNotBlank() -> updateInfo.apkUniversalUrl
            updateInfo.downloadPortalUrl.isNotBlank() -> updateInfo.downloadPortalUrl
            else -> "/download"
        }

        var baseUrl = LivreChatApp.instance.preferencesManager.serverUrl.trim()
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length - 1)
        }

        return if (specificApkUrl.startsWith("http://") || specificApkUrl.startsWith("https://")) {
            specificApkUrl
        } else {
            "$baseUrl$specificApkUrl"
        }
    }

    fun startUpdate(context: Context, updateInfo: UpdateInfo) {
        startInAppUpdate(context, updateInfo)
    }

    fun startInAppUpdate(context: Context, updateInfo: UpdateInfo) {
        val fullUrl = getTargetApkUrl(updateInfo)
        if (!fullUrl.endsWith(".apk")) {
            // Not a direct APK download link, open portal in browser
            startBrowserUpdate(context, updateInfo)
            return
        }

        downloadJob?.cancel()
        downloadJob = scope.launch {
            try {
                _downloadState.value = UpdateDownloadState.Downloading(0f, 0L, 0L)
                val updatesDir = context.getExternalFilesDir("updates") ?: File(context.filesDir, "updates")
                if (!updatesDir.exists()) {
                    updatesDir.mkdirs()
                }

                val apkFile = File(updatesDir, "livrechat_v${updateInfo.latestVersion}.apk")
                val tempFile = File(updatesDir, "livrechat_v${updateInfo.latestVersion}.apk.tmp")

                Log.d(TAG, "Iniciando download in-app: $fullUrl -> ${apkFile.absolutePath}")

                val request = Request.Builder()
                    .url(fullUrl)
                    .header("User-Agent", "LivreChat-Android/${BuildConfig.VERSION_NAME}")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful || response.body == null) {
                    throw IOException("HTTP ${response.code}: Falha ao baixar o arquivo.")
                }

                val body = response.body!!
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                tempFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(32768)
                        var bytes: Int
                        var lastUiUpdate = 0L
                        while (input.read(buffer).also { bytes = it } != -1) {
                            output.write(buffer, 0, bytes)
                            downloadedBytes += bytes
                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate > 150 || downloadedBytes == totalBytes) {
                                lastUiUpdate = now
                                val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else -1f
                                _downloadState.value = UpdateDownloadState.Downloading(progress, downloadedBytes, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }

                if (apkFile.exists()) {
                    apkFile.delete()
                }
                if (!tempFile.renameTo(apkFile)) {
                    tempFile.copyTo(apkFile, overwrite = true)
                    tempFile.delete()
                }

                Log.d(TAG, "Download in-app concluído com sucesso: ${apkFile.length()} bytes")
                _downloadState.value = UpdateDownloadState.ReadyToInstall(apkFile)

                // Launch install automatically
                Handler(Looper.getMainLooper()).post {
                    installApk(context, apkFile)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Download de atualização cancelado")
                _downloadState.value = UpdateDownloadState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Erro no download in-app", e)
                _downloadState.value = UpdateDownloadState.Error(e.message ?: "Falha ao baixar o arquivo de atualização.")
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Log.e(TAG, "Arquivo APK não encontrado: ${apkFile.absolutePath}")
            _downloadState.value = UpdateDownloadState.Error("Arquivo de instalação não encontrado.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "Permita a instalação de fontes desconhecidas para atualizar",
                        Toast.LENGTH_LONG
                    ).show()
                }
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao abrir tela de permissão de instalação", e)
                }
                return
            }
        }

        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao disparar instalador de pacote", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "Não foi possível abrir o instalador do aplicativo: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun startBrowserUpdate(context: Context, updateInfo: UpdateInfo) {
        try {
            val fullUrl = getTargetApkUrl(updateInfo)
            Log.d(TAG, "Abrindo link no navegador: $fullUrl")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir link no navegador", e)
        }
    }
}
