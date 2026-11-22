package io.github.ravindren_sm.openstream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.PosterUrl
import io.github.ravindren_sm.openstream.nav.Route
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.profiles.ContinueTile
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.components.Poster
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens

fun continueFocusId(index: Int) = "home-cw-$index"
fun doorFocusId(index: Int) = "home-door-$index"

/** The four doors every profile has. Search has no screen yet, so it opens the placeholder like the tab does. */
private val DOORS = listOf(Route.LIVE_TV to "Live TV", Route.MOVIES to "Movies", Route.SHOWS to "Shows", Route.SEARCH to "Search")

/**
 * Home: "Continue watching" first (what this profile has started and not finished, with progress and time left),
 * then the doors into the app. With nothing watched yet the doors become the whole page, with a short invitation.
 * [tiles] is null while the history is still being read.
 */
@Composable
fun HomeScreen(
    tiles: List<ContinueTile>?,
    portalUrl: String,
    memory: FocusMemory,
    onResume: (ContinueTile) -> Unit,
    onDoor: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S2),
        verticalArrangement = Arrangement.spacedBy(Spacing.S3),
    ) {
        if (!tiles.isNullOrEmpty()) {
            Text("Continue watching", style = Tokens.TitleMedium, color = Tokens.TextPrimary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S3), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.S1)) {
                itemsIndexed(tiles, key = { _, t -> t.resumeKey }) { i, tile -> ContinueCard(i, tile, portalUrl, memory) { onResume(tile) } }
            }
        } else if (tiles != null) {
            Text("Welcome to OpenStream", style = Tokens.TitleLarge, color = Tokens.TextPrimary)
            Text("Pick something to watch. Titles you start will appear here so you can carry on later.", style = Tokens.Body, color = Tokens.TextSecondary)
        }
        Text("Browse", style = Tokens.TitleMedium, color = Tokens.TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3), modifier = Modifier.fillMaxWidth()) {
            DOORS.forEachIndexed { i, (route, label) ->
                OsFocusable(id = doorFocusId(i), memory = memory, variant = FocusVariant.CARD, modifier = Modifier.weight(1f), onSelect = { onDoor(route) }) { _ ->
                    Box(Modifier.fillMaxWidth().height(if (tiles.isNullOrEmpty()) 160.dp else 100.dp).background(Tokens.Surface1), contentAlignment = Alignment.Center) {
                        Text(label, style = Tokens.TitleMedium, color = Tokens.TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(index: Int, tile: ContinueTile, portalUrl: String, memory: FocusMemory, onSelect: () -> Unit) {
    OsFocusable(id = continueFocusId(index), memory = memory, variant = FocusVariant.CARD, modifier = Modifier.width(220.dp), onSelect = onSelect) { _ ->
        Column(Modifier.background(Tokens.Surface1)) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                Poster(PosterUrl.resolve(tile.posterUri, portalUrl), Modifier.fillMaxSize(), targetWidthPx = 300)
                // Progress along the bottom edge of the poster.
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(6.dp).background(Tokens.SurfaceEmpty)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(tile.progress).background(Tokens.AccentPrimary))
                }
            }
            Column(Modifier.padding(Spacing.S1)) {
                Text(PortalText.decodeEntities(tile.title), style = Tokens.Body, color = Tokens.TextPrimary, maxLines = 1)
                tile.subtitle?.let { Text(PortalText.decodeEntities(it), style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1) }
                tile.timeLeft?.let { Text(it, style = Tokens.Body, color = Tokens.AccentPrimary, maxLines = 1) }
            }
        }
    }
}
