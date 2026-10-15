package com.livrechat.app.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.google.gson.JsonObject
import com.livrechat.app.util.ProximitySensorHelper
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

enum class CallState {
    IDLE,
    CALLING,
    RINGING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ENDED
}

interface WebRTCCallback {
    fun onCallStateChanged(state: CallState)
    fun onLocalDescriptionCreated(sdp: SessionDescription)
    fun onIceCandidateGenerated(candidate: IceCandidate)
    fun onError(message: String)
}

class WebRTCManager(
    private val context: Context,
    private val callback: WebRTCCallback
) {
    private val TAG = "LivreChat_WebRTC"

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    private var javaAudioDeviceModule: JavaAudioDeviceModule? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val proximityHelper = ProximitySensorHelper(context)
    private var isSpeakerOn = false
    private var isMicMuted = false

    private val iceServers = mutableListOf<PeerConnection.IceServer>()
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var isRemoteDescriptionSet = false

    init {
        initWebRTC()
        proximityHelper.onProximityChanged = { isNear ->
            // In earpiece mode, proximity sensor solely toggles screen-off wake lock.
            // We intentionally do not invoke updateAudioRouting() mid-call to prevent
            // Android audio HAL reinitializations that degrade microphone gain.
        }
    }

    private fun initWebRTC() {
        try {
            val options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(options)

            val adm = JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
                .setUseHardwareNoiseSuppressor(JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .createAudioDeviceModule()
            javaAudioDeviceModule = adm

            val eglBase = EglBase.create()
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
                .createPeerConnectionFactory()

            // Robust public STUN servers for NAT traversal
            iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
            iceServers.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer())
            iceServers.add(PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer())
            iceServers.add(PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer())
            iceServers.add(PeerConnection.IceServer.builder("stun:global.stun.twilio.com:3478").createIceServer())
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar WebRTC factory: ${e.message}", e)
        }
    }

    fun setIceServers(servers: List<PeerConnection.IceServer>) {
        iceServers.clear()
        iceServers.addAll(servers)
        if (iceServers.isEmpty()) {
            iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
        }
    }

    private fun cleanupCurrentCall() {
        try {
            isRemoteDescriptionSet = false
            synchronized(pendingIceCandidates) {
                pendingIceCandidates.clear()
            }

            localAudioTrack?.setEnabled(false)
            try {
                localAudioTrack?.dispose()
            } catch (e: Exception) {
                Log.w(TAG, "Aviso ao descartar localAudioTrack: ${e.message}")
            }
            localAudioTrack = null

            try {
                localAudioSource?.dispose()
            } catch (e: Exception) {
                Log.w(TAG, "Aviso ao descartar localAudioSource: ${e.message}")
            }
            localAudioSource = null

            try {
                peerConnection?.close()
                peerConnection?.dispose()
            } catch (e: Exception) {
                Log.w(TAG, "Aviso ao descartar peerConnection: ${e.message}")
            }
            peerConnection = null
            remoteAudioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Erro geral ao limpar chamada atual", e)
        }
    }

    private fun createAudioTrack() {
        try {
            val useHwAec = JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported()
            val useHwNs = JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()

            val audioConstraints = MediaConstraints().apply {
                // If hardware AEC is present, disable WebRTC software AEC to prevent double cancellation
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", if (useHwAec) "false" else "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation2", if (useHwAec) "false" else "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", if (useHwNs) "false" else "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression2", if (useHwNs) "false" else "true"))

                // Enable Auto Gain Control and High Pass Filter for vocal clarity
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl2", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "false"))
                mandatory.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
            }

            localAudioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
            localAudioTrack = peerConnectionFactory?.createAudioTrack("LIVRE_AUDIO_TRACK_${System.currentTimeMillis()}", localAudioSource)
            localAudioTrack?.setEnabled(true)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar track de áudio", e)
        }
    }

    fun startCall() {
        cleanupCurrentCall()
        callback.onCallStateChanged(CallState.CALLING)
        setupPeerConnection()
        createOffer()
    }

    fun answerCall(remoteSdp: String) {
        cleanupCurrentCall()
        callback.onCallStateChanged(CallState.CONNECTING)
        setupPeerConnection()
        setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, remoteSdp)) {
            createAnswer()
        }
    }

    fun onRemoteAnswerReceived(remoteSdp: String) {
        setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, remoteSdp))
    }

    private fun setupPeerConnection() {
        enableVoIPAudioMode()
        createAudioTrack()

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            keyType = PeerConnection.KeyType.ECDSA
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                callback.onIceCandidateGenerated(candidate)
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                Log.d(TAG, "ICE Connection State: $newState")
                when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        enableVoIPAudioMode()
                        callback.onCallStateChanged(CallState.CONNECTED)
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        callback.onCallStateChanged(CallState.RECONNECTING)
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        Log.w(TAG, "ICE Connection Failed, tentando restart...")
                        try {
                            peerConnection?.restartIce()
                            callback.onCallStateChanged(CallState.RECONNECTING)
                        } catch (e: Exception) {
                            Log.e(TAG, "Falha ao reiniciar ICE", e)
                        }
                    }
                    PeerConnection.IceConnectionState.CLOSED -> {
                        callback.onCallStateChanged(CallState.ENDED)
                        resetAudioMode()
                    }
                    else -> Unit
                }
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
                Log.d(TAG, "Signaling state alterado: $p0")
            }

            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                Log.d(TAG, "Track remoto recebido com sucesso")
                try {
                    val track = receiver?.track() as? AudioTrack
                    if (track != null) {
                        track.setEnabled(true)
                        val targetVolume = if (isSpeakerOn) 2.5 else 3.5
                        track.setVolume(targetVolume)
                        remoteAudioTrack = track
                        Log.d(TAG, "Volume do track remoto configurado para: $targetVolume")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Falha ao ajustar volume do track remoto: ${e.message}")
                }
            }
        })

        // Add local audio track
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("ARDAMS"))
        }
    }

    private fun createOffer() {
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        callback.onLocalDescriptionCreated(desc)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(err: String?) {
                        Log.e(TAG, "Falha ao aplicar SDP local na offer: $err")
                        callback.onError("Falha ao aplicar SDP local: $err")
                    }
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "Falha ao criar SDP offer: $err")
                callback.onError("Falha ao criar SDP Offer: $err")
            }
            override fun onSetFailure(p0: String?) {}
        }, sdpConstraints)
    }

    private fun createAnswer() {
        // In Unified Plan WebRTC, answer constraints should be empty to prevent transceiver conflicts
        val sdpConstraints = MediaConstraints()

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        callback.onLocalDescriptionCreated(desc)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(err: String?) {
                        Log.e(TAG, "Falha ao aplicar SDP local na answer: $err")
                        callback.onError("Falha ao aplicar SDP answer: $err")
                    }
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "Falha ao criar SDP answer: $err")
                callback.onError("Falha ao criar SDP Answer: $err")
            }
            override fun onSetFailure(p0: String?) {}
        }, sdpConstraints)
    }

    private fun setRemoteDescription(desc: SessionDescription, onSetSuccess: (() -> Unit)? = null) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "SDP Remoto configurado com sucesso (${desc.type}).")
                isRemoteDescriptionSet = true
                drainPendingIceCandidates()
                onSetSuccess?.invoke()
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "Falha ao configurar SDP Remoto: $err")
                callback.onError("Falha ao configurar SDP Remoto: $err")
            }
        }, desc)
    }

    fun addRemoteIceCandidate(candidate: IceCandidate) {
        if (candidate.sdp.isNullOrBlank()) return

        if (isRemoteDescriptionSet && peerConnection != null) {
            try {
                peerConnection?.addIceCandidate(candidate)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao adicionar ICE candidate", e)
            }
        } else {
            synchronized(pendingIceCandidates) {
                pendingIceCandidates.add(candidate)
            }
        }
    }

    private fun drainPendingIceCandidates() {
        synchronized(pendingIceCandidates) {
            for (c in pendingIceCandidates) {
                try {
                    peerConnection?.addIceCandidate(c)
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao drenar ICE candidate pendente", e)
                }
            }
            pendingIceCandidates.clear()
        }
    }

    fun toggleMute(): Boolean {
        isMicMuted = !isMicMuted
        localAudioTrack?.setEnabled(!isMicMuted)
        return isMicMuted
    }

    fun toggleSpeaker(): Boolean {
        isSpeakerOn = !isSpeakerOn
        proximityHelper.isScreenOffEnabled = !isSpeakerOn
        updateAudioRouting()
        try {
            val targetVolume = if (isSpeakerOn) 2.5 else 3.5
            remoteAudioTrack?.setVolume(targetVolume)
        } catch (e: Exception) {}
        return isSpeakerOn
    }

    private fun updateAudioRouting() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            try {
                val maxVoiceVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                val curVoiceVol = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                if (curVoiceVol < (maxVoiceVol * 0.85).toInt()) {
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (maxVoiceVol * 0.9).toInt(), 0)
                }
            } catch (e: Exception) {}

            if (isSpeakerOn) {
                audioManager.isSpeakerphoneOn = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val speaker = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speaker != null) {
                        audioManager.setCommunicationDevice(speaker)
                    } else {
                        audioManager.clearCommunicationDevice()
                    }
                }
            } else {
                audioManager.isSpeakerphoneOn = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val earpiece = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                    if (earpiece != null) {
                        audioManager.setCommunicationDevice(earpiece)
                    } else {
                        audioManager.clearCommunicationDevice()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao atualizar roteamento de áudio: ${e.message}")
        }
    }

    private fun enableVoIPAudioMode() {
        proximityHelper.isScreenOffEnabled = !isSpeakerOn
        updateAudioRouting()
        proximityHelper.startListening()
    }

    private fun resetAudioMode() {
        proximityHelper.stopListening()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {}
        isSpeakerOn = false
        remoteAudioTrack = null
    }

    fun endCall() {
        try {
            val hadActiveCall = peerConnection != null
            cleanupCurrentCall()
            resetAudioMode()
            if (hadActiveCall) {
                callback.onCallStateChanged(CallState.ENDED)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao encerrar chamada WebRTC", e)
        }
    }
}
