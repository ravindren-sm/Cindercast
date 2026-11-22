package io.github.ravindren_sm.openstream.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

class Choice(val id: String, val label: String)

private fun choiceId(i: Int) = "choice-$i"

/**
 * A list to choose from. With [multi], Select toggles an entry (a tick shows the chosen ones) and the list stays open; otherwise
 * Select picks and closes. The first row, Done, closes. Left and Right do nothing so the D-pad cannot wander to the page behind,
 * Back closes, and focus returns to [returnFocusId]. [note] explains where the choices come from.
 */
@Composable
fun ChoicePicker(
    title: String, options: List<Choice>, selected: Set<String>, multi: Boolean, note: String?, memory: FocusMemory,
    returnFocusId: String?, onToggle: (String) -> Unit, onClose: () -> Unit,
) {
    fun close() { onClose(); returnFocusId?.let { memory.tryFocus(it) } }
    LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; memory.tryFocus(choiceId(0)) }
    BackHandler { close() }
    Box(Modifier.fillMaxSize().background(Tokens.BgCanvas.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(760.dp).background(Tokens.Surface1).padding(Spacing.S3).onPreviewKeyEvent { e ->
                if (e.key == Key.DirectionLeft || e.key == Key.DirectionRight) true else false
            },
            verticalArrangement = Arrangement.spacedBy(Spacing.S1),
        ) {
            Text(title, style = Tokens.TitleMedium, color = Tokens.TextPrimary)
            note?.let { Text(it, style = Tokens.Body, color = Tokens.TextSecondary) }
            LazyColumn(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                itemsIndexed(listOf(Choice("", "Done")) + options, key = { i, c -> "$i-${c.id}" }) { i, c ->
                    val isDone = i == 0
                    val chosen = !isDone && c.id in selected
                    OsFocusable(
                        id = choiceId(i), memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth(),
                        onSelect = { if (isDone) close() else { onToggle(c.id); if (!multi) close() } },
                    ) { _ ->
                        Text(
                            (if (chosen) "✓  " else if (multi && !isDone) "    " else "") + c.label,
                            style = Tokens.BodyLarge, color = if (chosen) Tokens.AccentPrimary else Tokens.TextPrimary, maxLines = 1,
                            modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1),
                        )
                    }
                }
            }
        }
    }
}
