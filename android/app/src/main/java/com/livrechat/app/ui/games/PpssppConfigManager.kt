package com.livrechat.app.ui.games

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

object PpssppConfigManager {

    const val PPSSPP_PACKAGE_FREE = "org.ppsspp.ppsspp"
    const val PPSSPP_PACKAGE_GOLD = "org.ppsspp.ppssppgold"
    const val DEFAULT_ORACLE_SERVER = "164.152.40.160"
    const val DEFAULT_PORT_OFFSET = 10000

    /**
     * Checks if PPSSPP is installed on the device (Free or Gold edition)
     */
    fun isPpssppInstalled(context: Context): Boolean {
        return getInstalledPpssppPackage(context) != null
    }

    /**
     * Returns the package name of installed PPSSPP or null
     */
    fun getInstalledPpssppPackage(context: Context): String? {
        val pm = context.packageManager
        // Check Free version first
        try {
            pm.getPackageInfo(PPSSPP_PACKAGE_FREE, 0)
            return PPSSPP_PACKAGE_FREE
        } catch (_: PackageManager.NameNotFoundException) {}

        // Check Gold version
        try {
            pm.getPackageInfo(PPSSPP_PACKAGE_GOLD, 0)
            return PPSSPP_PACKAGE_GOLD
        } catch (_: PackageManager.NameNotFoundException) {}

        return null
    }

    /**
     * Injects or updates network multiplayer settings in ppsspp.ini:
     * [Network]
     * EnableNetworking = True
     * UseTrueWLAN = False
     * ProAdhocServer = 164.152.40.160
     * PortOffset = 10000
     * EnableAdhocServer = False
     */
    fun injectMultiplayerConfig(context: Context, serverIp: String = DEFAULT_ORACLE_SERVER, portOffset: Int = DEFAULT_PORT_OFFSET) {
        try {
            val possibleIniFiles = mutableListOf<File>()

            // 1. Standard /sdcard/PSP/SYSTEM/ppsspp.ini
            val primaryPspDir = File(Environment.getExternalStorageDirectory(), "PSP/SYSTEM")
            if (primaryPspDir.exists() || primaryPspDir.mkdirs()) {
                possibleIniFiles.add(File(primaryPspDir, "ppsspp.ini"))
            }

            // 2. App-specific external storage fallback
            val appExternalPsp = File(context.getExternalFilesDir(null), "PSP/SYSTEM")
            if (appExternalPsp.exists() || appExternalPsp.mkdirs()) {
                possibleIniFiles.add(File(appExternalPsp, "ppsspp.ini"))
            }

            for (iniFile in possibleIniFiles) {
                applyConfigToIniFile(iniFile, serverIp, portOffset)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyConfigToIniFile(file: File, serverIp: String, portOffset: Int) {
        val existingContent = if (file.exists()) file.readText() else ""

        val networkBlock = """
[Network]
EnableNetworking = True
UseTrueWLAN = False
ProAdhocServer = $serverIp
PortOffset = $portOffset
EnableAdhocServer = False
        """.trimIndent()

        if (existingContent.contains("[Network]")) {
            // Replace existing [Network] block
            val regex = Regex("""\[Network\][\s\S]*?(?=\n\[|\z)""")
            val updated = existingContent.replace(regex, networkBlock)
            file.writeText(updated)
        } else {
            // Append [Network] block
            val updated = (existingContent.trimEnd() + "\n\n" + networkBlock + "\n")
            file.writeText(updated)
        }
    }

    /**
     * Launches the game directly in PPSSPP
     */
    fun launchGame(context: Context, gameFile: File): Boolean {
        val ppssppPkg = getInstalledPpssppPackage(context)

        // Try launching via FileProvider Intent
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, gameFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/octet-stream")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (ppssppPkg != null) {
                    setPackage(ppssppPkg)
                }
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback: Launch PPSSPP main activity if installed
        if (ppssppPkg != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(ppssppPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
        }

        return false
    }

    /**
     * Opens Play Store to install PPSSPP
     */
    fun openPlayStore(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PPSSPP_PACKAGE_FREE")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PPSSPP_PACKAGE_FREE")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    /**
     * Opens direct official APK download for PPSSPP
     */
    fun openDirectApkDownload(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.ppsspp.org/files/1_18_1/ppsspp.apk")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
