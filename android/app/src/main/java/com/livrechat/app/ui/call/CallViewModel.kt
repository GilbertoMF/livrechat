package com.livrechat.app.ui.call

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.livrechat.app.LivreChatApp
import com.livrechat.app.data.local.entity.CallRecordEntity
import com.livrechat.app.data.remote.WebSocketClient
import com.livrechat.app.data.remote.WebSocketEventsListener
import com.livrechat.app.data.local.entity.MessageEntity
import com.livrechat.app.service.CallForegroundService
import com.livrechat.app.webrtc.CallState
import com.livrechat.app.webrtc.WebRTCCallback
import com.livrechat.app.webrtc.WebRTCManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.util.UUID

data class ActiveCallInfo(
    val contactUserId: String,
    val contactDisplayName: String,
    val contactUsername: String,
    val contactAvatarUrl: String?,
    val isOutgoing: Boolean,
    val remoteSdp: String? = null
)

class CallViewModel(application: Application) : AndroidViewModel(application), WebRTCCallback, WebSocketEventsListener {
    private val app = getApplication<LivreChatApp>()
    private val callDao = app.database.callDao()
    private val apiService = app.apiClient.apiService
    private val prefs = app.preferencesManager

    val callHistoryFlow = callDao.getAllCallsFlow()

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _callInfo = MutableStateFlow<ActiveCallInfo?>(null)
    val callInfo: StateFlow<ActiveCallInfo?> = _callInfo.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeaker = MutableStateFlow(false)
    val isSpeaker: StateFlow<Boolean> = _isSpeaker.asStateFlow()

    private val _durationSeconds = MutableStateFlow(0)
    val durationSeconds: StateFlow<Int> = _durationSeconds.asStateFlow()

    private var timerJob: Job? = null
    private var webRTCManager: WebRTCManager? = null
    var webSocketClient: WebSocketClient? = null

    init {
        webRTCManager = WebRTCManager(app, this)
        if (prefs.isLoggedIn()) {
            webSocketClient = WebSocketClient(prefs, this)
            webSocketClient?.connect()
        }
        loadIceServers()
    }

    private fun loadIceServers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = apiService.getIceServers()
                if (res.isSuccessful && res.body() != null) {
                    val servers = res.body()!!.iceServers.map { dto ->
                        val url = if (dto.urls is List<*>) (dto.urls as List<String>)[0] else dto.urls.toString()
                        val builder = PeerConnection.IceServer.builder(url)
                        dto.username?.let { builder.setUsername(it) }
                        dto.credential?.let { builder.setPassword(it) }
                        builder.createIceServer()
                    }
                    webRTCManager?.setIceServers(servers)
                }
            } catch (e: Exception) {
                // Use default Google STUN
            }
        }
    }

    fun startOutgoingCall(
        contactUserId: String,
        contactDisplayName: String,
        contactUsername: String,
        contactAvatarUrl: String?
    ) {
        _callInfo.value = ActiveCallInfo(
            contactUserId = contactUserId,
            contactDisplayName = contactDisplayName,
            contactUsername = contactUsername,
            contactAvatarUrl = contactAvatarUrl,
            isOutgoing = true
        )
        _durationSeconds.value = 0

        CallForegroundService.start(app, contactDisplayName)
        webRTCManager?.startCall()
    }

    fun answerIncomingCall() {
        val info = _callInfo.value ?: return
        val sdp = info.remoteSdp ?: return

        CallForegroundService.start(app, info.contactDisplayName)
        webRTCManager?.answerCall(sdp)
    }

    fun rejectIncomingCall() {
        val info = _callInfo.value
        if (info != null) {
            webSocketClient?.sendCallReject(info.contactUserId, "Chamada recusada")
            recordCall(info, "rejected", 0)
        }
        cleanupCall()
    }

    fun hangupCall() {
        val info = _callInfo.value
        val duration = _durationSeconds.value
        if (info != null) {
            webSocketClient?.sendCallHangup(info.contactUserId, duration)
            recordCall(info, "completed", duration)
        }
        webRTCManager?.endCall()
        cleanupCall()
    }

    fun toggleMute() {
        _isMuted.value = webRTCManager?.toggleMute() ?: false
    }

    fun toggleSpeaker() {
        _isSpeaker.value = webRTCManager?.toggleSpeaker() ?: false
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            _durationSeconds.value = 0
            while (_callState.value == CallState.CONNECTED) {
                delay(1000)
                _durationSeconds.value++
            }
        }
    }

    private fun cleanupCall() {
        timerJob?.cancel()
        CallForegroundService.stop(app)
        _callState.value = CallState.IDLE
        _callInfo.value = null
        _isMuted.value = false
        _isSpeaker.value = false
    }

    private fun recordCall(info: ActiveCallInfo, status: String, duration: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val record = CallRecordEntity(
                id = UUID.randomUUID().toString(),
                contactUserId = info.contactUserId,
                contactDisplayName = info.contactDisplayName,
                contactUsername = info.contactUsername,
                contactAvatarUrl = info.contactAvatarUrl,
                isOutgoing = info.isOutgoing,
                status = status,
                durationSeconds = duration,
                timestamp = System.currentTimeMillis()
            )
            callDao.insertCall(record)
        }
    }

    // WebRTCCallback
    override fun onCallStateChanged(state: CallState) {
        _callState.value = state
        if (state == CallState.CONNECTED) {
            startTimer()
        } else if (state == CallState.ENDED) {
            cleanupCall()
        }
    }

    override fun onLocalDescriptionCreated(sdp: SessionDescription) {
        val info = _callInfo.value ?: return
        if (info.isOutgoing) {
            webSocketClient?.sendCallOffer(info.contactUserId, sdp.description)
        } else {
            webSocketClient?.sendCallAnswer(info.contactUserId, sdp.description)
        }
    }

    override fun onIceCandidateGenerated(candidate: IceCandidate) {
        val info = _callInfo.value ?: return
        val json = JsonObject().apply {
            addProperty("sdpMid", candidate.sdpMid)
            addProperty("sdpMLineIndex", candidate.sdpMLineIndex)
            addProperty("candidate", candidate.sdp)
        }
        webSocketClient?.sendIceCandidate(info.contactUserId, json)
    }

    override fun onError(message: String) {
        hangupCall()
    }

    // WebSocketEventsListener WebRTC callbacks
    override fun onIncomingCall(callerId: String, callerUsername: String, callerDisplayName: String, callerAvatarUrl: String?, sdp: String) {
        _callInfo.value = ActiveCallInfo(
            contactUserId = callerId,
            contactDisplayName = callerDisplayName,
            contactUsername = callerUsername,
            contactAvatarUrl = callerAvatarUrl,
            isOutgoing = false,
            remoteSdp = sdp
        )
        _callState.value = CallState.RINGING
        webSocketClient?.sendCallRinging(callerId)
    }

    override fun onCallAnswered(calleeId: String, sdp: String) {
        webRTCManager?.onRemoteAnswerReceived(sdp)
    }

    override fun onIceCandidateReceived(fromUserId: String, candidate: JsonObject) {
        val sdpMid = if (candidate.has("sdpMid") && !candidate.get("sdpMid").isJsonNull) candidate.get("sdpMid").asString else null
        val sdpMLineIndex = if (candidate.has("sdpMLineIndex") && !candidate.get("sdpMLineIndex").isJsonNull) candidate.get("sdpMLineIndex").asInt else 0
        val sdp = if (candidate.has("candidate") && !candidate.get("candidate").isJsonNull) candidate.get("candidate").asString else ""
        webRTCManager?.addRemoteIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp))
    }

    override fun onCallRinging(fromUserId: String) {
        _callState.value = CallState.RINGING
    }

    override fun onCallRejected(fromUserId: String, reason: String) {
        val info = _callInfo.value
        if (info != null) {
            recordCall(info, "rejected", 0)
        }
        cleanupCall()
    }

    override fun onCallEnded(fromUserId: String) {
        val info = _callInfo.value
        if (info != null) {
            recordCall(info, "completed", _durationSeconds.value)
        }
        cleanupCall()
    }

    override fun onConnected() {}
    override fun onDisconnected() {}
    override fun onMessageReceived(message: MessageEntity) {}
    override fun onMessageAck(messageId: String, clientMessageId: String?, status: String) {}
    override fun onMessageReceipt(messageId: String, status: String, userId: String) {}
    override fun onTyping(conversationId: String, userId: String, username: String, isTyping: Boolean) {}

    override fun onCleared() {
        super.onCleared()
        webRTCManager?.endCall()
    }
}
