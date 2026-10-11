package com.livrechat.app.webrtc

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
            if (!isSpeakerOn) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val earpiece = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                    if (earpiece != null) {
                        audioManager.setCommunicationDevice(earpiece)
                    }
                }
            }
        }
    }

    private fun initWebRTC() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val eglBase = EglBase.create()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()

        // Default Google STUN servers
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
        iceServers.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer())
    }

    fun setIceServers(servers: List<PeerConnection.IceServer>) {
        iceServers.clear()
        iceServers.addAll(servers)
    }

    private fun createAudioTrack() {
        val audioConstraints = MediaConstraints().apply {
            // Low-latency and noise-reduction settings
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }

        localAudioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("LIVRE_AUDIO_TRACK", localAudioSource)
        localAudioTrack?.setEnabled(true)
    }

    fun startCall() {
        isRemoteDescriptionSet = false
        synchronized(pendingIceCandidates) { pendingIceCandidates.clear() }
        callback.onCallStateChanged(CallState.CALLING)
        setupPeerConnection()
        createOffer()
    }

    fun answerCall(remoteSdp: String) {
        isRemoteDescriptionSet = false
        synchronized(pendingIceCandidates) { pendingIceCandidates.clear() }
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
        createAudioTrack()

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            keyType = PeerConnection.KeyType.ECDSA
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
                        Log.w(TAG, "ICE Connection Failed")
                        callback.onCallStateChanged(CallState.ENDED)
                        resetAudioMode()
                    }
                    PeerConnection.IceConnectionState.CLOSED -> {
                        callback.onCallStateChanged(CallState.ENDED)
                        resetAudioMode()
                    }
                    else -> Unit
                }
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
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
                        callback.onError("Falha ao aplicar SDP local: $err")
                    }
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                callback.onError("Falha ao criar SDP Offer: $err")
            }
            override fun onSetFailure(p0: String?) {}
        }, sdpConstraints)
    }

    private fun createAnswer() {
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        callback.onLocalDescriptionCreated(desc)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(err: String?) {
                        callback.onError("Falha ao aplicar SDP answer: $err")
                    }
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                callback.onError("Falha ao criar SDP Answer: $err")
            }
            override fun onSetFailure(p0: String?) {}
        }, sdpConstraints)
    }

    private fun setRemoteDescription(desc: SessionDescription, onSetSuccess: (() -> Unit)? = null) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "SDP Remoto configurado com sucesso.")
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

    // Audio controls
    fun toggleMute(): Boolean {
        isMicMuted = !isMicMuted
        localAudioTrack?.setEnabled(!isMicMuted)
        return isMicMuted
    }

    fun toggleSpeaker(): Boolean {
        isSpeakerOn = !isSpeakerOn
        audioManager.isSpeakerphoneOn = isSpeakerOn
        if (isSpeakerOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        return isSpeakerOn
    }

    private fun enableVoIPAudioMode() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = isSpeakerOn
        proximityHelper.startListening()
    }

    private fun resetAudioMode() {
        proximityHelper.stopListening()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }

    fun endCall() {
        try {
            isRemoteDescriptionSet = false
            synchronized(pendingIceCandidates) {
                pendingIceCandidates.clear()
            }
            localAudioTrack?.setEnabled(false)
            localAudioSource?.dispose()
            peerConnection?.close()
            peerConnection = null
            resetAudioMode()
            callback.onCallStateChanged(CallState.ENDED)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao encerrar chamada WebRTC", e)
        }
    }
}
