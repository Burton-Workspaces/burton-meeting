package com.burton.meeting.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.burton.meeting.BuildConfig
import com.burton.meeting.report.BurtonIssues
import com.burton.meeting.ui.components.FullScreenModal
import com.burton.meeting.ui.theme.BurtonCharcoal
import com.burton.meeting.ui.theme.BurtonIvory
import com.burton.meeting.ui.theme.BurtonMute

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsModal(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    FullScreenModal(
        onDismiss = onDismiss,
        title = "Settings",
    ) {
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BurtonCharcoal, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Text("Display name", style = MaterialTheme.typography.labelSmall, color = BurtonMute)
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(color = BurtonIvory),
                cursorBrush = SolidColor(BurtonIvory),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (displayName.isBlank()) {
                        Text("Your name in meetings", color = BurtonMute, style = MaterialTheme.typography.titleLarge)
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        val context = LocalContext.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BurtonCharcoal, RoundedCornerShape(18.dp))
                .combinedClickable(
                    onClick = {},
                    onLongClick = { BurtonIssues.openNewIssue(context) },
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("About", style = MaterialTheme.typography.labelSmall, color = BurtonMute)
                Spacer(Modifier.height(4.dp))
                Text("Burton Meeting", style = MaterialTheme.typography.titleLarge, color = BurtonIvory)
            }
            Text(
                text = BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodyLarge,
                color = BurtonMute,
            )
        }
    }
}
