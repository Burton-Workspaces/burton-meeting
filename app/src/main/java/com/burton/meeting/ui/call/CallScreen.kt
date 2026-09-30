package com.burton.meeting.ui.call

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.burton.meeting.domain.CallMode
import com.burton.meeting.domain.CallSession
import com.burton.meeting.domain.CallStatus
import com.burton.meeting.domain.RemotePeer
import com.burton.meeting.ui.components.VideoPane
import com.burton.meeting.ui.theme.BurtonBlack
import com.burton.meeting.ui.theme.BurtonCharcoal
import com.burton.meeting.ui.theme.BurtonDanger
import com.burton.meeting.ui.theme.BurtonElevated
import com.burton.meeting.ui.theme.BurtonIvory
import com.burton.meeting.ui.theme.BurtonMute
import com.burton.meeting.ui.theme.BurtonSand
import org.webrtc.EglBase
import org.webrtc.VideoTrack

@Composable
fun CallScreen(
    viewModel: CallViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val current = session ?: return
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BurtonBlack)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(current.roomName, style = MaterialTheme.typography.headlineMedium, color = BurtonIvory)
                Text(
                    text = "${current.roomMode.label} · ${current.roomCode} · ${current.participantCount}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = BurtonMute,
                    modifier = Modifier.clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Meeting code", current.roomCode))
                    },
                )
            }
        }
        if (current.status == CallStatus.FAILED && current.error != null) {
            Text(
                current.error,
                color = BurtonIvory,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            ParticipantGrid(session = current)
        }
        CallControls(
            session = current,
            onMute = { viewModel.setMuted(!current.muted) },
            onCamera = { viewModel.setCameraEnabled(!current.cameraOn) },
            onSpeaker = { viewModel.setSpeakerOn(!current.speakerOn) },
            onLeave = viewModel::leave,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ParticipantGrid(session: CallSession) {
    val egl = session.eglContext as? EglBase.Context
    val tiles = buildList {
        add(
            Tile(
                id = session.localPeerId,
                name = "${session.localDisplayName} (you)",
                muted = session.muted,
                cameraOn = session.cameraOn,
                video = session.localVideoTrack as? VideoTrack,
                mirror = true,
            ),
        )
        session.peers.forEach { peer -> add(peer.toTile()) }
    }
    val columns = if (tiles.size <= 1) 1 else 2
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tiles, key = { it.id }) { tile ->
            ParticipantTile(tile = tile, eglContext = egl)
        }
    }
}

@Composable
private fun ParticipantTile(tile: Tile, eglContext: EglBase.Context?) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(BurtonCharcoal),
    ) {
        if (tile.cameraOn && tile.video != null && eglContext != null) {
            VideoPane(
                track = tile.video,
                eglContext = eglContext,
                mirror = tile.mirror,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(BurtonElevated, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tile.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = BurtonSand,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(BurtonBlack.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                tile.name,
                color = BurtonIvory,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (tile.muted) {
                Icon(Icons.Rounded.MicOff, contentDescription = "Muted", tint = BurtonIvory, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CallControls(
    session: CallSession,
    onMute: () -> Unit,
    onCamera: () -> Unit,
    onSpeaker: () -> Unit,
    onLeave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlButton(
            icon = if (session.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
            label = if (session.muted) "Unmute" else "Mute",
            onClick = onMute,
        )
        if (session.cameraAllowed) {
            ControlButton(
                icon = if (session.cameraOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                label = if (session.cameraOn) "Camera" else "Voice",
                onClick = onCamera,
            )
        }
        ControlButton(
            icon = if (session.speakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
            label = "Speaker",
            onClick = onSpeaker,
        )
        IconButton(
            onClick = onLeave,
            modifier = Modifier
                .size(56.dp)
                .background(BurtonDanger, CircleShape),
        ) {
            Icon(Icons.Rounded.CallEnd, contentDescription = "Leave", tint = BurtonIvory)
        }
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .background(BurtonElevated, CircleShape),
    ) {
        Icon(icon, contentDescription = label, tint = BurtonIvory)
    }
}

private data class Tile(
    val id: String,
    val name: String,
    val muted: Boolean,
    val cameraOn: Boolean,
    val video: VideoTrack?,
    val mirror: Boolean,
)

private fun RemotePeer.toTile() = Tile(
    id = info.id,
    name = info.displayName,
    muted = info.muted,
    cameraOn = info.cameraOn && videoTrack != null,
    video = videoTrack as? VideoTrack,
    mirror = false,
)
