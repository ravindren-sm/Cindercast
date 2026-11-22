package io.github.ravindren_sm.openstream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.PosterUrl
import io.github.ravindren_sm.openstream.profiles.FavoritesState
import io.github.ravindren_sm.openstream.profiles.Kinds
import io.github.ravindren_sm.openstream.profiles.db.FavoriteEntity
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.components.Poster
import io.github.ravindren_sm.openstream.ui.components.onMenuKey
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens

fun favChannelFocusId(i: Int) = "fav-ch-$i"
fun favMovieFocusId(i: Int) = "fav-mv-$i"
fun favShowFocusId(i: Int) = "fav-sh-$i"
fun homeFavChannelFocusId(i: Int) = "home-fc-$i"

/** Focus candidates for the Favorites tab, first one that exists wins. */
val FAVORITES_INITIAL_IDS = arrayOf(favChannelFocusId(0), favMovieFocusId(0), favShowFocusId(0))

/**
 * Favorites: the channels, movies and shows the viewer marked, in three rows. Select opens or plays one; the Menu
 * key removes it. Channels are in channel-number order and play as a list, so Up and Down zap between them.
 */
@Composable
fun FavoritesScreen(
    favorites: FavoritesState,
    portalUrl: String,
    memory: FocusMemory,
    onOpenChannel: (index: Int) -> Unit,
    onOpenTitle: (FavoriteEntity) -> Unit,
    onRemove: (FavoriteEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val channels = favorites.channels()
    val movies = favorites.ofKind(Kinds.MOVIE)
    val shows = favorites.ofKind(Kinds.SHOW)
    // After a removal the focused tile is gone: the next tile in that row (same position) or the previous one takes focus.
    var refocus by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(refocus) {
        val ids = refocus ?: return@LaunchedEffect
        delay(250)
        androidx.compose.runtime.withFrameNanos { }
        memory.tryFocusFirst(*ids.toTypedArray(), *FAVORITES_INITIAL_IDS)
        refocus = null
    }
    fun remove(f: FavoriteEntity, index: Int, idOf: (Int) -> String) {
        refocus = listOfNotNull(idOf(index), if (index > 0) idOf(index - 1) else null)
        onRemove(f)
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S2),
        verticalArrangement = Arrangement.spacedBy(Spacing.S3),
    ) {
        if (channels.isEmpty() && movies.isEmpty() && shows.isEmpty()) {
            Text("No favorites yet", style = Tokens.TitleLarge, color = Tokens.TextPrimary)
            Text("On Live TV press Menu on a channel. On a movie or show page choose Add to favorites.", style = Tokens.Body, color = Tokens.TextSecondary)
            return@Column
        }
        if (channels.isNotEmpty()) {
            Text("Channels", style = Tokens.TitleMedium, color = Tokens.TextPrimary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S3), contentPadding = PaddingValues(end = Spacing.S1, top = Spacing.S1, bottom = Spacing.S1)) {
                itemsIndexed(channels, key = { _, f -> f.refId }) { i, f ->
                    ChannelCard(favChannelFocusId(i), f.number.orEmpty(), f.name, memory, { onOpenChannel(i) }, Modifier.onMenuKey { remove(f, i, ::favChannelFocusId) })
                }
            }
        }
        TitleRow("Movies", movies, ::favMovieFocusId, portalUrl, memory, onOpenTitle) { f, i -> remove(f, i, ::favMovieFocusId) }
        TitleRow("Shows", shows, ::favShowFocusId, portalUrl, memory, onOpenTitle) { f, i -> remove(f, i, ::favShowFocusId) }
        Text("Press Menu on a favorite to remove it", style = Tokens.Body, color = Tokens.TextSecondary)
    }
}

/** A channel tile: its number and name. */
@Composable
fun ChannelCard(id: String, number: String, name: String, memory: FocusMemory, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    OsFocusable(id = id, memory = memory, variant = FocusVariant.CARD, onSelect = onSelect, modifier = modifier.width(280.dp)) { _ ->
        Column(Modifier.fillMaxWidth().height(150.dp).padding(Spacing.S2), verticalArrangement = Arrangement.Center) {
            Text(number, style = Tokens.Mono, color = Tokens.TextSecondary, maxLines = 1)
            Text(PortalText.decodeEntities(name), style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 2)
        }
    }
}

@Composable
private fun TitleRow(
    heading: String, items: List<FavoriteEntity>, idOf: (Int) -> String, portalUrl: String, memory: FocusMemory,
    onOpen: (FavoriteEntity) -> Unit, onRemove: (FavoriteEntity, Int) -> Unit,
) {
    if (items.isEmpty()) return
    Text(heading, style = Tokens.TitleMedium, color = Tokens.TextPrimary)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S3), contentPadding = PaddingValues(end = Spacing.S1, top = Spacing.S1, bottom = Spacing.S1)) {
        itemsIndexed(items, key = { _, f -> f.refId }) { i, f ->
            OsFocusable(id = idOf(i), memory = memory, variant = FocusVariant.CARD, onSelect = { onOpen(f) }, modifier = Modifier.width(190.dp).onMenuKey { onRemove(f, i) }) { _ ->
                Column(Modifier.background(Tokens.Surface1)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                        Poster(PosterUrl.resolve(f.posterUri, portalUrl), Modifier.fillMaxSize(), targetWidthPx = 300)
                    }
                    Text(PortalText.decodeEntities(f.name), style = Tokens.Body, color = Tokens.TextPrimary, maxLines = 1, modifier = Modifier.padding(Spacing.S1))
                }
            }
        }
    }
}
