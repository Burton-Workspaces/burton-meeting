package com.burton.meeting.data.webrtc

import android.content.Context
import com.burton.meeting.data.signaling.SignalMessage
import com.burton.meeting.domain.CallMode
import com.burton.meeting.domain.PeerInfo
import com.burton.meeting.domain.RemotePeer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
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
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

class PeerEngine(
    private val context: Context,
    private val localPeerId: String,
    private val roomMode: CallMode,
    private var localMode: CallMode,
    private val send: (SignalMessage) -> Unit,
    private val onChanged: () -> Unit,
) {
    val eglBase: EglBase = EglBase.create()
    var localVideoTrack: VideoTrack? = null
        private set

    private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "burton-rtc") }
        .asCoroutineDispatcher()
    private val connections = ConcurrentHashMap<String, PeerConnection>()
    private val pendingIce = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    private val remoteInfo = ConcurrentHashMap<String, RemotePeer>()
    private val remoteReady = ConcurrentHashMap<String, Boolean>()

    private lateinit var factory: PeerConnectionFactory
    private lateinit var audioDevice: JavaAudioDeviceModule
    private var audioSource: AudioSource? = null
    private var videoSource: VideoSource? = null
    private var audioTrack: AudioTrack? = null
    private var capturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var cameraOn = false
    private var muted = false
    private var released = false

    val remotes: List<RemotePeer>
        get() = remoteInfo.values.sortedBy { it.info.displayName.lowercase() }

    suspend fun start(cameraEnabled: Boolean, muted: Boolean) = withContext(dispatcher) {
        ensureFactory(context)
        audioDevice = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        val encoder = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoder = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDevice)
            .setVideoEncoderFactory(encoder)
            .setVideoDecoderFactory(decoder)
            .createPeerConnectionFactory()
        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("burton-audio", audioSource).also {
            it.setEnabled(!muted)
        }
        this@PeerEngine.muted = muted
        if (roomMode == CallMode.VIDEO) {
            videoSource = factory.createVideoSource(false)
            localVideoTrack = factory.createVideoTrack("burton-video", videoSource)
            if (cameraEnabled && localMode == CallMode.VIDEO) {
                startCameraLocked()
            } else {
                localVideoTrack?.setEnabled(false)
            }
        }
        cameraOn = localVideoTrack?.enabled() == true
        onChanged()
    }

    suspend fun offerTo(peer: PeerInfo) = withContext(dispatcher) {
        remember(peer)
        val pc = connectionLocked(peer.id)
        val sdp = pc.createOfferSdp(offerConstraints())
        pc.setLocalSdp(sdp)
        send(SignalMessage.Offer(localPeerId, peer.id, sdp.description))
    }

    suspend fun handleOffer(from: String, sdp: String) = withContext(dispatcher) {
        val pc = connectionLocked(from)
        pc.setRemoteSdp(SessionDescription(SessionDescription.Type.OFFER, sdp))
        drainIceLocked(from, pc)
        val answer = pc.createAnswerSdp(offerConstraints())
        pc.setLocalSdp(answer)
        send(SignalMessage.Answer(localPeerId, from, answer.description))
    }

    suspend fun handleAnswer(from: String, sdp: String) = withContext(dispatcher) {
        val pc = connections[from] ?: return@withContext
        pc.setRemoteSdp(SessionDescription(SessionDescription.Type.ANSWER, sdp))
        drainIceLocked(from, pc)
    }

    suspend fun handleIce(from: String, candidate: IceCandidate) = withContext(dispatcher) {
        val pc = connections[from]
        val remoteSet = remoteReady[from] == true
        if (pc == null || !remoteSet) {
            pendingIce.getOrPut(from) { mutableListOf() }.add(candidate)
        } else {
            pc.addIceCandidate(candidate)
        }
    }

    suspend fun updatePeer(info: PeerInfo) = withContext(dispatcher) {
        val current = remoteInfo[info.id]
        remoteInfo[info.id] = (current ?: RemotePeer(info)).copy(info = info)
        onChanged()
    }

    suspend fun removePeer(peerId: String) = withContext(dispatcher) {
        connections.remove(peerId)?.close()
        remoteInfo.remove(peerId)
        pendingIce.remove(peerId)
        remoteReady.remove(peerId)
        onChanged()
    }

    suspend fun setMuted(value: Boolean) = withContext(dispatcher) {
        muted = value
        audioTrack?.setEnabled(!value)
        onChanged()
    }

    suspend fun setCameraEnabled(value: Boolean) = withContext(dispatcher) {
        if (roomMode != CallMode.VIDEO) return@withContext
        if (value) startCameraLocked() else stopCameraLocked()
        localMode = if (value) CallMode.VIDEO else CallMode.VOICE
        onChanged()
    }

    fun snapshotCameraOn(): Boolean = cameraOn
    fun snapshotMuted(): Boolean = muted
    fun snapshotLocalMode(): CallMode = localMode

    fun release() {
        if (released) return
        released = true
        runBlocking(dispatcher) {
            capturer?.stopCapture()
            capturer?.dispose()
            capturer = null
            textureHelper?.dispose()
            textureHelper = null
            connections.values.forEach { it.close() }
            connections.clear()
            localVideoTrack?.dispose()
            audioTrack?.dispose()
            videoSource?.dispose()
            audioSource?.dispose()
            if (this@PeerEngine::factory.isInitialized) factory.dispose()
            if (this@PeerEngine::audioDevice.isInitialized) audioDevice.release()
            eglBase.release()
        }
        dispatcher.close()
    }

    private fun remember(peer: PeerInfo) {
        remoteInfo[peer.id] = remoteInfo[peer.id]?.copy(info = peer) ?: RemotePeer(peer)
    }

    private fun connectionLocked(remoteId: String): PeerConnection {
        connections[remoteId]?.let { return it }
        val config = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        }
        val pc = factory.createPeerConnection(config, PcObserver(remoteId))
            ?: error("Could not create peer connection")
        audioTrack?.let { pc.addTrack(it, listOf(STREAM_ID)) }
        localVideoTrack?.let { pc.addTrack(it, listOf(STREAM_ID)) }
        connections[remoteId] = pc
        remember(PeerInfo(remoteId, "Guest", CallMode.VOICE, muted = false, cameraOn = false))
        return pc
    }

    private fun startCameraLocked() {
        if (capturer != null) {
            localVideoTrack?.setEnabled(true)
            cameraOn = true
            return
        }
        val source = videoSource ?: return
        val next = createCapturer() ?: return
        textureHelper = SurfaceTextureHelper.create("burton-capture", eglBase.eglBaseContext)
        next.initialize(textureHelper, context, source.capturerObserver)
        next.startCapture(960, 540, 24)
        capturer = next
        localVideoTrack?.setEnabled(true)
        cameraOn = true
    }

    private fun stopCameraLocked() {
        runCatching { capturer?.stopCapture() }
        capturer?.dispose()
        capturer = null
        textureHelper?.dispose()
        textureHelper = null
        localVideoTrack?.setEnabled(false)
        cameraOn = false
    }

    private fun createCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val preferred = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
            ?: return null
        return enumerator.createCapturer(preferred, null)
    }

    private fun offerConstraints() = MediaConstraints().apply {
        mandatory += MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
        mandatory += MediaConstraints.KeyValuePair(
            "OfferToReceiveVideo",
            if (roomMode == CallMode.VIDEO) "true" else "false",
        )
    }

    private fun drainIceLocked(peerId: String, pc: PeerConnection) {
        remoteReady[peerId] = true
        pendingIce.remove(peerId).orEmpty().forEach { pc.addIceCandidate(it) }
    }

    private inner class PcObserver(private val remoteId: String) : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddStream(stream: MediaStream?) = Unit
        override fun onRemoveStream(stream: MediaStream?) = Unit
        override fun onDataChannel(channel: org.webrtc.DataChannel?) = Unit

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            val connected = state == PeerConnection.IceConnectionState.CONNECTED ||
                state == PeerConnection.IceConnectionState.COMPLETED
            val current = remoteInfo[remoteId] ?: return
            remoteInfo[remoteId] = current.copy(connected = connected)
            onChanged()
        }

        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate ?: return
            send(
                SignalMessage.Ice(
                    from = localPeerId,
                    to = remoteId,
                    candidate = candidate.sdp,
                    sdpMid = candidate.sdpMid ?: "",
                    sdpMLineIndex = candidate.sdpMLineIndex,
                ),
            )
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit

        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            val track = receiver?.track() as? VideoTrack ?: return
            val current = remoteInfo[remoteId] ?: RemotePeer(
                PeerInfo(remoteId, "Guest", CallMode.VIDEO, muted = false, cameraOn = true),
            )
            remoteInfo[remoteId] = current.copy(videoTrack = track, connected = true)
            onChanged()
        }
    }

    companion object {
        private const val STREAM_ID = "burton"
        @Volatile private var factoryReady = false

        fun ensureFactory(context: Context) {
            if (factoryReady) return
            synchronized(this) {
                if (factoryReady) return
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions(),
                )
                factoryReady = true
            }
        }
    }
}

private suspend fun PeerConnection.createOfferSdp(constraints: MediaConstraints): SessionDescription =
    suspendCancellableCoroutine { cont ->
        createOffer(SdpSink(cont), constraints)
    }

private suspend fun PeerConnection.createAnswerSdp(constraints: MediaConstraints): SessionDescription =
    suspendCancellableCoroutine { cont ->
        createAnswer(SdpSink(cont), constraints)
    }

private suspend fun PeerConnection.setLocalSdp(sdp: SessionDescription) =
    suspendCancellableCoroutine { cont ->
        setLocalDescription(SetSink(cont), sdp)
    }

private suspend fun PeerConnection.setRemoteSdp(sdp: SessionDescription) =
    suspendCancellableCoroutine { cont ->
        setRemoteDescription(SetSink(cont), sdp)
    }

private class SdpSink(
    private val cont: kotlin.coroutines.Continuation<SessionDescription>,
) : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) {
        if (sdp != null) cont.resume(sdp) else cont.resumeWithException(IllegalStateException("Empty SDP"))
    }

    override fun onSetSuccess() = Unit
    override fun onCreateFailure(error: String?) {
        cont.resumeWithException(IllegalStateException(error ?: "SDP create failed"))
    }

    override fun onSetFailure(error: String?) = Unit
}

private class SetSink(
    private val cont: kotlin.coroutines.Continuation<Unit>,
) : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) = Unit
    override fun onSetSuccess() {
        cont.resume(Unit)
    }

    override fun onCreateFailure(error: String?) = Unit
    override fun onSetFailure(error: String?) {
        cont.resumeWithException(IllegalStateException(error ?: "SDP set failed"))
    }
}
