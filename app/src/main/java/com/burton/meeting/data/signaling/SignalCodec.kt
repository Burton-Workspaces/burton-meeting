package com.burton.meeting.data.signaling

import com.burton.meeting.data.parse.TinyJson
import com.burton.meeting.data.parse.TinyJson.bool
import com.burton.meeting.data.parse.TinyJson.int
import com.burton.meeting.data.parse.TinyJson.objList
import com.burton.meeting.data.parse.TinyJson.str
import com.burton.meeting.domain.CallMode
import com.burton.meeting.domain.PeerInfo

sealed class SignalMessage {
    data class Hello(
        val peerId: String,
        val displayName: String,
        val mode: CallMode,
        val code: String,
        val muted: Boolean,
        val cameraOn: Boolean,
    ) : SignalMessage()

    data class Welcome(
        val peerId: String,
        val roomName: String,
        val roomCode: String,
        val roomMode: CallMode,
        val peers: List<PeerInfo>,
    ) : SignalMessage()

    data class PeerJoined(val peer: PeerInfo) : SignalMessage()

    data class PeerLeft(val peerId: String) : SignalMessage()

    data class Offer(
        val from: String,
        val to: String,
        val sdp: String,
    ) : SignalMessage()

    data class Answer(
        val from: String,
        val to: String,
        val sdp: String,
    ) : SignalMessage()

    data class Ice(
        val from: String,
        val to: String,
        val candidate: String,
        val sdpMid: String,
        val sdpMLineIndex: Int,
    ) : SignalMessage()

    data class State(
        val peerId: String,
        val muted: Boolean,
        val cameraOn: Boolean,
        val displayName: String,
        val mode: CallMode,
    ) : SignalMessage()

    data class Error(val message: String) : SignalMessage()
}

object SignalCodec {
    fun encode(message: SignalMessage): String = TinyJson.stringify(toMap(message))

    fun decode(text: String): SignalMessage {
        val obj = TinyJson.parseObject(text)
        return when (obj.str("type")) {
            "hello" -> SignalMessage.Hello(
                peerId = obj.str("peerId"),
                displayName = obj.str("displayName"),
                mode = CallMode.parse(obj.str("mode")),
                code = obj.str("code"),
                muted = obj.bool("muted"),
                cameraOn = obj.bool("cameraOn"),
            )
            "welcome" -> SignalMessage.Welcome(
                peerId = obj.str("peerId"),
                roomName = obj.str("roomName"),
                roomCode = obj.str("roomCode"),
                roomMode = CallMode.parse(obj.str("roomMode")),
                peers = obj.objList("peers").map(::peerFrom),
            )
            "peer-joined" -> SignalMessage.PeerJoined(peerFrom(obj["peer"] as? Map<*, *>))
            "peer-left" -> SignalMessage.PeerLeft(obj.str("peerId"))
            "offer" -> SignalMessage.Offer(obj.str("from"), obj.str("to"), obj.str("sdp"))
            "answer" -> SignalMessage.Answer(obj.str("from"), obj.str("to"), obj.str("sdp"))
            "ice" -> SignalMessage.Ice(
                from = obj.str("from"),
                to = obj.str("to"),
                candidate = obj.str("candidate"),
                sdpMid = obj.str("sdpMid"),
                sdpMLineIndex = obj.int("sdpMLineIndex"),
            )
            "state" -> SignalMessage.State(
                peerId = obj.str("peerId"),
                muted = obj.bool("muted"),
                cameraOn = obj.bool("cameraOn"),
                displayName = obj.str("displayName"),
                mode = CallMode.parse(obj.str("mode")),
            )
            "error" -> SignalMessage.Error(obj.str("message", "Unknown error"))
            else -> SignalMessage.Error("Unknown signal type")
        }
    }

    fun peerToMap(peer: PeerInfo): Map<String, Any?> = mapOf(
        "id" to peer.id,
        "displayName" to peer.displayName,
        "mode" to peer.mode.name.lowercase(),
        "muted" to peer.muted,
        "cameraOn" to peer.cameraOn,
    )

    private fun toMap(message: SignalMessage): Map<String, Any?> = when (message) {
        is SignalMessage.Hello -> mapOf(
            "type" to "hello",
            "peerId" to message.peerId,
            "displayName" to message.displayName,
            "mode" to message.mode.name.lowercase(),
            "code" to message.code,
            "muted" to message.muted,
            "cameraOn" to message.cameraOn,
        )
        is SignalMessage.Welcome -> mapOf(
            "type" to "welcome",
            "peerId" to message.peerId,
            "roomName" to message.roomName,
            "roomCode" to message.roomCode,
            "roomMode" to message.roomMode.name.lowercase(),
            "peers" to message.peers.map(::peerToMap),
        )
        is SignalMessage.PeerJoined -> mapOf(
            "type" to "peer-joined",
            "peer" to peerToMap(message.peer),
        )
        is SignalMessage.PeerLeft -> mapOf(
            "type" to "peer-left",
            "peerId" to message.peerId,
        )
        is SignalMessage.Offer -> mapOf(
            "type" to "offer",
            "from" to message.from,
            "to" to message.to,
            "sdp" to message.sdp,
        )
        is SignalMessage.Answer -> mapOf(
            "type" to "answer",
            "from" to message.from,
            "to" to message.to,
            "sdp" to message.sdp,
        )
        is SignalMessage.Ice -> mapOf(
            "type" to "ice",
            "from" to message.from,
            "to" to message.to,
            "candidate" to message.candidate,
            "sdpMid" to message.sdpMid,
            "sdpMLineIndex" to message.sdpMLineIndex,
        )
        is SignalMessage.State -> mapOf(
            "type" to "state",
            "peerId" to message.peerId,
            "muted" to message.muted,
            "cameraOn" to message.cameraOn,
            "displayName" to message.displayName,
            "mode" to message.mode.name.lowercase(),
        )
        is SignalMessage.Error -> mapOf(
            "type" to "error",
            "message" to message.message,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun peerFrom(raw: Map<*, *>?): PeerInfo {
        val obj = (raw as? Map<String, Any?>).orEmpty()
        return PeerInfo(
            id = obj.str("id"),
            displayName = obj.str("displayName", "Guest"),
            mode = CallMode.parse(obj.str("mode")),
            muted = obj.bool("muted"),
            cameraOn = obj.bool("cameraOn"),
        )
    }
}
