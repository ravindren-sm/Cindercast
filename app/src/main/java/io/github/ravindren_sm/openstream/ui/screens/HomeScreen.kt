package io.github.ravindren_sm.openstream.ui.screens

import androidx.activity.compose.BackHandler
import io.github.ravindren_sm.openstream.ui.focus.trapFocus
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.ravindren_sm.openstream.ui.focus.keepHorizontalFocus
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.PinKinds
import io.github.ravindren_sm.openstream.data.PinnedRow
import io.github.ravindren_sm.openstream.data.PosterUrl
import io.github.ravindren_sm.openstream.nav.Route
import io.github.ravindren_sm.openstream.profiles.ContinueTile
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.components.Poster
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import kotlinx.coroutines.delay
import io.github.ravindren_sm.openstream.ui.components.onMenuKey

fun continueFocusId(index: Int) = "home-cw-$index"
fun recentFocusId(index: Int) = "home-rw-$index"
fun doorFocusId(index: Int) = "home-door-$index"
fun homeSavedFocusId(index: Int) = "home-saved-$index"
fun homePinFocusId(row: Int, index: Int) = "home-pin-$row-$index"
private fun menuFocusId(index: Int) = "home-menu-$index"

/** The four doors every profile has. Search has no screen yet, so it opens the placeholder like the tab does. */
private val DOORS = listOf(Route.LIVE_TV to "Live TV", Route.MOVIES to "Movies", Route.SHOWS to "Shows", Route.SEARCH to "Search")

/** What the options menu can do to a tile. */
enum class TileAction { REMOVE, TOGGLE_WATCHED }

/**
 * Home: "Continue watching" first (started and not finished, with progress and time left), then "Recently watched"
 * (finished titles), then the doors into the app. With nothing watched yet the doors are the whole page, with a short
 * invitation. The Menu key on a tile opens its options (remove, mark watched or not watched). [continueTiles] is null
 * while the history is still being read.
 */
@Composable
fun HomeScreen(
    continueTiles: List<ContinueTile>?,
    recentTiles: List<ContinueTile>,
    favoriteChannels: List<io.github.ravindren_sm.openstream.profiles.db.FavoriteEntity>,
    onOpenFavoriteChannel: (Int) -> Unit,
    /** Menu on a favorite channel tile: remove it from favorites. */
    onRemoveFavoriteChannel: (io.github.ravindren_sm.openstream.profiles.db.FavoriteEntity) -> Unit = {},
    pinnedRows: List<PinnedRow>,
    onOpenPinnedTitle: (io.github.ravindren_sm.openstream.provider.VodItem) -> Unit,
    onOpenPinnedChannel: (rowIndex: Int, channels: List<io.github.ravindren_sm.openstream.provider.LiveChannel>, index: Int) -> Unit,
    portalUrl: String,
    memory: FocusMemory,
    onResume: (ContinueTile) -> Unit,
    onDoor: (Route) -> Unit,
    /** The profile's saved searches (empty when none, or for a Kids profile): a row of tiles that open the Saved tab on that search. */
    savedSearches: List<io.github.ravindren_sm.openstream.data.SavedSearch> = emptyList(),
    onOpenSaved: (io.github.ravindren_sm.openstream.data.SavedSearch) -> Unit = {},
    /** False for a Kids profile: no Live TV door. */
    liveAllowed: Boolean = true,
    onTileAction: (ContinueTile, TileAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuTile: ContinueTile? by remember { mutableStateOf(null) }
    var menuFocusIdBefore by remember { mutableStateOf<String?>(null) }
    var refocus by remember { mutableStateOf<List<String>?>(null) }
    val empty = continueTiles.isNullOrEmpty() && recentTiles.isEmpty() && favoriteChannels.isEmpty() && pinnedRows.isEmpty() && savedSearches.isEmpty()

    // After the menu closes, put focus back where the viewer was (the tile may be gone: fall back to a door).
    LaunchedEffect(refocus) {
        val back = refocus ?: return@LaunchedEffect
        delay(300)                                    // let a changed row settle first
        androidx.compose.runtime.withFrameNanos { }
        memory.tryFocusFirst(*back.toTypedArray(), doorFocusId(0))
        refocus = null
    }
    LaunchedEffect(menuTile) {
        if (menuTile != null) { androidx.compose.runtime.withFrameNanos { }; memory.tryFocus(menuFocusId(0)) }
    }
    fun open(tile: ContinueTile, focusId: String) { menuFocusIdBefore = focusId; menuTile = tile }
    fun close(action: TileAction?) {
        val tile = menuTile ?: return
        menuTile = null
        // Follow the tile: marking it watched or not moves it to the first place of the other row.
        refocus = when (action) {
            TileAction.TOGGLE_WATCHED -> listOf(if (tile.completed) continueFocusId(0) else recentFocusId(0))
            else -> listOfNotNull(menuFocusIdBefore)
        }
        if (action != null) onTileAction(tile, action)
    }
    BackHandler(enabled = menuTile != null) { close(null) }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S2),
            verticalArrangement = Arrangement.spacedBy(Spacing.S3),
        ) {
            if (!continueTiles.isNullOrEmpty()) {
                Text("Continue watching", style = Tokens.TitleMedium, color = Tokens.TextPrimary)
                TileRow(continueTiles, ::continueFocusId, 220.dp, portalUrl, memory, onResume, ::open)
            } else if (empty && continueTiles != null) {
                Text("Welcome to OpenStream", style = Tokens.TitleLarge, color = Tokens.TextPrimary)
                Text("Pick something to watch. Titles you start will appear here so you can carry on later.", style = Tokens.Body, color = Tokens.TextSecondary)
            }
            if (recentTiles.isNotEmpty()) {
                Text("Recently watched", style = Tokens.TitleMedium, color = Tokens.TextPrimary)
                TileRow(recentTiles, ::recentFocusId, 170.dp, portalUrl, memory, onResume, ::open)
            }
            if (favoriteChannels.isNotEmpty()) {
                Text("Favorite channels", style = Tokens.TitleMedium, color = Tokens.TextPrimary)
                LazyRow(modifier = Modifier.keepHorizontalFocus(memory, homeFavChannelFocusId(0)), horizontalArrangement = Arrangement.spacedBy(Spacing.S3), contentPadding = Spacing.CardRow) {
                    itemsIndexed(favoriteChannels, key = { _, f -> f.refId }) { i, f ->
                        // Menu removes the channel from favorites, as on the Favorites tab; focus moves to its neighbour.
                        ChannelCard(homeFavChannelFocusId(i), f.number.orEmpty(), f.name, memory, { onOpenFavoriteChannel(i) },
                            Modifier.onMenuKey {
                                refocus = listOfNotNull(homeFavChannelFocusId(i), if (i > 0) homeFavChannelFocusId(i - 1) else null, continueFocusId(0))
                                onRemoveFavoriteChannel(f)
                            })
                    }
                }
            }
            pinnedRows.forEachIndexed { r, row ->
                val live = row.pin.kind == PinKinds.LIVE
                Text(if (live) "Live · ${PortalText.decodeEntities(row.pin.title)}" else "New in ${PortalText.decodeEntities(row.pin.title)}", style = Tokens.TitleMedium, color = Tokens.TextPrimary)
                when {
                    row.channels != null -> LazyRow(modifier = Modifier.keepHorizontalFocus(memory, homePinFocusId(r, 0)), horizontalArrangement = Arrangement.spacedBy(Spacing.S3), contentPadding = Spacing.CardRow) {
                        itemsIndexed(row.channels, key = { _, c -> c.id }) { i, c -> ChannelCard(homePinFocusId(r, i), c.number, c.name, memory, { onOpenPinnedChannel(r, row.channels, i) }) }
                    }
                    row.titles != null -> LazyRow(modifier = Modifier.keepHorizontalFocus(memory, homePinFocusId(r, 0)), horizontalArrangement = Arrangement.spacedBy(Spacing.S3), contentPadding = Spacing.CardRow) {
                        itemsIndexed(row.titles, key = { _, v -> v.id }) { i, v ->
                            OsFocusable(id = homePinFocusId(r, i), memory = memory, variant = FocusVariant.CARD, modifier = Modifier.width(170.dp), onSelect = { onOpenPinnedTitle(v) }) { _ ->
                                Column(Modifier.background(Tokens.Surface1)) {
                                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) { Poster(PosterUrl.resolve(v.posterUri, portalUrl), Modifier.fillMaxSize(), targetWidthPx = 300) }
                                    Text(PortalText.decodeEntities(v.name), style = Tokens.Body, color = Tokens.TextPrimary, maxLines = 1, modifier = Modifier.padding(Spacing.S1))
                                }
                            }
                        }
                    }
                    row.failed -> Text("Could not load this row.", style = Tokens.Body, color = Tokens.StatusWarning)
                    else -> Text("Loading…", style = Tokens.Body, color = Tokens.TextSecondary)
                }
            }
            if (savedSearches.isNotEmpty()) {
                Text("Saved searches", style = Tokens.TitleMedium, color = Tokens.TextPrimary)
                LazyRow(modifier = Modifier.keepHorizontalFocus(memory, homeSavedFocusId(0)), horizontalArrangement = Arrangement.spacedBy(Spacing.S3), contentPadding = Spacing.CardRow) {
                    itemsIndexed(savedSearches, key = { _, s -> s.id }) { i, s ->
                        OsFocusable(id = homeSavedFocusId(i), memory = memory, variant = FocusVariant.CARD, modifier = Modifier.width(300.dp), onSelect = { onOpenSaved(s) }) { _ ->
                            Column(Modifier.fillMaxWidth().background(Tokens.Surface1).padding(Spacing.S2), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(s.name, style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 2)
                                Text(s.summary(), style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 2)
                            }
                        }
                    }
                }
            }
            Text("Browse", style = Tokens.TitleMedium, color = Tokens.TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3), modifier = Modifier.fillMaxWidth().keepHorizontalFocus(memory, doorFocusId(0))) {
                DOORS.filter { liveAllowed || it.first != Route.LIVE_TV }.forEachIndexed { i, (route, label) ->
                    OsFocusable(id = doorFocusId(i), memory = memory, variant = FocusVariant.CARD, modifier = Modifier.weight(1f), onSelect = { onDoor(route) }) { _ ->
                        Box(Modifier.fillMaxWidth().height(if (empty) 160.dp else 100.dp).background(Tokens.Surface1), contentAlignment = Alignment.Center) {
                            Text(label, style = Tokens.TitleMedium, color = Tokens.TextPrimary)
                        }
                    }
                }
            }
            if (pinnedRows.isEmpty()) Text("Tip: press Menu on a category in Movies or Shows, or a group in Live TV, to show it here (a tick marks pinned ones).", style = Tokens.Body, color = Tokens.TextSecondary)
            val titles = !continueTiles.isNullOrEmpty() || recentTiles.isNotEmpty()
            val hint = when {
                titles && favoriteChannels.isNotEmpty() -> "Press Menu on a title for options, or on a favorite channel to remove it"
                titles -> "Press Menu on a title for options"
                favoriteChannels.isNotEmpty() -> "Press Menu on a favorite channel to remove it"
                else -> null
            }
            if (hint != null) Text(hint, style = Tokens.Body, color = Tokens.TextSecondary)
        }
        menuTile?.let { tile -> TileMenu(tile, memory, onChoose = ::close) }
    }
}

@Composable
private fun TileRow(
    tiles: List<ContinueTile>, idOf: (Int) -> String, width: androidx.compose.ui.unit.Dp, portalUrl: String, memory: FocusMemory,
    onSelect: (ContinueTile) -> Unit, onMenu: (ContinueTile, String) -> Unit,
) {
    LazyRow(modifier = Modifier.keepHorizontalFocus(memory, idOf(0)), horizontalArrangement = Arrangement.spacedBy(Spacing.S3), contentPadding = Spacing.CardRow) {
        itemsIndexed(tiles, key = { _, t -> t.resumeKey }) { i, tile ->
            val id = idOf(i)
            OsFocusable(
                id = id, memory = memory, variant = FocusVariant.CARD, onSelect = { onSelect(tile) },
                modifier = Modifier.width(width).onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyUp && e.key == Key.Menu) { onMenu(tile, id); true } else e.key == Key.Menu
                },
            ) { _ ->
                Column(Modifier.background(Tokens.Surface1)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                        Poster(PosterUrl.resolve(tile.posterUri, portalUrl), Modifier.fillMaxSize(), targetWidthPx = 300)
                        // Progress along the bottom edge of the poster.
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(6.dp).background(Tokens.SurfaceEmpty)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(tile.progress).background(if (tile.completed) Tokens.StatusSuccess else Tokens.AccentPrimary))
                        }
                    }
                    Column(Modifier.padding(Spacing.S1)) {
                        Text(PortalText.decodeEntities(tile.title), style = Tokens.Body, color = Tokens.TextPrimary, maxLines = 1)
                        tile.subtitle?.let { Text(PortalText.decodeEntities(it), style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1) }
                        tile.timeLeft?.let { Text(it, style = Tokens.Body, color = if (tile.completed) Tokens.StatusSuccess else Tokens.AccentPrimary, maxLines = 1) }
                    }
                }
            }
        }
    }
}

/**
 * Options for one tile. Up and Down move between the choices and Left and Right do nothing, so the D-pad cannot wander
 * to the tiles behind. Back closes it without changing anything.
 */
@Composable
private fun TileMenu(tile: ContinueTile, memory: FocusMemory, onChoose: (TileAction?) -> Unit) {
    val choices = listOf(
        "Remove from Home" to TileAction.REMOVE,
        (if (tile.completed) "Mark as not watched" else "Mark as watched") to TileAction.TOGGLE_WATCHED,
        "Cancel" to null,
    )
    var index by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().trapFocus().background(Tokens.BgCanvas.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(520.dp).background(Tokens.Surface1).padding(Spacing.S3)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent e.key in NAV_KEYS
                    when (e.key) {
                        Key.DirectionDown -> { index = (index + 1).coerceAtMost(choices.lastIndex); memory.tryFocus(menuFocusId(index)); true }
                        Key.DirectionUp -> { index = (index - 1).coerceAtLeast(0); memory.tryFocus(menuFocusId(index)); true }
                        Key.DirectionLeft, Key.DirectionRight -> true
                        else -> false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(Spacing.S1),
        ) {
            Text(PortalText.decodeEntities(tile.title), style = Tokens.TitleMedium, color = Tokens.TextPrimary, maxLines = 1)
            choices.forEachIndexed { i, (label, action) ->
                OsFocusable(id = menuFocusId(i), memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth(), onSelect = { onChoose(action) }) { _ ->
                    Text(label, style = Tokens.BodyLarge, color = Tokens.TextPrimary, modifier = Modifier.padding(Spacing.S2))
                }
            }
        }
    }
}

private val NAV_KEYS = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)
