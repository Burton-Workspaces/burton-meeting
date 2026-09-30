package com.burton.meeting.data.signaling

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class SignalClient(
    host: String,
    port: Int,
) {
    private val incoming = Channel<SignalMessage>(Channel.BUFFERED)
    val messages: Flow<SignalMessage> = incoming.receiveAsFlow()
    private var stopping = false

    private val client = object : WebSocketClient(URI("ws://$host:$port")) {
        override fun onOpen(handshakedata: ServerHandshake?) = Unit

        override fun onMessage(message: String) {
            val parsed = runCatching { SignalCodec.decode(message) }.getOrNull() ?: return
            incoming.trySend(parsed)
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) {
            if (!stopping) {
                incoming.trySend(SignalMessage.Error(reason.ifBlank { "Disconnected" }))
            }
            incoming.close()
        }

        override fun onError(ex: Exception) {
            incoming.trySend(SignalMessage.Error(ex.message ?: "Signaling error"))
        }
    }

    suspend fun connect() {
        suspendCancellableCoroutine { cont ->
            client.setConnectionLostTimeout(0)
            Thread {
                try {
                    val ok = client.connectBlocking()
                    if (cont.isActive) {
                        if (ok) cont.resume(Unit)
                        else cont.resumeWithException(IllegalStateException("Could not reach the host"))
                    }
                } catch (error: Exception) {
                    if (cont.isActive) cont.resumeWithException(error)
                }
            }.apply { name = "burton-signal-connect"; start() }
            cont.invokeOnCancellation { runCatching { client.closeBlocking() } }
        }
    }

    fun send(message: SignalMessage) {
        if (client.isOpen) client.send(SignalCodec.encode(message))
    }

    fun close() {
        stopping = true
        runCatching { client.closeBlocking() }
        incoming.close()
    }
}
