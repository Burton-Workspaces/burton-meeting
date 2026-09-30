package com.burton.meeting.data.signaling

import com.burton.meeting.domain.CallMode
import com.burton.meeting.domain.PeerInfo
import com.burton.meeting.domain.RoomCodes
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class MeetingSignalServer(
    private val roomName: String,
    private val roomCode: String,
    private val roomMode: CallMode,
    private val port: Int = RoomCodes.SIGNAL_PORT,
) {
    private val peers = ConcurrentHashMap<WebSocket, PeerInfo>()
    private var server: WebSocketServer? = null

    val boundPort: Int get() = server?.port ?: port

    suspend fun start() {
        check(server == null) { "Server already started" }
        suspendCancellableCoroutine { cont ->
            val started = serverImpl(onStart = {
                if (cont.isActive) cont.resume(Unit)
            })
            server = started
            started.start()
            cont.invokeOnCancellation {
                started.stop(500)
            }
        }
    }

    fun stop() {
        runCatching { server?.stop(500) }
        server = null
        peers.clear()
    }

    private fun serverImpl(onStart: () -> Unit): WebSocketServer =
        object : WebSocketServer(InetSocketAddress(port)) {
            override fun onStart() = onStart()

            override fun onOpen(conn: WebSocket, handshake: ClientHandshake) = Unit

            override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
                val left = peers.remove(conn) ?: return
                broadcast(SignalMessage.PeerLeft(left.id), except = conn)
            }

            override fun onMessage(conn: WebSocket, message: String) {
                val parsed = runCatching { SignalCodec.decode(message) }.getOrNull()
                    ?: return send(conn, SignalMessage.Error("Bad message"))
                handle(conn, parsed)
            }

            override fun onError(conn: WebSocket?, ex: Exception) = Unit
        }

    private fun handle(conn: WebSocket, message: SignalMessage) {
        when (message) {
            is SignalMessage.Hello -> hello(conn, message)
            is SignalMessage.Offer,
            is SignalMessage.Answer,
            is SignalMessage.Ice,
            -> relay(conn, message)
            is SignalMessage.State -> {
                val current = peers[conn] ?: return
                val updated = current.copy(
                    muted = message.muted,
                    cameraOn = message.cameraOn,
                    displayName = message.displayName.ifBlank { current.displayName },
                    mode = message.mode,
                )
                peers[conn] = updated
                broadcast(message.copy(peerId = current.id), except = conn)
            }
            else -> Unit
        }
    }

    private fun hello(conn: WebSocket, hello: SignalMessage.Hello) {
        if (RoomCodes.normalize(hello.code) != roomCode) {
            send(conn, SignalMessage.Error("Wrong meeting code"))
            conn.close()
            return
        }
        val peerId = hello.peerId.ifBlank { conn.remoteSocketAddress?.toString().orEmpty() }
        val info = PeerInfo(
            id = peerId,
            displayName = hello.displayName.ifBlank { "Guest" },
            mode = if (roomMode == CallMode.VOICE) CallMode.VOICE else hello.mode,
            muted = hello.muted,
            cameraOn = hello.cameraOn && roomMode == CallMode.VIDEO,
        )
        val existing = peers.values.toList()
        peers[conn] = info
        send(
            conn,
            SignalMessage.Welcome(
                peerId = info.id,
                roomName = roomName,
                roomCode = roomCode,
                roomMode = roomMode,
                peers = existing,
            ),
        )
        broadcast(SignalMessage.PeerJoined(info), except = conn)
    }

    private fun relay(from: WebSocket, message: SignalMessage) {
        val targetId = when (message) {
            is SignalMessage.Offer -> message.to
            is SignalMessage.Answer -> message.to
            is SignalMessage.Ice -> message.to
            else -> return
        }
        val target = peers.entries.firstOrNull { it.value.id == targetId }?.key ?: return
        send(target, message)
    }

    private fun broadcast(message: SignalMessage, except: WebSocket? = null) {
        val payload = SignalCodec.encode(message)
        peers.keys.forEach { socket ->
            if (socket != except && socket.isOpen) socket.send(payload)
        }
    }

    private fun send(conn: WebSocket, message: SignalMessage) {
        if (conn.isOpen) conn.send(SignalCodec.encode(message))
    }
}
