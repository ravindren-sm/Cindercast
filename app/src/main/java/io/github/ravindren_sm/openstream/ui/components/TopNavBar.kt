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
import androidx.compose.runtime.Composable
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
            .padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S2),
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
    }
}
