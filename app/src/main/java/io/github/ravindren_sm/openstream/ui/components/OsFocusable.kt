package io.github.ravindren_sm.openstream.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.FocusStyle
import io.github.ravindren_sm.openstream.ui.theme.Radii
import io.github.ravindren_sm.openstream.ui.theme.Tokens

/** Visual treatment when focused (variants from the prototype's TvFocusable). */
enum class FocusVariant { CARD, ROW, BUTTON, TAB }

/**
 * The one focusable building block. Gives every element the same focus behavior:
 * - a stable [id] registered in [memory] so Back can restore focus to exactly this element;
 * - a 3 dp high-contrast ring (and, for cards, a slight scale-up) when focused;
 * - Enter / D-pad center runs [onSelect] (clickable handles the TV remote's select key).
 *
 * [content] receives whether the element is focused, so callers can restyle their content.
 */
@Composable
fun OsFocusable(
    id: String,
    memory: FocusMemory,
    modifier: Modifier = Modifier,
    variant: FocusVariant = FocusVariant.CARD,
    onSelect: () -> Unit = {},
    /** For composites (such as a text field with two modes) that register one requester for several nodes. */
    ownedRequester: FocusRequester? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val requester = ownedRequester ?: remember(id) { memory.requesterFor(id) }
    if (ownedRequester == null) DisposableEffect(id) { onDispose { memory.release(id) } }

    val scale by animateFloatAsState(if (focused && variant == FocusVariant.CARD) FocusStyle.CardScale else 1f, label = "focusScale")
    val shape: Shape = RoundedCornerShape(if (variant == FocusVariant.TAB) Radii.Sm else Radii.Md)
    val background = when {
        !focused -> if (variant == FocusVariant.ROW || variant == FocusVariant.CARD) Tokens.Surface1 else Color.Transparent
        else -> Tokens.Surface3
    }

    Box(
        modifier
            .testTag(id)                       // exposed as a resource id (see MainActivity) so UI tests and the TV crawler see stable ids
            .focusRequester(requester)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) memory.focusedId = id
            }
            .zIndex(if (focused) 1f else 0f)   // drawn above its neighbours while scaled up, so they do not cover its edges
            .scale(scale)
            .background(background, shape)
            .border(if (focused) BorderStroke(FocusStyle.RingWidth, Tokens.FocusRing) else BorderStroke(0.dp, Color.Transparent), shape)
            .clickable(onClick = onSelect),
    ) { content(focused) }
}
