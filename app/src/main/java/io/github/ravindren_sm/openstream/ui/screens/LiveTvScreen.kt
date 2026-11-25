package io.github.ravindren_sm.openstream.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import io.github.ravindren_sm.openstream.data.ChannelLogoUrl
import io.github.ravindren_sm.openstream.data.ChannelNames
import io.github.ravindren_sm.openstream.data.ChannelSort
import io.github.ravindren_sm.openstream.data.ChannelSorting
import io.github.ravindren_sm.openstream.data.LivePlayback
import io.github.ravindren_sm.openstream.data.PagedChannels
import io.github.ravindren_sm.openstream.data.SortStatus
import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.ui.components.ChannelLogo
import io.github.ravindren_sm.openstream.ui.components.ChannelPreview
import io.github.ravindren_sm.openstream.ui.components.Choice
import io.github.ravindren_sm.openstream.ui.components.ChoicePicker
import io.github.ravindren_sm.openstream.ui.components.NowNextBlock
import io.github.ravindren_sm.openstream.ui.components.QualityBadge
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
    /** The selected group in the portal's own order (by channel number), loaded a page at a time. */
    var numbered: PagedChannels by mutableStateOf(PagedChannels { CatalogResult.Failed(LiveCatalog.NOT_CONNECTED) })
        private set
    /**
     * The channels on screen, which the player zaps through and the focus ids index: [numbered] until an A to Z sort of the
     * whole group has finished, then a complete sorted copy of it.
     */
    var channels: PagedChannels by mutableStateOf(numbered)
        private set
    /** The order chosen for the groups (kept while the viewer moves between groups). */
    var sort: ChannelSort by mutableStateOf(ChannelSort.NUMBER)
        private set
    var sortStatus: SortStatus by mutableStateOf(SortStatus.Idle)
        private set
    /** Bumped on every choice of sort, so choosing A to Z again retries a failed load. */
    var sortRequest: Int by mutableIntStateOf(0)
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
        numbered = PagedChannels { page -> catalog.channels(genre.id, page) }
        channels = numbered
        sortStatus = SortStatus.Idle
        if (resetScroll) channelListState.requestScrollToItem(0)
    }

    /** Chooses the order of the channel list. A to Z is carried out by [sortFully], which the screen runs. */
    fun changeSort(newSort: ChannelSort) {
        sort = newSort
        sortRequest++
        sortStatus = SortStatus.Idle
        channels = numbered
        channelListState.requestScrollToItem(0)
    }

    /**
     * Carries out an A to Z sort of the selected group: loads every page of it (up to [ChannelSorting.CAP] channels; a bigger
     * group stays in the portal's order and [sortStatus] says so), then replaces [channels] by a sorted copy. Suspends while
     * pages load, so call it from an effect that is cancelled when the group or the sort changes. Returns true when [channels]
     * was replaced (the caller then puts the list back at the top).
     */
    suspend fun sortFully(): Boolean {
        if (sort != ChannelSort.NAME) return false
        val source = numbered
        withContext(Dispatchers.IO) { if (source.items.isEmpty() && !source.exhausted) source.loadNext() }
        val total = source.total
        if (total == null) { sortStatus = SortStatus.Failed(source.error ?: LiveCatalog.NO_CHANNELS); return false }
        if (!ChannelSorting.canSortFully(total)) { sortStatus = SortStatus.TooBig(total); return false }
        while (!source.exhausted) {
            sortStatus = SortStatus.Loading(source.items.size, total)
            val before = source.items.size
            withContext(Dispatchers.IO) { source.loadNext() }
            source.error?.let { sortStatus = SortStatus.Failed(it); return false }
            // Another loader (the list's own paging) may hold the page: wait for it rather than spin.
            if (source.items.size == before && !source.exhausted) delay(50)
        }
        val sorted = withContext(Dispatchers.Default) { ChannelSorting.byName(source.items) }
        // The shown list changes only if this is still the group and the order that was asked for.
        if (source !== numbered || sort != ChannelSort.NAME) return false
        channels = withContext(Dispatchers.Default) { PagedChannels.fixed(sorted) }
        sortStatus = SortStatus.Done
        return true
    }
}

/** Focus ids: the genre rail and the channel list use position, so Back can return to the exact row. */
fun genreFocusId(index: Int) = "genre-$index"
fun channelFocusId(index: Int) = "channel-$index"
/** The Sort button above the channel list. */
const val LIVE_SORT_ID = "live-sort"

/** How near the end of the loaded channels the user gets before the next page is requested. */
private const val PREFETCH_ROWS = 6

/** Width of the genre rail and of the side panel (a 16:9 preview with the programme under it). The list takes what is left. */
private val RAIL_WIDTH = 400.dp
private val PANEL_WIDTH = 480.dp

/**
 * Live TV: genre rail on the left, channels of the selected genre in the middle, loaded a server
 * page at a time as the user scrolls, and a side panel for the focused channel on the right: a muted
 * live picture, its name and number, what is on now and next, and a hint. [connected] is whether the
 * portal session is ready; the genres are fetched once it is. Selecting a channel calls
 * [onOpenChannel] with its position. The panel needs [playback] (without it the list uses the full
 * width) and is never focusable: Left from the list goes to the rail, Right from the rail enters the list.
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
    /** Resolves a channel to a stream for the side panel's preview; null leaves the panel out. */
    playback: LivePlayback? = null,
    /** The portal address, to resolve channel logos that are sent as paths or file names. */
    portalUrl: String = "",
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    // The channel under focus, for the side panel (set and cleared by the rows' focus changes, as in Sports).
    var focusedChannel: LiveChannel? by remember { mutableStateOf(null) }
    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowSec = System.currentTimeMillis() / 1000 } }

    // Genres load once the session is connected (and again after a failed attempt on reconnect).
    LaunchedEffect(connected) {
        if (connected && !state.genresLoaded) withContext(Dispatchers.IO) { state.loadGenres() }
    }
    // A to Z: loads the whole (small enough) group in the background, then swaps in the sorted copy. Restarts when the group or
    // the choice changes, and stops when the screen is left (it resumes on return).
    LaunchedEffect(state.numbered, state.sortRequest, state.genresLoaded) {
        focusedChannel = null
        if (!state.genresLoaded || state.sort != ChannelSort.NAME) return@LaunchedEffect
        if (state.sortFully()) {
            // The rows were replaced: start at the top, and if focus was on a row of the old order keep it in the list.
            val inList = memory.focusedId?.startsWith("channel-") == true
            state.channelListState.scrollToItem(0)
            if (inList) { withFrameNanos { }; withFrameNanos { }; memory.tryFocus(channelFocusId(0)) }
        }
    }

    Box(modifier.fillMaxSize()) {
    Row(
        Modifier.fillMaxSize()
            // Down from the tab bar lands on the group being watched.
            .enterAt(memory, setOf(androidx.compose.ui.focus.FocusDirection.Down)) { genreFocusId(state.selected) }
            .padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S4),
    ) {
        // ---- genre rail ----
        Column(Modifier.width(RAIL_WIDTH).fillMaxHeight()) {
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
        // One focus group for the Sort button and the list: Right from the rail lands on the first visible channel (or on Sort
        // when the group is empty), not on whichever of the two is nearer the row focus came from.
        Column(
            Modifier.weight(1f).fillMaxHeight().enterAt(memory) {
                if (state.channels.items.isEmpty()) LIVE_SORT_ID else channelFocusId(state.channelListState.firstVisibleItemIndex)
            },
        ) {
            if (state.genresLoaded) ChannelList(
                state, memory, portalUrl, nowSec, onOpenChannel, isFavorite, onToggleFavorite, guide,
                onOpenSort = { picking = true },
                onFocusChange = { ch, focused -> if (focused) focusedChannel = ch else if (focusedChannel?.id == ch.id) focusedChannel = null },
            )
        }

        // ---- side panel (never focusable) ----
        if (playback != null && state.genresLoaded) ChannelPanel(focusedChannel, playback, guide, nowSec, portalUrl, isFavorite)
    }
    if (picking) {
        ChoicePicker(
            "Sort channels", ChannelSort.entries.map { Choice(it.name, if (it == ChannelSort.NUMBER) "Number (the portal's order)" else "A to Z") },
            setOf(state.sort.name), multi = false,
            note = "A to Z loads the whole group first, so it works for groups of up to ${ChannelSorting.CAP} channels; a bigger group stays in the portal's order.",
            memory = memory, returnFocusId = LIVE_SORT_ID,
            onToggle = { id -> state.changeSort(ChannelSort.valueOf(id)) }, onClose = { picking = false },
        )
    }
    }
}

@Composable
private fun ChannelList(
    state: LiveBrowseState, memory: FocusMemory, portalUrl: String, nowSec: Long, onOpenChannel: (Int, LiveChannel) -> Unit,
    isFavorite: (LiveChannel) -> Boolean, onToggleFavorite: (LiveChannel) -> Unit, guide: GuideLoader?,
    onOpenSort: () -> Unit, onFocusChange: (LiveChannel, Boolean) -> Unit,
) {
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

    Row(Modifier.padding(bottom = Spacing.S1), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        Text(paged.total?.let { "%,d channels".format(it) } ?: " ", style = Tokens.Body, color = Tokens.TextSecondary)
        OsFocusable(id = LIVE_SORT_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = onOpenSort) { _ ->
            Text(
                "Sort: ${state.sort.label}", style = Tokens.Body, maxLines = 1,
                color = if (state.sort != ChannelSort.NUMBER) Tokens.AccentPrimary else Tokens.TextPrimary,
                modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1),
            )
        }
    }
    val status = ChannelSorting.statusText(state.sort, state.sortStatus)
    if (status.isNotEmpty()) {
        val warn = state.sortStatus is SortStatus.TooBig || state.sortStatus is SortStatus.Failed
        Text(status, style = Tokens.Body, color = if (warn) Tokens.StatusWarning else Tokens.TextSecondary, maxLines = 2, modifier = Modifier.padding(bottom = Spacing.S1))
    }
    Box(Modifier.fillMaxSize()) {
        if (paged.items.isEmpty() && paged.error == null && !paged.exhausted) {
            Message("Loading channels…")
        } else if (paged.items.isEmpty() && paged.error == null) {
            Message("No channels in this group.")
        } else {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                itemsIndexed(paged.items, key = { _, c -> c.id }) { index, channel ->
                    ChannelRow(index, channel, portalUrl, nowSec, memory, onOpenChannel, isFavorite(channel), onToggleFavorite, guide, onFocusChange)
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

/** One channel row: number, logo (a neutral box until it loads or when there is none), name without its quality tag, the badge, a heart, now and next. */
@Composable
private fun ChannelRow(
    index: Int, channel: LiveChannel, portalUrl: String, nowSec: Long, memory: FocusMemory, onOpenChannel: (Int, LiveChannel) -> Unit,
    favorite: Boolean, onToggleFavorite: (LiveChannel) -> Unit, guide: GuideLoader?, onFocusChange: (LiveChannel, Boolean) -> Unit,
) {
    val parsed = remember(channel.id, channel.name) { ChannelNames.parse(PortalText.decodeEntities(channel.name)) }
    val logoUrl = remember(channel.id, portalUrl) { ChannelLogoUrl.resolve(channel.logo, portalUrl) }
    OsFocusable(
        id = channelFocusId(index), memory = memory, variant = FocusVariant.ROW,
        modifier = Modifier.fillMaxWidth().onMenuKey { onToggleFavorite(channel) },
        onSelect = { onOpenChannel(index, channel) },
        onFocusChange = { f -> onFocusChange(channel, f) },
    ) { _ ->
        Row(Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1 + 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(channel.number, style = Tokens.Mono, color = Tokens.TextSecondary, maxLines = 1, modifier = Modifier.width(84.dp))
            ChannelLogo(logoUrl, Modifier.width(84.dp).height(52.dp))
            Column(Modifier.weight(1f).padding(start = Spacing.S2)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                    Text(parsed.display, style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    parsed.quality?.let { QualityBadge(it) }
                    if (favorite) Text(HEART, style = Tokens.BodyLarge, color = Tokens.StatusError)
                }
                // What the channel is showing, so it does not have to be opened to find out.
                if (guide != null) NowNextLine(channel, guide, nowSec)
            }
        }
    }
}

/**
 * The side panel for the channel under focus: a muted live picture (after focus has rested a moment), the channel's number,
 * name and badge, what is on now and next with a progress bar, and a hint. Plain content: the D-pad never lands here.
 */
@Composable
private fun ChannelPanel(
    channel: LiveChannel?, playback: LivePlayback, guide: GuideLoader?, nowSec: Long, portalUrl: String, isFavorite: (LiveChannel) -> Boolean,
) {
    // The guide of the focused channel, read after a short pause so moving through the list asks for nothing; the row's own
    // line has usually loaded it already (the loader keeps it ten minutes).
    var programmes: List<EpgProgramme>? by remember(channel?.id) { mutableStateOf(null) }
    LaunchedEffect(channel?.id) {
        if (channel != null && guide != null) { delay(300); programmes = withContext(Dispatchers.IO) { guide.load(channel) } }
    }
    Column(Modifier.width(PANEL_WIDTH).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        ChannelPreview(channel, playback)
        if (channel == null) {
            Text("Move to a channel to see its picture and what is on.", style = Tokens.Body, color = Tokens.TextSecondary)
        } else {
            val parsed = remember(channel.id, channel.name) { ChannelNames.parse(PortalText.decodeEntities(channel.name)) }
            val logoUrl = remember(channel.id, portalUrl) { ChannelLogoUrl.resolve(channel.logo, portalUrl) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
                ChannelLogo(logoUrl, Modifier.width(84.dp).height(52.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                        if (channel.number.isNotBlank()) Text(channel.number, style = Tokens.Mono, color = Tokens.AccentPrimary, maxLines = 1)
                        parsed.quality?.let { QualityBadge(it) }
                        if (isFavorite(channel)) Text(HEART, style = Tokens.Body, color = Tokens.StatusError)
                    }
                    Text(parsed.display, style = Tokens.TitleMedium, color = Tokens.TextPrimary, maxLines = 2)
                }
            }
            NowNextBlock(channel, programmes, nowSec)
            Text("Live picture, no sound. Select opens the channel; Menu adds it to favorites.", style = Tokens.Body, color = Tokens.TextSecondary)
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
