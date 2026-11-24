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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.CatalogResult
import io.github.ravindren_sm.openstream.data.GuideLoader
import io.github.ravindren_sm.openstream.data.LiveCatalog
import io.github.ravindren_sm.openstream.data.NflSearch
import io.github.ravindren_sm.openstream.data.PagedChannels
import io.github.ravindren_sm.openstream.data.SportsLogic
import io.github.ravindren_sm.openstream.data.VodKind
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.LiveGenre
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.data.VodCategory
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.focus.enterAt
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** One line of the Sports rail. */
sealed interface SportsEntry {
    val label: String
    /** The NFL search (games found in the channel guides). */
    data object Nfl : SportsEntry { override val label = "NFL" }
    /** A live channel group about sport. */
    data class Live(val genre: LiveGenre) : SportsEntry { override val label get() = PortalText.decodeEntities(genre.title) }
    /** An on-demand category about sport; Select opens it in Movies or Shows. */
    data class OnDemand(val kind: VodKind, val category: VodCategory) : SportsEntry { override val label get() = PortalText.decodeEntities(category.title) }
}

/** What the Sports tab keeps while the viewer is elsewhere (held by the app shell, like the Live TV state). */
class SportsState(private val catalog: LiveCatalog, loader: GuideLoader, saveNfl: (String) -> Unit = {}) {
    var selected: Int by mutableIntStateOf(0)
        private set
    var channels: PagedChannels by mutableStateOf(PagedChannels { CatalogResult.Failed(LiveCatalog.NOT_CONNECTED) })
        private set
    val nfl = NflSearch(catalog, loader, save = saveNfl)
    val railState = LazyListState()
    val listState = LazyListState()

    /** Shows rail entry [index]: a live group's channels, or the NFL search. On-demand entries are opened by the shell. */
    fun select(index: Int, entry: SportsEntry) {
        selected = index
        if (entry is SportsEntry.Live) channels = PagedChannels { page -> catalog.channels(entry.genre.id, page) }
        listState.requestScrollToItem(0)
    }
}

/** The rail: the NFL search, then the live sports groups, then the on-demand sports categories (movies, then shows). */
fun sportsEntries(genres: List<LiveGenre>, movieCategories: List<VodCategory>, showCategories: List<VodCategory>): List<SportsEntry> =
    listOf<SportsEntry>(SportsEntry.Nfl) +
        genres.filter { it.id != LiveCatalog.ALL_ID && SportsLogic.isSports(it.title) }.map { SportsEntry.Live(it) } +
        movieCategories.filter { SportsLogic.isSports(it.title) }.map { SportsEntry.OnDemand(VodKind.MOVIES, it) } +
        showCategories.filter { SportsLogic.isSports(it.title) }.map { SportsEntry.OnDemand(VodKind.SHOWS, it) }

fun sportsRailFocusId(i: Int) = "sports-rail-$i"
fun sportsRowFocusId(i: Int) = "sports-row-$i"
const val SPORTS_MORE_ID = "sports-nfl-more"

/**
 * Sports: the live sports groups with what is on now and next on each channel (so a channel does not have to be opened to
 * see what it shows), an NFL search across the likely channels' guides, and the on-demand sports categories.
 */
@Composable
fun SportsScreen(
    state: SportsState,
    entries: List<SportsEntry>,
    /** Every live group the viewer may see (not only the sports ones): the networks carrying most NFL games sit in general groups. */
    allGenres: List<LiveGenre>,
    loading: Boolean,
    loader: GuideLoader,
    connected: Boolean,
    memory: FocusMemory,
    onOpenChannel: (channels: List<LiveChannel>, index: Int) -> Unit,
    onOpenOnDemand: (SportsEntry.OnDemand) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowSec = System.currentTimeMillis() / 1000 } }
    val entry = entries.getOrNull(state.selected) ?: SportsEntry.Nfl

    Row(
        modifier.fillMaxSize()
            // Down from the tab bar lands on the rail entry being shown.
            .enterAt(memory, setOf(FocusDirection.Down)) { sportsRailFocusId(state.selected) }
            .padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S4),
    ) {
        Column(Modifier.width(440.dp).fillMaxHeight()) {
            if (entries.size <= 1 && loading) Text(if (connected) "Loading sports groups…" else "Waiting for the portal connection…", style = Tokens.BodyLarge, color = Tokens.TextSecondary)
            LazyColumn(
                state = state.railState, verticalArrangement = Arrangement.spacedBy(Spacing.S1),
                modifier = Modifier.enterAt(memory) { sportsRailFocusId(state.selected) },
            ) {
                itemsIndexed(entries, key = { i, e -> "$i:${e.label}" }) { i, e ->
                    val prev = entries.getOrNull(i - 1)
                    // Section headings where the kind of entry changes.
                    val heading = when {
                        e is SportsEntry.Live && prev !is SportsEntry.Live -> "Live channels"
                        e is SportsEntry.OnDemand && prev !is SportsEntry.OnDemand -> "On demand"
                        else -> null
                    }
                    Column {
                        heading?.let { Text(it, style = Tokens.Body, color = Tokens.TextSecondary, modifier = Modifier.padding(top = Spacing.S2, bottom = Spacing.S1, start = Spacing.S2)) }
                        OsFocusable(
                            id = sportsRailFocusId(i), memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth(),
                            onSelect = { if (e is SportsEntry.OnDemand) onOpenOnDemand(e) else state.select(i, e) },
                        ) { _ ->
                            Text(
                                e.label + if (e is SportsEntry.OnDemand) "  ›" else "", style = Tokens.BodyLarge, maxLines = 1,
                                color = if (i == state.selected) Tokens.AccentPrimary else Tokens.TextPrimary,
                                modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1 + 4.dp),
                            )
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            when (entry) {
                is SportsEntry.Nfl -> NflPane(state, allGenres, connected, nowSec, memory, onOpenChannel)
                is SportsEntry.Live -> LivePane(state, loader, nowSec, memory, onOpenChannel)
                is SportsEntry.OnDemand -> Unit
            }
        }
    }
}

@Composable
private fun LivePane(state: SportsState, loader: GuideLoader, nowSec: Long, memory: FocusMemory, onOpenChannel: (List<LiveChannel>, Int) -> Unit) {
    val paged = state.channels
    val listState = state.listState
    LaunchedEffect(paged) {
        if (paged.items.isEmpty() && !paged.exhausted) withContext(Dispatchers.IO) { paged.loadNext() }
        snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to paged.items.size }.collect { (last, loaded) ->
            if (loaded > 0 && last >= loaded - 4 && !paged.exhausted && paged.error == null) withContext(Dispatchers.IO) { paged.loadNext() }
        }
    }
    // Which channels of this group have a guide: when the first several have none, say so once instead of on every row.
    val hasGuide = remember(paged) { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val noGuideAtAll = hasGuide.size >= 6 && hasGuide.values.none { it }
    Text(paged.total?.let { "%,d channels · what is on now and next".format(it) } ?: " ", style = Tokens.Body, color = Tokens.TextSecondary, modifier = Modifier.padding(bottom = Spacing.S1))
    if (noGuideAtAll) Text(
        "The provider gives no programme guide for these channels, so what is showing is not known. Channels named like this are usually " +
            "numbered slots the provider fills with a match only while one is on, and the name does not say which. Open one to see; " +
            "Up and Down step to the next.",
        style = Tokens.Body, color = Tokens.StatusWarning, modifier = Modifier.padding(bottom = Spacing.S1),
    )
    when {
        paged.items.isEmpty() && paged.error != null -> Text(paged.error!!, style = Tokens.BodyLarge, color = Tokens.StatusError)
        paged.items.isEmpty() && paged.exhausted -> Text("No channels in this group.", style = Tokens.BodyLarge, color = Tokens.TextSecondary)
        paged.items.isEmpty() -> Text("Loading channels…", style = Tokens.BodyLarge, color = Tokens.TextSecondary)
        else -> LazyColumn(
            state = listState, verticalArrangement = Arrangement.spacedBy(Spacing.S1),
            modifier = Modifier.fillMaxSize().enterAt(memory) { sportsRowFocusId(listState.firstVisibleItemIndex) },
        ) {
            itemsIndexed(paged.items, key = { _, c -> c.id }) { i, ch ->
                ChannelGuideRow(sportsRowFocusId(i), ch, loader, nowSec, memory, onGuide = { has -> hasGuide[ch.id] = has }, quietWhenEmpty = noGuideAtAll) { onOpenChannel(paged.items, i) }
            }
        }
    }
}

@Composable
private fun NflPane(
    state: SportsState, allGenres: List<LiveGenre>, connected: Boolean, nowSec: Long, memory: FocusMemory,
    onOpenChannel: (List<LiveChannel>, Int) -> Unit,
) {
    val nfl = state.nfl
    var more by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // The search normally runs in the background (see the shell). If Sports is opened before any pass has finished and none
    // is under way (the first minute after start-up), start one here so the viewer is not left waiting.
    LaunchedEffect(connected, allGenres.size) {
        if (connected && allGenres.isNotEmpty() && nfl.finishedAt == 0L && !nfl.running) withContext(Dispatchers.IO) { nfl.refresh(allGenres) }
    }
    LaunchedEffect(more) {
        if (more > 0 && connected && allGenres.isNotEmpty()) withContext(Dispatchers.IO) { nfl.extend(allGenres) }
    }
    val updated = if (nfl.finishedAt > 0) "Updated ${timeText(context, nfl.finishedAt / 1000)}" else ""
    val status = when {
        nfl.finishedAt == 0L && !connected -> "Waiting for the portal connection…"
        nfl.finishedAt == 0L -> "Looking for NFL games in the guide: ${nfl.current.ifBlank { "…" }} · ${nfl.checked} of ${nfl.queued} channels"
        nfl.running -> "$updated · checking again in the background (${nfl.checked} of ${nfl.queued} channels)"
        else -> "$updated · from the guides of ${if (nfl.extended) "the sports and network channels" else "channels named NFL, the US and Canadian networks, ESPN, Paramount+, Peacock and DAZN"}"
    }
    Text(status, style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 2, modifier = Modifier.padding(bottom = Spacing.S1))
    val list = remember(nfl.listings, nowSec) { nfl.current(nowSec) }
    val channels = list.map { it.channel }
    // Games are sorted to the top as they are found. A lazy list keeps its first visible row in place when rows are added
    // above it, which would push the new games off the top, so stay at the top until the viewer moves into the results.
    LaunchedEffect(list.size) {
        if (memory.focusedId?.startsWith("sports-row-") != true) state.listState.scrollToItem(0)
    }
    if (list.isEmpty() && nfl.finishedAt > 0L) Text("No NFL games in the guide right now.", style = Tokens.BodyLarge, color = Tokens.TextPrimary, modifier = Modifier.padding(bottom = Spacing.S2))
    LazyColumn(
        state = state.listState, verticalArrangement = Arrangement.spacedBy(Spacing.S1),
        modifier = Modifier.fillMaxSize().enterAt(memory) { if (list.isEmpty()) SPORTS_MORE_ID else sportsRowFocusId(state.listState.firstVisibleItemIndex) },
    ) {
        itemsIndexed(list, key = { _, l -> "${l.channel.id}:${l.programme?.startSec}" }) { i, l ->
            val heading = when {
                i == 0 && l.game -> "Games"
                !l.game && (i == 0 || list[i - 1].game) -> if (list.any { it.game }) "NFL shows" else "No games in the guide yet · NFL shows"
                else -> null
            }
            Column {
            heading?.let { Text(it, style = Tokens.TitleMedium, color = Tokens.TextPrimary, modifier = Modifier.padding(top = if (i == 0) 0.dp else Spacing.S2, bottom = Spacing.S1)) }
            OsFocusable(id = sportsRowFocusId(i), memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth(), onSelect = { onOpenChannel(channels, i) }) { _ ->
                Row(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = Spacing.S2), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(340.dp)) {
                        Text(l.channel.number, style = Tokens.Mono, color = Tokens.TextSecondary, maxLines = 1)
                        Text(PortalText.decodeEntities(l.channel.name), style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val p = l.programme
                        Text(p?.let { PortalText.decodeEntities(it.name) } ?: "Nothing listed right now", style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1)
                        val whenText = when {
                            p == null -> ""
                            l.live -> "Live now · until ${timeText(context, p.stopSec)}"
                            else -> "Starts ${timeText(context, p.startSec)}"
                        }
                        Text(whenText, style = Tokens.Body, color = if (l.live) Tokens.AccentPrimary else Tokens.TextSecondary, maxLines = 1)
                    }
                }
            }
            }
        }
        if (nfl.finishedAt > 0L && !nfl.running && !nfl.extended) item(key = "more") {
            Box(Modifier.padding(top = Spacing.S2)) {
                OsFocusable(id = SPORTS_MORE_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = { more++ }) { _ ->
                    Text("Check more channels (the other sports groups, slower)", style = Tokens.Body, color = Tokens.TextPrimary,
                        modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1))
                }
            }
        }
    }
}
