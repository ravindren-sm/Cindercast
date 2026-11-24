package io.github.ravindren_sm.openstream.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.CatalogResult
import io.github.ravindren_sm.openstream.data.LiveCatalog
import io.github.ravindren_sm.openstream.data.PagedChannels
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.LiveGenre
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.HEART
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.components.onMenuKey
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.focus.enterAt
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import io.github.ravindren_sm.openstream.data.GuideLoader
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything the Live TV screen needs to survive opening a channel and coming back: the genres,
 * the selected genre, the channels loaded so far and both scroll positions. Held by the app shell,
 * not by the screen, so leaving the screen does not throw the loaded pages away.
 */
class LiveBrowseState(private val catalog: LiveCatalog) {
    /** The group the viewer chose last (kept across restarts by the shell), or null; Live TV opens on it instead of "All". */
    var rememberedGroupId: () -> String? = { null }
    /** Called with the id of a group the viewer picked, so the shell can keep it. */
    var onGroupChosen: (String) -> Unit = {}

    /**
     * Every group the portal lists, and the ones shown (the saved search defaults can hide groups by language). Compose state:
     * the shell derives the Sports rail and the background NFL search's groups from it, and as a plain field the shell did
     * not redraw when it filled (the NFL search then waited for some unrelated redraw, 42 s in one run).
     */
    var allGenres: List<LiveGenre> by mutableStateOf(emptyList())
        private set
    private var groupFilter: (LiveGenre) -> Boolean = { true }
    var genres: List<LiveGenre> by mutableStateOf(emptyList())
        private set
    var genresLoaded: Boolean by mutableStateOf(false)
        private set
    var genresError: String? by mutableStateOf(null)
        private set
    var selected: Int by mutableIntStateOf(0)
        private set
    var channels: PagedChannels by mutableStateOf(PagedChannels { CatalogResult.Failed(LiveCatalog.NOT_CONNECTED) })
        private set

    val genreListState = LazyListState()
    val channelListState = LazyListState()

    /** Loads the genres and selects the first one ("All channels"). Blocking: call off the main thread. */
    fun loadGenres() {
        when (val r = catalog.genres()) {
            is CatalogResult.Ok -> androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                // One step for the screen: the groups and the group shown change together, so focus never lands on "All" first.
                allGenres = r.value; genres = r.value.filter(groupFilter); genresLoaded = true; genresError = null
                select(rememberedIndex(), resetScroll = false, chosen = false)
            }
            is CatalogResult.Failed -> genresError = r.message
        }
    }

    /** Shows only the groups [filter] allows (null shows all). */
    fun applyGroupFilter(filter: ((LiveGenre) -> Boolean)?) {
        groupFilter = filter ?: { true }
        if (!genresLoaded) return
        genres = allGenres.filter(groupFilter)
        select(rememberedIndex(), resetScroll = true, chosen = false)
    }

    /** Where the remembered group is in the shown groups, else the first ("All"). */
    private fun rememberedIndex(): Int = rememberedGroupId()?.let { id -> genres.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: 0

    /** Shows the channels of genre [index], from the first page. */
    fun select(index: Int, resetScroll: Boolean = true, chosen: Boolean = true) {
        val genre = genres.getOrNull(index) ?: return
        selected = index
        if (chosen) onGroupChosen(genre.id)
        channels = PagedChannels { page -> catalog.channels(genre.id, page) }
        if (resetScroll) channelListState.requestScrollToItem(0)
    }
}

/** Focus ids: the genre rail and the channel list use position, so Back can return to the exact row. */
fun genreFocusId(index: Int) = "genre-$index"
fun channelFocusId(index: Int) = "channel-$index"

/** How near the end of the loaded channels the user gets before the next page is requested. */
private const val PREFETCH_ROWS = 6

/**
 * Live TV: genre rail on the left, channels of the selected genre on the right, loaded a server
 * page at a time as the user scrolls. [connected] is whether the portal session is ready; the
 * genres are fetched once it is. Selecting a channel calls [onOpenChannel] with its position.
 */
@Composable
fun LiveTvScreen(
    state: LiveBrowseState,
    connected: Boolean,
    memory: FocusMemory,
    onOpenChannel: (index: Int, channel: LiveChannel) -> Unit,
    isFavorite: (LiveChannel) -> Boolean = { false },
    onToggleFavorite: (LiveChannel) -> Unit = {},
    /** The group shown as a row on Home; Menu on a group in the rail pins it (one at a time) or unpins it. */
    pinnedGenreId: String? = null,
    onTogglePinGenre: (LiveGenre) -> Unit = {},
    /** When set, each channel row also shows what is on now and next (read from the portal as rows appear). */
    guide: GuideLoader? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    // Genres load once the session is connected (and again after a failed attempt on reconnect).
    LaunchedEffect(connected) {
        if (connected && !state.genresLoaded) withContext(Dispatchers.IO) { state.loadGenres() }
    }

    Row(
        modifier.fillMaxSize()
            // Down from the tab bar lands on the group being watched.
            .enterAt(memory, setOf(androidx.compose.ui.focus.FocusDirection.Down)) { genreFocusId(state.selected) }
            .padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S4),
    ) {
        // ---- genre rail ----
        Column(Modifier.width(440.dp).fillMaxHeight()) {
            when {
                // Left from a channel comes back to the group being watched, not to whichever group is level with the channel.
                state.genresLoaded -> LazyColumn(state = state.genreListState, verticalArrangement = Arrangement.spacedBy(Spacing.S1),
                    modifier = Modifier
                        // Leaving the rail with the group being watched scrolled out of view (the viewer looked further down
                        // without choosing): bring it back, so Left returns to it rather than to the group beside the channel.
                        .onFocusChanged { f ->
                            val rail = state.genreListState
                            if (!f.hasFocus && rail.layoutInfo.visibleItemsInfo.none { it.index == state.selected }) {
                                rail.requestScrollToItem((state.selected - 3).coerceAtLeast(0))
                            }
                        }
                        .enterAt(memory) { genreFocusId(state.selected) }) {
                    itemsIndexed(state.genres, key = { _, g -> g.id }) { index, genre ->
                        OsFocusable(
                            id = genreFocusId(index), memory = memory, variant = FocusVariant.ROW,
                            modifier = Modifier.fillMaxWidth().onMenuKey { onTogglePinGenre(genre) },
                            onSelect = { state.select(index) },
                        ) { _ ->
                            Text(
                                genre.title + if (genre.id == pinnedGenreId) "  ✓" else "", style = Tokens.BodyLarge,
                                color = if (index == state.selected) Tokens.AccentPrimary else Tokens.TextPrimary,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1 + 4.dp),
                            )
                        }
                    }
                }
                state.genresError != null -> RetryRow(state.genresError!!, memory) {
                    scope.launch {
                        withContext(Dispatchers.IO) { state.loadGenres() }
                        // The retry row is gone once the genres load; without this, focus falls to the top tab.
                        if (state.genresLoaded) { withFrameNanos { }; memory.tryFocus(genreFocusId(0)) }
                    }
                }
                else -> Message(if (connected) "Loading channel groups…" else "Waiting for the portal connection…")
            }
        }

        // ---- channel list ----
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (state.genresLoaded) ChannelList(state, memory, onOpenChannel, isFavorite, onToggleFavorite, guide)
        }
    }
}

@Composable
private fun ChannelList(
    state: LiveBrowseState, memory: FocusMemory, onOpenChannel: (Int, LiveChannel) -> Unit,
    isFavorite: (LiveChannel) -> Boolean, onToggleFavorite: (LiveChannel) -> Unit, guide: GuideLoader?,
) {
    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowSec = System.currentTimeMillis() / 1000 } }
    val paged = state.channels
    val listState = state.channelListState
    val scope = rememberCoroutineScope()

    // First page, then more as the user nears the end of what is loaded. Keyed on the paged list, so
    // choosing another genre starts a fresh load. The flow also re-evaluates when the list grows.
    LaunchedEffect(paged) {
        if (paged.items.isEmpty() && !paged.exhausted) withContext(Dispatchers.IO) { paged.loadNext() }
        snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to paged.items.size }
            .collect { (lastVisible, loaded) ->
                if (loaded > 0 && lastVisible >= loaded - PREFETCH_ROWS && !paged.exhausted && paged.error == null) {
                    withContext(Dispatchers.IO) { paged.loadNext() }
                }
            }
    }

    Text(
        paged.total?.let { "%,d channels".format(it) } ?: " ",
        style = Tokens.Body, color = Tokens.TextSecondary,
        modifier = Modifier.padding(bottom = Spacing.S1),
    )
    Box(Modifier.fillMaxSize()) {
        if (paged.items.isEmpty() && paged.error == null && !paged.exhausted) {
            Message("Loading channels…")
        } else if (paged.items.isEmpty() && paged.error == null) {
            Message("No channels in this group.")
        } else {
            // Right from the group rail enters the channel list at its first visible channel.
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(Spacing.S1),
                modifier = Modifier.enterAt(memory) { channelFocusId(listState.firstVisibleItemIndex) }) {
                itemsIndexed(paged.items, key = { _, c -> c.id }) { index, channel ->
                    OsFocusable(
                        id = channelFocusId(index), memory = memory, variant = FocusVariant.ROW,
                        modifier = Modifier.fillMaxWidth().onMenuKey { onToggleFavorite(channel) },
                        onSelect = { onOpenChannel(index, channel) },
                    ) { _ ->
                        Row(Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1 + 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(channel.number, style = Tokens.Mono, color = Tokens.TextSecondary, maxLines = 1, modifier = Modifier.width(96.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(channel.name, style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                    if (isFavorite(channel)) Text("  $HEART", style = Tokens.BodyLarge, color = Tokens.StatusError)
                                }
                                // What the channel is showing, so it does not have to be opened to find out.
                                if (guide != null) NowNextLine(channel, guide, nowSec)
                            }
                        }
                    }
                }
                if (paged.error != null) {
                    item(key = "retry") {
                        RetryRow(paged.error!!, memory) {
                            scope.launch {
                                val before = paged.items.size
                                withContext(Dispatchers.IO) { paged.loadNext() }
                                // The retry row is removed on success. Put focus on the first new row (keeping a
                                // few rows of context above it) instead of letting it fall to the top tab.
                                if (paged.items.size > before) {
                                    listState.scrollToItem(maxOf(before - 3, 0))
                                    withFrameNanos { }
                                    memory.tryFocus(channelFocusId(before))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RetryRow(message: String, memory: FocusMemory, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
        Text(message, style = Tokens.BodyLarge, color = Tokens.StatusError)
        OsFocusable(id = "live-retry", memory = memory, variant = FocusVariant.BUTTON, onSelect = onRetry) { _ ->
            Text("Select to try again", style = Tokens.BodyLarge, color = Tokens.TextPrimary,
                modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1))
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = Tokens.BodyLarge, color = Tokens.TextSecondary)
}
