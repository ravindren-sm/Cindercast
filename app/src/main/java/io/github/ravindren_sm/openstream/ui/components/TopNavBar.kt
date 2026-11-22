package io.github.ravindren_sm.openstream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.nav.Route
import io.github.ravindren_sm.openstream.nav.tabFocusId
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens

/**
 * Top tab bar: Home, Live TV, TV Guide, Movies, Shows, Search, Favorites, Settings.
 * The active tab has an accent underline. Each tab's focus ID is [tabFocusId], which is also what
 * the back stack restores after a tab switch.
 */
@Composable
fun TopNavBar(
    activeTab: Route?,
    memory: FocusMemory,
    onSelectTab: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            // Each tab has S2 of inner padding, so shrink the bar's margin by that much to line the
            // tab text up with the page content at the safe-area edge.
            .padding(horizontal = Spacing.SafeHorizontal - Spacing.S2, vertical = Spacing.S2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Route.TABS.forEach { tab ->
            OsFocusable(
                id = tabFocusId(tab),
                memory = memory,
                variant = FocusVariant.TAB,
                onSelect = { onSelectTab(tab) },
            ) { focused ->
                Column(Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1)) {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (focused || tab == activeTab) Tokens.TextPrimary else Tokens.TextSecondary,
                        maxLines = 1,
                        softWrap = false,
                    )
                    // Active-tab underline (accent), reserved space when inactive so tabs do not jump.
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .height(3.dp)
                            .width(if (tab == activeTab) 48.dp else 0.dp)
                            .background(Tokens.AccentPrimary),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Clock()
    }
}

/** The time of day, refreshed on the minute. Uses the device's own 12 or 24 hour setting. */
@Composable
private fun Clock() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(60_000 - now % 60_000) } }
    val text = remember(now) { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(now)) }
    Text(text, style = Tokens.BodyLarge, color = Tokens.TextSecondary, modifier = Modifier.padding(end = Spacing.S2))
}
