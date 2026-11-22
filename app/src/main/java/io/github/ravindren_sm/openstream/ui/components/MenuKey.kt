package io.github.ravindren_sm.openstream.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** The mark shown on a favorite title or channel. */
const val HEART = "♥"

/** Runs [action] when the remote's Menu key is released while this element (or a child) has focus. The key is consumed. */
fun Modifier.onMenuKey(action: () -> Unit): Modifier = onPreviewKeyEvent { e ->
    if (e.key != Key.Menu) false
    else { if (e.type == KeyEventType.KeyUp) action(); true }
}
