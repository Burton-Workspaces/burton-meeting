package com.burton.meeting.data.repository

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import com.burton.meeting.data.discovery.MeetingDiscovery
import com.burton.meeting.data.signaling.MeetingSignalServer
import com.burton.meeting.data.signaling.SignalClient
import com.burton.meeting.data.signaling.SignalMessage
import com.burton.meeting.data.webrtc.PeerEngine
import com.burton.meeting.domain.CallMode
import com.burton.meeting.domain.CallRole
import com.burton.meeting.domain.CallSession
import com.burton.meeting.domain.CallStatus
import com.burton.meeting.domain.NearbyMeeting
import com.burton.meeting.domain.PeerInfo
import com.burton.meeting.domain.RoomCodes
import com.burton.meeting.service.MeetingCallService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate

@Singleton
class MeetingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: LocalPrefs,
    private val discovery: MeetingDiscovery,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val nearby = discovery.nearby
    val displayName = prefs.displayName

    private val _session = MutableStateFlow<CallSession?>(null)
    val session: StateFlow<CallSession?> = _session

    private var sessionJob: Job? = null
    private var server: MeetingSignalServer? = null
    private var client: SignalClient? = null
    private var engine: PeerEngine? = null

    fun startBrowsing() = discovery.startBrowsing()
    fun stopBrowsing() = discovery.stopBrowsing()

    suspend fun setDisplayName(name: String) = prefs.setDisplayName(name)

    suspend fun host(roomName: String, roomMode: CallMode) {
        leaveInternal(resumeBrowse = false)
        val code = RoomCodes.generate()
        val name = roomName.ifBlank { "Meeting" }
        val hosted = MeetingSignalServer(name, code, roomMode)
        hosted.start()
        server = hosted
        discovery.stopBrowsing()
        discovery.advertise(name, code, roomMode, hosted.boundPort)
        connect(
            meeting = NearbyMeeting(code, name, roomMode, "127.0.0.1", hosted.boundPort),
            role = CallRole.HOST,
            localMode = roomMode,
            publicHost = localIpv4(),
        )
    }

    suspend fun join(meeting: NearbyMeeting, localMode: CallMode) {
        leaveInternal(resumeBrowse = false)
        discovery.stopBrowsing()
        val mode = if (meeting.mode == CallMode.VOICE) CallMode.VOICE else localMode
        connect(meeting, CallRole.GUEST, mode, meeting.host)
    }

    suspend fun joinByCode(code: String, localMode: CallMode) {
        val meeting = discovery.findByCode(code)
            ?: error("No meeting with that code on this Wi-Fi")
        join(meeting, localMode)
    }

    fun setMuted(muted: Boolean) {
        val current = _session.value ?: return
        scope.launch {
            engine?.setMuted(muted)
            client?.send(stateMessage(current.copy(muted = muted)))
            publishEngine()
        }
    }

    fun setCameraEnabled(enabled: Boolean) {
        val current = _session.value ?: return
        if (!current.cameraAllowed) return
        scope.launch {
            engine?.setCameraEnabled(enabled)
            val next = current.copy(
                cameraOn = enabled,
                localMode = if (enabled) CallMode.VIDEO else CallMode.VOICE,
            )
            client?.send(stateMessage(next))
            publishEngine()
        }
    }

    fun setSpeakerOn(on: Boolean) {
        applyAudio(on)
        _session.value = _session.value?.copy(speakerOn = on)
    }

    fun leave() {
        leaveInternal(resumeBrowse = true)
    }

    private suspend fun connect(
        meeting: NearbyMeeting,
        role: CallRole,
        localMode: CallMode,
        publicHost: String,
    ) {
        val peerId = UUID.randomUUID().toString().take(8)
        val display = prefs.displayName.first().ifBlank { defaultName() }
        val cameraOn = localMode == CallMode.VIDEO && meeting.mode == CallMode.VIDEO
        val next = CallSession(
            roomName = meeting.name,
            roomCode = meeting.code,
            roomMode = meeting.mode,
            role = role,
            host = publicHost,
            port = meeting.port,
            localPeerId = peerId,
            localDisplayName = display,
            localMode = localMode,
            muted = false,
            cameraOn = cameraOn,
            speakerOn = true,
            status = CallStatus.CONNECTING,
        )
        _session.value = next
        applyAudio(true)
        startCallService(next)

        val rtc = PeerEngine(
            context = context,
            localPeerId = peerId,
            roomMode = meeting.mode,
            localMode = localMode,
            send = { client?.send(it) },
            onChanged = { scope.launch { publishEngine() } },
        )
        engine = rtc
        rtc.start(cameraEnabled = cameraOn, muted = false)

        val signal = SignalClient(meeting.host, meeting.port)
        client = signal
        signal.connect()
        signal.send(
            SignalMessage.Hello(
                peerId = peerId,
                displayName = display,
                mode = localMode,
                code = meeting.code,
                muted = false,
                cameraOn = cameraOn,
            ),
        )
        sessionJob = scope.launch {
            signal.messages.collect { onSignal(it) }
        }
        publishEngine()
    }

    private suspend fun onSignal(message: SignalMessage) {
        val current = _session.value ?: return
        val rtc = engine ?: return
        when (message) {
            is SignalMessage.Welcome -> {
                _session.value = current.copy(
                    roomName = message.roomName,
                    roomCode = message.roomCode,
                    roomMode = message.roomMode,
                    status = CallStatus.CONNECTED,
                    error = null,
                )
                message.peers.forEach { rtc.updatePeer(it) }
                publishEngine()
            }
            is SignalMessage.PeerJoined -> {
                if (message.peer.id == current.localPeerId) return
                rtc.updatePeer(message.peer)
                rtc.offerTo(message.peer)
                publishEngine()
            }
            is SignalMessage.PeerLeft -> {
                rtc.removePeer(message.peerId)
                publishEngine()
            }
            is SignalMessage.Offer -> if (message.to == current.localPeerId) {
                rtc.handleOffer(message.from, message.sdp)
            }
            is SignalMessage.Answer -> if (message.to == current.localPeerId) {
                rtc.handleAnswer(message.from, message.sdp)
            }
            is SignalMessage.Ice -> if (message.to == current.localPeerId) {
                rtc.handleIce(
                    message.from,
                    IceCandidate(message.sdpMid, message.sdpMLineIndex, message.candidate),
                )
            }
            is SignalMessage.State -> rtc.updatePeer(
                PeerInfo(
                    id = message.peerId,
                    displayName = message.displayName,
                    mode = message.mode,
                    muted = message.muted,
                    cameraOn = message.cameraOn,
                ),
            ).also { publishEngine() }
            is SignalMessage.Error -> {
                if (_session.value == null) return
                if (message.message == "Disconnected" && sessionJob?.isActive != true) return
                _session.value = current.copy(status = CallStatus.FAILED, error = message.message)
            }
            is SignalMessage.Hello -> Unit
        }
    }

    private fun publishEngine() {
        val current = _session.value ?: return
        val rtc = engine ?: return
        _session.value = current.copy(
            muted = rtc.snapshotMuted(),
            cameraOn = rtc.snapshotCameraOn(),
            localMode = rtc.snapshotLocalMode(),
            localVideoTrack = rtc.localVideoTrack,
            eglContext = rtc.eglBase.eglBaseContext,
            peers = rtc.remotes,
            status = if (current.status == CallStatus.FAILED) current.status else CallStatus.CONNECTED,
        )
    }

    private fun leaveInternal(resumeBrowse: Boolean) {
        sessionJob?.cancel()
        sessionJob = null
        _session.value = null
        runCatching { engine?.release() }
        engine = null
        runCatching { client?.close() }
        client = null
        runCatching { server?.stop() }
        server = null
        discovery.stopAdvertising()
        stopCallService()
        restoreAudio()
        if (resumeBrowse) discovery.startBrowsing()
    }

    private fun stateMessage(session: CallSession) = SignalMessage.State(
        peerId = session.localPeerId,
        muted = session.muted,
        cameraOn = session.cameraOn,
        displayName = session.localDisplayName,
        mode = session.localMode,
    )

    private fun applyAudio(speakerOn: Boolean) {
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audio.isSpeakerphoneOn = speakerOn
    }

    private fun restoreAudio() {
        audio.mode = AudioManager.MODE_NORMAL
        @Suppress("DEPRECATION")
        audio.isSpeakerphoneOn = false
    }

    private fun startCallService(session: CallSession) {
        val intent = Intent(context, MeetingCallService::class.java)
            .putExtra(MeetingCallService.EXTRA_TITLE, session.roomName)
            .putExtra(MeetingCallService.EXTRA_VOICE, session.roomMode == CallMode.VOICE)
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopCallService() {
        context.stopService(Intent(context, MeetingCallService::class.java))
    }

    private fun defaultName(): String {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val raw = wifi.connectionInfo?.ssid?.trim('"').orEmpty()
        return if (raw.isBlank() || raw == "<unknown ssid>") "Phone" else raw
    }

    private fun localIpv4(): String {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }
            .getOrDefault(emptyList())
        for (network in interfaces) {
            val address = network.inetAddresses.toList()
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
            if (address != null) return address.hostAddress ?: continue
        }
        return "127.0.0.1"
    }
}
