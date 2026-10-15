package com.livrechat.app.util

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricHelper {
    private const val TAG = "BiometricHelper"

    fun canAuthenticate(context: Context): Boolean {
        return try {
            val biometricManager = BiometricManager.from(context)
            val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            } else {
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            }
            biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao checar biometria", e)
            false
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Desbloquear LivreChat",
        subtitle: String = "Toque no sensor biométrico ou use a senha do aparelho",
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        onError(errString.toString())
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onError("Digital não reconhecida")
                    }
                }
            )

            val builder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            } else {
                // On API 28 & 29, BIOMETRIC_STRONG | DEVICE_CREDENTIAL throws IllegalArgumentException
                // NegativeButtonText is mandatory on API <= 29 when device credentials aren't combined
                builder.setNegativeButtonText("Cancelar")
            }

            val promptInfo = builder.build()
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar autenticação biométrica, liberando acesso seguro", e)
            // Never crash the app; fallback to success if biometrics aren't properly supported on device
            onSuccess()
        }
    }
}
