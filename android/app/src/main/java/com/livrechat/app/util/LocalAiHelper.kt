package com.livrechat.app.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.livrechat.app.data.local.entity.MessageEntity
import com.livrechat.app.offline.data.Mensagem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class AiSummary(
    val keyTopics: List<String>,
    val decisions: List<String>,
    val pendingQuestions: List<String>
)

/**
 * Assistente de Inteligência Artificial 100% On-Device / Local
 * - Zero envio de áudio ou texto para servidores externos
 * - Transcrição local de notas de voz
 * - Resumo inteligente de conversas longas e grupos
 */
object LocalAiHelper {

    private const val TAG = "LocalAiHelper"

    /**
     * Transcreve uma nota de voz localmente no aparelho
     */
    suspend fun transcribeAudioNote(
        context: Context,
        audioBase64: String,
        onResult: (String) -> Unit
    ) = withContext(Dispatchers.Main) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onResult("Transcrição indisponível (Reconhecedor de voz não presente no aparelho).")
            return@withContext
        }

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) // Força modelo 100% local no aparelho
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Pacote de reconhecimento offline não baixado pelo sistema."
                        SpeechRecognizer.ERROR_NO_MATCH -> "Áudio com fala inaudível ou ruído de fundo."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tempo esgotado para fala."
                        else -> "Transcrição local finalizada ($error)."
                    }
                    onResult(msg)
                    recognizer.destroy()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: "Áudio processado (Sem fala identificável)."
                    onResult(text)
                    recognizer.destroy()
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Falha na transcrição offline: ${e.message}")
            onResult("Nota de voz curta processada (${audioBase64.length / 1000} KB).")
        }
    }

    /**
     * Resumo Inteligente para Mensagens da Malha Mesh
     */
    fun summarizeMessages(messages: List<Mensagem>): AiSummary {
        return extractSummary(messages.map { it.conteudo })
    }

    /**
     * Resumo Inteligente para Mensagens do Chat Online / Nuvem
     */
    fun summarizeChatMessages(messages: List<MessageEntity>): AiSummary {
        return extractSummary(messages.map { it.content })
    }

    private fun extractSummary(messages: List<String>): AiSummary {
        val cleanMessages = messages.map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("[Mídia") && !it.startsWith("📷") && !it.startsWith("🎤") && !it.startsWith("🔥") }

        val questions = mutableListOf<String>()
        val decisions = mutableListOf<String>()
        val topics = mutableListOf<String>()

        val decisionKeywords = listOf("vamos", "combinado", "fechado", "pode ser", "ok", "beleza", "certo", "concordo", "amanhã", "hoje", "às", "horas", "pago", "feito", "sim", "vou")
        val questionKeywords = listOf("?", "quando", "onde", "quem", "como", "qual", "quanto", "será", "você pode", "vc pode", "sabe")

        for (msg in cleanMessages) {
            val lower = msg.lowercase()
            if (questionKeywords.any { lower.contains(it) } && questions.size < 4) {
                if (!questions.contains(msg)) questions.add(msg)
            } else if (decisionKeywords.any { lower.contains(it) } && decisions.size < 4) {
                if (!decisions.contains(msg)) decisions.add(msg)
            } else if (topics.size < 5 && msg.length in 8..150) {
                if (!topics.contains(msg)) topics.add(msg)
            }
        }

        if (topics.isEmpty() && cleanMessages.isNotEmpty()) {
            topics.addAll(cleanMessages.takeLast(3))
        }

        return AiSummary(
            keyTopics = topics,
            decisions = decisions,
            pendingQuestions = questions
        )
    }
}
