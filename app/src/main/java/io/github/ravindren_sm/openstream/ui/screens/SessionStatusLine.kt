package io.github.ravindren_sm.openstream.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.SessionStatus
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens

/** The portal connection state, in small type for the bottom-left corner of the screen. Hidden while no portal is configured or the app is idle. */
@Composable
fun SessionStatusLine(status: SessionStatus, modifier: Modifier = Modifier) {
    val (text, color) = when (status) {
        SessionStatus.Idle -> return
        SessionStatus.Connecting -> "Connecting to the portal…" to Tokens.TextSecondary
        is SessionStatus.Ready -> ("Portal connected" + (status.expiry?.let { " · account active until $it" } ?: "")) to Tokens.StatusSuccess
        SessionStatus.Degraded -> "Portal connection check failed; trying again shortly." to Tokens.StatusWarning
        is SessionStatus.Failed -> status.message to Tokens.StatusError
    }
    Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 24.sp), color = color,
        modifier = modifier.padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.SafeVertical))
}
