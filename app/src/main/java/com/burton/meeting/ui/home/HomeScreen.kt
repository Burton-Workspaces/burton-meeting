package com.burton.meeting.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.burton.meeting.domain.CallMode
import com.burton.meeting.domain.NearbyMeeting
import com.burton.meeting.domain.RoomCodes
import com.burton.meeting.ui.components.BurtonModalSheet
import com.burton.meeting.ui.components.FullScreenModal
import com.burton.meeting.ui.components.MeetingsSkeleton
import com.burton.meeting.ui.settings.SettingsModal
import com.burton.meeting.ui.theme.BurtonCharcoal
import com.burton.meeting.ui.theme.BurtonElevated
import com.burton.meeting.ui.theme.BurtonIvory
import com.burton.meeting.ui.theme.BurtonMute
import com.burton.meeting.ui.theme.BurtonSand
import com.burton.meeting.ui.theme.BurtonVoid

@Composable
fun HomeScreen(
    onNeedPermissions: (video: Boolean, onGranted: () -> Unit) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val nearby by viewModel.nearby.collectAsStateWithLifecycle()
    val displayName by viewModel.displayName.collectAsStateWithLifecycle("")
    val error by viewModel.error.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf<NearbyMeeting?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Meetings",
                style = MaterialTheme.typography.headlineLarge,
                color = BurtonIvory,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = BurtonIvory)
            }
        }
        Text(
            text = when {
                nearby.isEmpty() -> "Looking for meetings on this Wi-Fi"
                nearby.size == 1 -> "1 meeting on this network"
                else -> "${nearby.size} meetings on this network"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = BurtonMute,
        )
        Spacer(Modifier.height(16.dp))
        if (nearby.isEmpty()) {
            MeetingsSkeleton()
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = true),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(nearby, key = { it.code }) { meeting ->
                    MeetingCard(meeting = meeting, onClick = { joining = meeting })
                }
            }
        }
        if (nearby.isEmpty()) Spacer(Modifier.weight(1f))
        if (error != null) {
            Text(error ?: "", color = BurtonIvory, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = viewModel::clearError) {
                Text("Dismiss", color = BurtonSand)
            }
        }
        Button(
            onClick = { showCreate = true },
            colors = ButtonDefaults.buttonColors(containerColor = BurtonIvory, contentColor = BurtonVoid),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start a meeting")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showJoin = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        ) {
            Text("Join with a code", color = BurtonIvory)
        }
    }

    if (showSettings) {
        SettingsModal(
            displayName = displayName,
            onDisplayNameChange = viewModel::setDisplayName,
            onDismiss = { showSettings = false },
        )
    }
    if (showCreate) {
        StartMeetingModal(
            onDismiss = { showCreate = false },
            onStart = { name, mode ->
                onNeedPermissions(mode == CallMode.VIDEO) {
                    showCreate = false
                    viewModel.startMeeting(name, mode)
                }
            },
        )
    }
    if (showJoin) {
        JoinCodeModal(
            onDismiss = { showJoin = false },
            onJoin = { code, mode ->
                onNeedPermissions(mode == CallMode.VIDEO) {
                    showJoin = false
                    viewModel.joinByCode(code, mode)
                }
            },
        )
    }
    joining?.let { meeting ->
        JoinNearbySheet(
            meeting = meeting,
            onDismiss = { joining = null },
            onJoin = { mode ->
                onNeedPermissions(mode == CallMode.VIDEO && meeting.mode == CallMode.VIDEO) {
                    val chosen = meeting
                    joining = null
                    viewModel.joinMeeting(chosen, mode)
                }
            },
        )
    }
}

@Composable
private fun MeetingCard(meeting: NearbyMeeting, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BurtonCharcoal, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = if (meeting.mode == CallMode.VIDEO) Icons.Rounded.Videocam else Icons.Rounded.Call,
            contentDescription = meeting.mode.label,
            tint = BurtonSand,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(meeting.name, style = MaterialTheme.typography.titleLarge, color = BurtonIvory)
            Text(
                "${meeting.mode.label} · ${meeting.code}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = BurtonMute,
            )
        }
    }
}

@Composable
private fun StartMeetingModal(
    onDismiss: () -> Unit,
    onStart: (String, CallMode) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(CallMode.VIDEO) }
    FullScreenModal(
        onDismiss = onDismiss,
        title = "Start a meeting",
        actionLabel = "Start",
        onAction = { onStart(name.ifBlank { "Meeting" }, mode) },
    ) {
        Spacer(Modifier.height(20.dp))
        LabeledField(label = "Name", value = name, onValueChange = { name = it }, placeholder = "Meeting")
        Spacer(Modifier.height(16.dp))
        Text("Mode", style = MaterialTheme.typography.labelSmall, color = BurtonMute)
        Spacer(Modifier.height(8.dp))
        ModePicker(mode = mode, videoEnabled = true, onChange = { mode = it })
    }
}

@Composable
private fun JoinCodeModal(
    onDismiss: () -> Unit,
    onJoin: (String, CallMode) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(CallMode.VIDEO) }
    val normalized = RoomCodes.normalize(code)
    FullScreenModal(
        onDismiss = onDismiss,
        title = "Join with a code",
        actionLabel = "Join",
        actionEnabled = RoomCodes.isValid(code),
        onAction = { onJoin(normalized, mode) },
    ) {
        Spacer(Modifier.height(20.dp))
        LabeledField(
            label = "Code",
            value = code,
            onValueChange = { code = RoomCodes.normalize(it).take(RoomCodes.LENGTH) },
            placeholder = "XXXXXX",
        )
        Spacer(Modifier.height(16.dp))
        Text("Join as", style = MaterialTheme.typography.labelSmall, color = BurtonMute)
        Spacer(Modifier.height(8.dp))
        ModePicker(mode = mode, videoEnabled = true, onChange = { mode = it })
        Spacer(Modifier.height(12.dp))
        Text(
            "The host must be on the same Wi-Fi. Voice still works in a video meeting if you turn the camera off.",
            style = MaterialTheme.typography.bodyMedium,
            color = BurtonMute,
        )
    }
}

@Composable
private fun JoinNearbySheet(
    meeting: NearbyMeeting,
    onDismiss: () -> Unit,
    onJoin: (CallMode) -> Unit,
) {
    var mode by remember {
        mutableStateOf(if (meeting.mode == CallMode.VOICE) CallMode.VOICE else CallMode.VIDEO)
    }
    BurtonModalSheet(onDismiss = onDismiss) {
        Text(meeting.name, style = MaterialTheme.typography.headlineMedium, color = BurtonIvory)
        Spacer(Modifier.height(4.dp))
        Text(
            "${meeting.mode.label} · ${meeting.code}",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = BurtonMute,
        )
        Spacer(Modifier.height(16.dp))
        Text("Join as", style = MaterialTheme.typography.labelSmall, color = BurtonMute)
        Spacer(Modifier.height(8.dp))
        ModePicker(
            mode = mode,
            videoEnabled = meeting.mode == CallMode.VIDEO,
            onChange = { mode = it },
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onJoin(mode) },
            colors = ButtonDefaults.buttonColors(containerColor = BurtonIvory, contentColor = BurtonVoid),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Join")
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BurtonCharcoal, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = BurtonMute)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = BurtonIvory),
            cursorBrush = SolidColor(BurtonIvory),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isBlank()) {
                    Text(placeholder, color = BurtonMute, style = MaterialTheme.typography.titleLarge)
                }
                inner()
            },
        )
    }
}

@Composable
fun ModePicker(
    mode: CallMode,
    videoEnabled: Boolean,
    onChange: (CallMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ModeChip(
            label = "Video",
            selected = mode == CallMode.VIDEO,
            enabled = videoEnabled,
            modifier = Modifier.weight(1f),
            onClick = { onChange(CallMode.VIDEO) },
        )
        ModeChip(
            label = "Voice only",
            selected = mode == CallMode.VOICE,
            enabled = true,
            modifier = Modifier.weight(1f),
            onClick = { onChange(CallMode.VOICE) },
        )
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background = when {
        selected -> BurtonIvory
        else -> BurtonElevated
    }
    val foreground = when {
        selected -> BurtonVoid
        enabled -> BurtonIvory
        else -> BurtonMute
    }
    Text(
        text = label,
        color = foreground,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .background(background, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        textAlign = TextAlign.Center,
    )
}
