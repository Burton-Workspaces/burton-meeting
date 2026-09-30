package com.burton.meeting.domain

enum class CallMode {
    VIDEO,
    VOICE,
    ;

    val label: String
        get() = if (this == VIDEO) "Video" else "Voice"

    companion object {
        fun parse(raw: String?): CallMode =
            if (raw.equals("voice", ignoreCase = true)) VOICE else VIDEO
    }
}

enum class CallRole {
    HOST,
    GUEST,
}

enum class CallStatus {
    CONNECTING,
    CONNECTED,
    FAILED,
}

data class NearbyMeeting(
    val code: String,
    val name: String,
    val mode: CallMode,
    val host: String,
    val port: Int,
)

data class PeerInfo(
    val id: String,
    val displayName: String,
    val mode: CallMode,
    val muted: Boolean,
    val cameraOn: Boolean,
)

data class RemotePeer(
    val info: PeerInfo,
    val connected: Boolean = false,
    val videoTrack: Any? = null,
)

data class CallSession(
    val roomName: String,
    val roomCode: String,
    val roomMode: CallMode,
    val role: CallRole,
    val host: String,
    val port: Int,
    val localPeerId: String,
    val localDisplayName: String,
    val localMode: CallMode,
    val muted: Boolean,
    val cameraOn: Boolean,
    val speakerOn: Boolean,
    val status: CallStatus,
    val error: String? = null,
    val peers: List<RemotePeer> = emptyList(),
    val localVideoTrack: Any? = null,
    val eglContext: Any? = null,
) {
    val participantCount: Int get() = peers.size + 1
    val cameraAllowed: Boolean get() = roomMode == CallMode.VIDEO
}
