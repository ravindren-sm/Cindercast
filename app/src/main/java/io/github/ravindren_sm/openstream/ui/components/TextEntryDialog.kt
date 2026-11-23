package io.github.ravindren_sm.openstream.ui.components

import androidx.activity.compose.BackHandler
import io.github.ravindren_sm.openstream.ui.focus.trapFocus
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens

/** A small box with one text field, Save and Cancel (for a short value such as a key). The text is never logged. */
@Composable
fun TextEntryDialog(title: String, note: String, label: String, memory: FocusMemory, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { withFrameNanos { }; memory.tryFocus("entry-field") }
    BackHandler { onCancel() }
    Box(Modifier.fillMaxSize().trapFocus().background(Tokens.BgCanvas.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(900.dp).background(Tokens.Surface1).padding(Spacing.S3), verticalArrangement = Arrangement.spacedBy(Spacing.S2)) {
            Text(title, style = Tokens.TitleMedium, color = Tokens.TextPrimary)
            Text(note, style = Tokens.Body, color = Tokens.TextSecondary)
            OsTextField(id = "entry-field", memory = memory, value = text, onValueChange = { text = it }, label = label, modifier = Modifier.width(840.dp), downId = "entry-save")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
                OsFocusable(id = "entry-save", memory = memory, variant = FocusVariant.BUTTON, onSelect = { if (text.isNotBlank()) onSave(text.trim()) }) { _ ->
                    Text("Save", style = Tokens.BodyLarge, color = Tokens.TextPrimary, modifier = Modifier.padding(horizontal = Spacing.S3, vertical = Spacing.S1))
                }
                OsFocusable(id = "entry-cancel", memory = memory, variant = FocusVariant.BUTTON, onSelect = onCancel) { _ ->
                    Text("Cancel", style = Tokens.BodyLarge, color = Tokens.TextPrimary, modifier = Modifier.padding(horizontal = Spacing.S3, vertical = Spacing.S1))
                }
            }
        }
    }
}
