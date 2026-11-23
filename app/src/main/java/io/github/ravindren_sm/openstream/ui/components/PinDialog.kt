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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import kotlinx.coroutines.launch

private fun pinKeyId(label: String) = "pin-$label"

/**
 * A four-digit PIN pad for the D-pad: ten digit buttons, Delete and Cancel (the number keys of a remote work too). Dots show how
 * many digits are in; the digits are never shown. With [confirmTwice] (choosing a new PIN) it asks for the PIN a second time and
 * hands over only a matching pair. [onSubmit] checks the PIN and returns text to show when it is refused, or null when accepted
 * (the dialog then calls [onDone]). Back and Cancel call [onCancel].
 */
@Composable
fun PinDialog(
    title: String, memory: FocusMemory, confirmTwice: Boolean = false,
    onSubmit: suspend (String) -> String?, onDone: () -> Unit, onCancel: () -> Unit,
) {
    var entry by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }       // the first entry, while confirming
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { withFrameNanos { }; memory.tryFocus(pinKeyId("5")) }
    BackHandler { onCancel() }

    fun submit(pin: String) {
        if (confirmTwice && first == null) { first = pin; entry = ""; message = null; return }
        if (confirmTwice && first != pin) { first = null; entry = ""; message = "The two entries differ. Start again."; return }
        busy = true
        scope.launch {
            val refused = onSubmit(pin)
            busy = false
            if (refused == null) onDone() else { entry = ""; first = null; message = refused }
        }
    }
    fun add(d: Char) { if (busy || entry.length >= 4) return; entry += d; if (entry.length == 4) submit(entry) }

    Box(Modifier.fillMaxSize().trapFocus().background(Tokens.BgCanvas.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(520.dp).background(Tokens.Surface1).padding(Spacing.S3).onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) {
                    val digit = when (e.key) {
                        Key.Zero, Key.NumPad0 -> '0'; Key.One, Key.NumPad1 -> '1'; Key.Two, Key.NumPad2 -> '2'; Key.Three, Key.NumPad3 -> '3'
                        Key.Four, Key.NumPad4 -> '4'; Key.Five, Key.NumPad5 -> '5'; Key.Six, Key.NumPad6 -> '6'; Key.Seven, Key.NumPad7 -> '7'
                        Key.Eight, Key.NumPad8 -> '8'; Key.Nine, Key.NumPad9 -> '9'; else -> null
                    }
                    if (digit != null) { add(digit); return@onPreviewKeyEvent true }
                }
                false
            },
            verticalArrangement = Arrangement.spacedBy(Spacing.S1), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = Tokens.TitleMedium, color = Tokens.TextPrimary)
            Text(if (confirmTwice && first != null) "Enter it again" else "Enter four digits", style = Tokens.Body, color = Tokens.TextSecondary)
            Text((0 until 4).joinToString("  ") { if (it < entry.length) "●" else "○" }, style = Tokens.TitleLarge, color = Tokens.TextPrimary)
            Text(message ?: " ", style = Tokens.Body, color = Tokens.StatusError, maxLines = 2)
            listOf("123", "456", "789").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S1)) { row.forEach { PadKey(it.toString(), memory) { add(it) } } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                PadKey("Del", memory) { if (!busy) entry = entry.dropLast(1) }
                PadKey("0", memory) { add('0') }
                PadKey("Cancel", memory) { onCancel() }
            }
        }
    }
}

@Composable
private fun PadKey(label: String, memory: FocusMemory, onPress: () -> Unit) {
    OsFocusable(id = pinKeyId(label.take(6)), memory = memory, variant = FocusVariant.ROW, modifier = Modifier.width(140.dp), onSelect = onPress) { _ ->
        Box(Modifier.width(140.dp).padding(vertical = Spacing.S1), contentAlignment = Alignment.Center) {
            Text(label, style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1)
        }
    }
}
