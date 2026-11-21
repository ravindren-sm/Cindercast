package io.github.ravindren_sm.openstream.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens

/**
 * A text button built on [OsFocusable]. A disabled button still takes focus (so the D-pad does not
 * skip over it and the user can see it) but ignores Select and is drawn dimmed.
 */
@Composable
fun OsButton(
    id: String,
    memory: FocusMemory,
    text: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OsFocusable(id = id, memory = memory, modifier = modifier, variant = FocusVariant.BUTTON, onSelect = { if (enabled) onSelect() }) { focused ->
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                !enabled -> Tokens.TextSecondary.copy(alpha = 0.5f)
                focused -> Tokens.TextPrimary
                else -> Tokens.AccentPrimary
            },
            modifier = Modifier.padding(horizontal = Spacing.S3, vertical = Spacing.S2),
        )
    }
}
