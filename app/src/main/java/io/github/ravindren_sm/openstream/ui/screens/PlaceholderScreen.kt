package io.github.ravindren_sm.openstream.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.nav.Route
import io.github.ravindren_sm.openstream.nav.RouteEntry
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens

/** The focus ID a screen takes when it appears and nothing was remembered (per the prototype's focus table). */
fun initialFocusId(route: Route): String = "card-0"

/**
 * Stand-in for every screen until it is built (M3 and later). It exists to exercise the shell:
 * focus ring, D-pad movement, "open a detail" and Back restoring the exact card.
 * No provider data is shown.
 */
@Composable
fun PlaceholderScreen(
    entry: RouteEntry,
    memory: FocusMemory,
    onOpenDetail: (Route, Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S3),
        verticalArrangement = Arrangement.spacedBy(Spacing.S3),
    ) {
        Text(entry.route.label, style = MaterialTheme.typography.headlineMedium, color = Tokens.TextPrimary)
        Text(
            "${entry.route.path} (placeholder: this screen is built in a later milestone)",
            style = MaterialTheme.typography.bodyLarge,
            color = Tokens.TextSecondary,
        )
        val detail = detailRouteFor(entry.route)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
            repeat(5) { i ->
                OsFocusable(
                    id = "card-$i",
                    memory = memory,
                    variant = FocusVariant.CARD,
                    onSelect = { detail?.let { onOpenDetail(it, mapOf("item" to "$i")) } },
                ) { _ ->
                    Column(Modifier.width(200.dp).height(300.dp).padding(Spacing.S2)) {
                        Text("Sample ${i + 1}", style = MaterialTheme.typography.bodyLarge, color = Tokens.TextPrimary)
                        Text(
                            if (detail != null) "Select to open" else "No detail screen",
                            style = MaterialTheme.typography.bodyLarge, color = Tokens.TextSecondary,
                        )
                    }
                }
            }
        }
        if (entry.params.isNotEmpty()) {
            Text("Opened from sample ${(entry.params["item"]?.toIntOrNull() ?: 0) + 1}. Back returns to that card.",
                style = MaterialTheme.typography.bodyLarge, color = Tokens.StatusSuccess)
        }
    }
}

/** Which screen a card opens, for the placeholder demo. */
private fun detailRouteFor(route: Route): Route? = when (route) {
    Route.MOVIES -> Route.MOVIE_DETAIL
    Route.SHOWS -> Route.SHOW_DETAIL
    Route.HOME, Route.FAVORITES, Route.SEARCH -> Route.MOVIE_DETAIL
    Route.LIVE_TV -> Route.LIVE_PLAYER
    else -> null
}
