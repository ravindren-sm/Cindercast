package io.github.ravindren_sm.openstream.ui.components

import androidx.activity.compose.BackHandler
import io.github.ravindren_sm.openstream.ui.focus.trapFocus
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.TitleMerge
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.provider.VodItem
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens

private fun pickerRowId(i: Int) = "version-$i"

/**
 * Choose between the versions of a title (quality, language). Up and Down move, Back closes. [current] is marked.
 * Focus returns to [returnFocusId] when it closes.
 */
@Composable
fun VersionPicker(
    versions: List<VodItem>, current: VodItem?, memory: FocusMemory, returnFocusId: String?,
    onPick: (VodItem) -> Unit, onDismiss: () -> Unit,
) {
    var index by remember { mutableIntStateOf(versions.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)) }
    fun close() { onDismiss(); returnFocusId?.let { memory.tryFocus(it) } }
    LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; memory.tryFocus(pickerRowId(index)) }
    BackHandler { close() }
    Box(Modifier.fillMaxSize().trapFocus().background(Tokens.BgCanvas.copy(alpha = 0.88f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(720.dp).background(Tokens.Surface1).padding(Spacing.S3).onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent e.key in setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)
                when (e.key) {
                    Key.DirectionDown -> { index = (index + 1).coerceAtMost(versions.lastIndex); memory.tryFocus(pickerRowId(index)); true }
                    Key.DirectionUp -> { index = (index - 1).coerceAtLeast(0); memory.tryFocus(pickerRowId(index)); true }
                    Key.DirectionLeft, Key.DirectionRight -> true
                    else -> false
                }
            },
            verticalArrangement = Arrangement.spacedBy(Spacing.S1),
        ) {
            Text("Choose a version", style = Tokens.TitleMedium, color = Tokens.TextPrimary)
            versions.forEachIndexed { i, v ->
                OsFocusable(id = pickerRowId(i), memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth(), onSelect = { onPick(v); returnFocusId?.let { memory.tryFocus(it) } }) { _ ->
                    Column(Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1)) {
                        Text(TitleMerge.versionLabel(v, i) + if (v.id == current?.id) "  (current)" else "", style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1)
                        Text(PortalText.decodeEntities(v.name), style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1)
                    }
                }
            }
        }
    }
}
