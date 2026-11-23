package io.github.ravindren_sm.openstream.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.FocusStyle
import io.github.ravindren_sm.openstream.ui.theme.Radii
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens

/**
 * A single-line text field for the remote, with two modes (found necessary on the Fire TV: a text
 * field that merely receives focus makes Fire OS open its keyboard, which then swallows the D-pad):
 *
 * - **Display mode**: a focusable box showing the value (or a prompt). The D-pad passes over it freely.
 * - **Edit mode** (Select/Enter): a real text field takes focus and the TV's on-screen keyboard opens
 *   (it also works with the Alexa remote and the Fire TV phone app; hardware keyboards type directly).
 *   The keyboard's Next/Done, or moving focus away, returns to display mode.
 *
 * One focus ID covers both modes, registered once in [memory], so Back can restore focus to the field.
 * [value] is never logged by this component.
 */
@Composable
fun OsTextField(
    id: String,
    memory: FocusMemory,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    /** Focus ID that Down should move to, when the layout's nearest-neighbor choice would be wrong. */
    downId: String? = null,
    /** Focus ID that Up should move to, likewise (a full-width field otherwise picks the element nearest its centre). */
    upId: String? = null,
) {
    var editing by remember { mutableStateOf(false) }
    var hadFocusWhileEditing by remember { mutableStateOf(false) }
    var returnFocusToDisplay by remember { mutableStateOf(false) }
    var leftByDpad by remember { mutableStateOf(false) }
    val requester = remember(id) { memory.requesterFor(id) }
    DisposableEffect(id) { onDispose { memory.release(id) } }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(Radii.Md)
    val downTarget = Modifier.focusProperties {
        if (downId != null) down = memory.requesterFor(downId)
        if (upId != null) up = memory.requesterFor(upId)
    }

    // Entering edit mode: focus the real field and open the keyboard once it is on screen.
    LaunchedEffect(editing) {
        if (editing) {
            withFrameNanos { }
            try { requester.requestFocus() } catch (_: IllegalStateException) { }
            keyboard?.show()
        } else if (returnFocusToDisplay) {
            returnFocusToDisplay = false
            withFrameNanos { }
            memory.tryFocus(id)
        }
    }

    fun finishEditing(moveOn: Boolean) {
        keyboard?.hide()
        memory.noteEditEnded()
        editing = false
        hadFocusWhileEditing = false
        if (moveOn) focusManager.moveFocus(FocusDirection.Down) else returnFocusToDisplay = true
    }

    // While editing, Back only ends the edit (closes the keyboard); it must not also leave the screen.
    BackHandler(enabled = editing) { finishEditing(moveOn = false) }

    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Tokens.TextPrimary)
        hint?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Tokens.TextSecondary) }
        if (!editing) {
            OsFocusable(
                id = id, memory = memory, ownedRequester = requester,
                modifier = downTarget.padding(top = Spacing.S1).fillMaxWidth(),
                variant = FocusVariant.ROW,
                onSelect = { editing = true },
            ) { focused ->
                Text(
                    if (value.isEmpty()) "Select to type" else value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isEmpty()) Tokens.TextSecondary.copy(alpha = 0.6f) else Tokens.TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S2),
                )
            }
        } else {
            Box(
                Modifier
                    .padding(top = Spacing.S1)
                    .fillMaxWidth()
                    .background(Tokens.Surface3, shape)
                    .border(BorderStroke(FocusStyle.RingWidth, Tokens.AccentPrimary), shape)
                    .padding(horizontal = Spacing.S2, vertical = Spacing.S2),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Tokens.TextPrimary),
                    cursorBrush = SolidColor(Tokens.AccentPrimary),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrect = false),
                    keyboardActions = KeyboardActions(
                        onNext = { finishEditing(moveOn = true) },
                        onDone = { finishEditing(moveOn = false); onDone() },
                    ),
                    modifier = downTarget
                        .fillMaxWidth()
                        .focusRequester(requester)
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && (e.key == Key.DirectionUp || e.key == Key.DirectionDown)) leftByDpad = true
                            false
                        }
                        .onFocusChanged { state ->
                            if (state.isFocused) { hadFocusWhileEditing = true; memory.focusedId = id }
                            // Focus moved elsewhere (for example Up/Down on the remote): back to display mode.
                            else if (hadFocusWhileEditing) {
                                memory.noteEditEnded(); editing = false; hadFocusWhileEditing = false
                                // Focus left without the D-pad (the keyboard was closed with Back): come back to this field.
                                if (!leftByDpad) returnFocusToDisplay = true
                                leftByDpad = false
                            }
                        },
                )
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Tokens.StatusError, modifier = Modifier.padding(top = Spacing.S1)) }
    }
}
