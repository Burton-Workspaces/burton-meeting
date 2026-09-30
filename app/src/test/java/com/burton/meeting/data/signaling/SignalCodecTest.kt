package com.burton.meeting.data.signaling

import com.burton.meeting.domain.CallMode
import com.burton.meeting.domain.PeerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalCodecTest {
    @Test
    fun roundTripsHello() {
        val original = SignalMessage.Hello(
            peerId = "p1",
            displayName = "Kitchen",
            mode = CallMode.VOICE,
            code = "AB2DEF",
            muted = true,
            cameraOn = false,
        )
        val parsed = SignalCodec.decode(SignalCodec.encode(original))
        assertEquals(original, parsed)
    }

    @Test
    fun roundTripsWelcomeWithPeers() {
        val original = SignalMessage.Welcome(
            peerId = "host",
            roomName = "Standup",
            roomCode = "AB2DEF",
            roomMode = CallMode.VIDEO,
            peers = listOf(
                PeerInfo("a", "Ada", CallMode.VIDEO, muted = false, cameraOn = true),
                PeerInfo("b", "Bea", CallMode.VOICE, muted = true, cameraOn = false),
            ),
        )
        val parsed = SignalCodec.decode(SignalCodec.encode(original)) as SignalMessage.Welcome
        assertEquals(original, parsed)
    }

    @Test
    fun roundTripsIceAndSdp() {
        val offer = SignalMessage.Offer("a", "b", "v=0\r\no=- 1 1 IN IP4 127.0.0.1")
        val ice = SignalMessage.Ice("a", "b", "candidate:1 1 UDP 1 10.0.0.4 9 typ host", "0", 0)
        assertEquals(offer, SignalCodec.decode(SignalCodec.encode(offer)))
        assertEquals(ice, SignalCodec.decode(SignalCodec.encode(ice)))
    }

    @Test
    fun unknownTypeIsError() {
        val parsed = SignalCodec.decode("""{"type":"nope"}""")
        assertTrue(parsed is SignalMessage.Error)
    }
}
