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
import io.github.ravindren_sm.openstream.data.LivePlayback
import io.github.ravindren_sm.openstream.ui.components.ChannelPreview
import io.github.ravindren_sm.openstream.data.GamesSearch
import io.github.ravindren_sm.openstream.data.League
import io.github.ravindren_sm.openstream.data.Leagues
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
    /** A league's games (found in the channel guides by the games search). */
    data class Games(val league: League) : SportsEntry { override val label get() = league.label }
    /** A live channel group about sport. */
    data class Live(val genre: LiveGenre) : SportsEntry { override val label get() = PortalText.decodeEntities(genre.title) }
    /** An on-demand category about sport; Select opens it in Movies or Shows. */
    data class OnDemand(val kind: VodKind, val category: VodCategory) : SportsEntry { override val label get() = PortalText.decodeEntities(category.title) }
}

/** What the Sports tab keeps while the viewer is elsewhere (held by the app shell, like the Live TV state). */
class SportsState(private val catalog: LiveCatalog, loader: GuideLoader, saveGames: (String) -> Unit = {}) {
    var selected: Int by mutableIntStateOf(0)
        private set
    var channels: PagedChannels by mutableStateOf(PagedChannels { CatalogResult.Failed(LiveCatalog.NOT_CONNECTED) })
        private set
    /** One search of the channel guides finds the games of every league. */
    val games = GamesSearch(catalog, loader, save = saveGames)
    val railState = LazyListState()
    val listState = LazyListState()

    /** Shows rail entry [index]: a live group's channels, or a league's games. On-demand entries are opened by the shell. */
    fun select(index: Int, entry: SportsEntry) {
        selected = index
        if (entry is SportsEntry.Live) channels = PagedChannels { page -> catalog.channels(entry.genre.id, page) }
        listState.requestScrollToItem(0)
    }
}

/** The rail: each league's games, then the live sports groups, then the on-demand sports categories (movies, then shows). */
fun sportsEntries(genres: List<LiveGenre>, movieCategories: List<VodCategory>, showCategories: List<VodCategory>): List<SportsEntry> =
    League.entries.map<League, SportsEntry> { SportsEntry.Games(it) } +
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
    /** Resolves a channel to a stream for the preview inset. */
    playback: LivePlayback,
    connected: Boolean,
    memory: FocusMemory,
    onOpenChannel: (channels: List<LiveChannel>, index: Int) -> Unit,
    onOpenOnDemand: (SportsEntry.OnDemand) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowSec = System.currentTimeMillis() / 1000 } }
    val entry = entries.getOrNull(state.selected) ?: SportsEntry.Games(League.NFL)

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
                        e is SportsEntry.Games && prev == null -> "Games in the guide"
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
                            val gameCount = if (e is SportsEntry.Games) remember(state.games.listings, nowSec) { Leagues.forLeague(state.games.current(nowSec), e.league).count { it.game } } else 0
                            Text(
                                e.label + if (e is SportsEntry.OnDemand) "  ›" else if (gameCount > 0) "  ·  $gameCount" else "", style = Tokens.BodyLarge, maxLines = 1,
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
                is SportsEntry.Games -> GamesPane(entry.league, state, allGenres, connected, nowSec, memory, playback, onOpenChannel)
                is SportsEntry.Live -> LivePane(state, loader, playback, nowSec, memory, onOpenChannel)
                is SportsEntry.OnDemand -> Unit
            }
        }
    }
}

@Composable
private fun LivePane(state: SportsState, loader: GuideLoader, playback: LivePlayback, nowSec: Long, memory: FocusMemory, onOpenChannel: (List<LiveChannel>, Int) -> Unit) {
    val paged = state.channels
    val listState = state.listState
    LaunchedEffect(paged) {
        if (paged.items.isEmpty() && !paged.exhausted) withContext(Dispatchers.IO) { paged.loadNext() }
        snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to paged.items.size }.collect { (last, loaded) ->
            if (loaded > 0 && last >= loaded - 4 && !paged.exhausted && paged.error == null) withContext(Dispatchers.IO) { paged.loadNext() }
        }
    }
    // The channel under focus, shown live in the inset beside the list.
    var previewed: LiveChannel? by remember(paged) { mutableStateOf(null) }
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
        else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
            LazyColumn(
                state = listState, verticalArrangement = Arrangement.spacedBy(Spacing.S1),
                modifier = Modifier.weight(1f).fillMaxHeight().enterAt(memory) { sportsRowFocusId(listState.firstVisibleItemIndex) },
            ) {
                itemsIndexed(paged.items, key = { _, c -> c.id }) { i, ch ->
                    ChannelGuideRow(
                        sportsRowFocusId(i), ch, loader, nowSec, memory, onGuide = { has -> hasGuide[ch.id] = has }, quietWhenEmpty = noGuideAtAll,
                        onFocusChange = { f -> if (f) previewed = ch else if (previewed?.id == ch.id) previewed = null },
                    ) { onOpenChannel(paged.items, i) }
                }
            }
            // The empty space beside the list: a small muted live picture of the channel under focus.
            Column(Modifier.width(PREVIEW_WIDTH).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                ChannelPreview(previewed, playback)
                Text(
                    if (previewed != null) "Live picture, no sound. Select opens the channel full screen." else "Move to a channel to see what it shows.",
                    style = Tokens.Body, color = Tokens.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun GamesPane(
    league: League, state: SportsState, allGenres: List<LiveGenre>, connected: Boolean, nowSec: Long, memory: FocusMemory,
    playback: LivePlayback, onOpenChannel: (List<LiveChannel>, Int) -> Unit,
) {
    val search = state.games
    var more by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // The search normally runs in the background (see the shell). If Sports is opened before any pass has finished and none
    // is under way (the first minute after start-up), start one here so the viewer is not left waiting.
    LaunchedEffect(connected, allGenres.size) {
        if (connected && allGenres.isNotEmpty() && search.finishedAt == 0L && !search.running) withContext(Dispatchers.IO) { search.refresh(allGenres) }
    }
    LaunchedEffect(more) {
        if (more > 0 && connected && allGenres.isNotEmpty()) withContext(Dispatchers.IO) { search.extend(allGenres) }
    }
    val updated = if (search.finishedAt > 0) "Updated ${timeText(context, search.finishedAt / 1000)}" else ""
    val status = when {
        search.finishedAt == 0L && !connected -> "Waiting for the portal connection…"
        search.finishedAt == 0L -> "Looking for games in the guides: ${search.current.ifBlank { "…" }} · ${search.checked} of ${search.queued} channels"
        search.running -> "$updated · checking again in the background (${search.checked} of ${search.queued} channels)"
        else -> "$updated · from the guides of ${if (search.extended) "the sports and network channels" else "the league groups, the US and Canadian networks, ESPN, Paramount+, Peacock, DAZN and Sky Sports"}"
    }
    Text(status, style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 2, modifier = Modifier.padding(bottom = Spacing.S1))
    val list = remember(search.listings, nowSec, league) { Leagues.forLeague(search.current(nowSec), league) }
    val channels = list.map { it.channel }
    var previewed: LiveChannel? by remember(league) { mutableStateOf(null) }
    // Games are sorted to the top as they are found. A lazy list keeps its first visible row in place when rows are added
    // above it, which would push the new games off the top, so stay at the top until the viewer moves into the results.
    LaunchedEffect(list.size, league) {
        if (memory.focusedId?.startsWith("sports-row-") != true) state.listState.scrollToItem(0)
    }
    if (list.isEmpty() && search.finishedAt > 0L) Text("No ${league.label} games in the guide right now.", style = Tokens.BodyLarge, color = Tokens.TextPrimary, modifier = Modifier.padding(bottom = Spacing.S2))
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
    LazyColumn(
        state = state.listState, verticalArrangement = Arrangement.spacedBy(Spacing.S1),
        modifier = Modifier.weight(1f).fillMaxHeight().enterAt(memory) { if (list.isEmpty()) SPORTS_MORE_ID else sportsRowFocusId(state.listState.firstVisibleItemIndex) },
    ) {
        itemsIndexed(list, key = { _, l -> "${l.channel.id}:${l.programme?.startSec}" }) { i, l ->
            val heading = when {
                i == 0 && l.game -> "Games"
                !l.game && (i == 0 || list[i - 1].game) -> if (list.any { it.game }) "${league.label} shows" else "No games in the guide yet · ${league.label} shows"
                else -> null
            }
            Column {
            heading?.let { Text(it, style = Tokens.TitleMedium, color = Tokens.TextPrimary, modifier = Modifier.padding(top = if (i == 0) 0.dp else Spacing.S2, bottom = Spacing.S1)) }
            OsFocusable(
                id = sportsRowFocusId(i), memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth(), onSelect = { onOpenChannel(channels, i) },
                onFocusChange = { f -> if (f) previewed = l.channel else if (previewed?.id == l.channel.id) previewed = null },
            ) { _ ->
                Row(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = Spacing.S2), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(300.dp)) {
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
        if (search.finishedAt > 0L && !search.running && !search.extended) item(key = "more") {
            Box(Modifier.padding(top = Spacing.S2)) {
                OsFocusable(id = SPORTS_MORE_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = { more++ }) { _ ->
                    Text("Check more channels (the other sports groups, slower)", style = Tokens.Body, color = Tokens.TextPrimary,
                        modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1))
                }
            }
        }
    }
    // The empty space beside the list: a small muted live picture of the channel under focus.
    Column(Modifier.width(PREVIEW_WIDTH).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
        ChannelPreview(previewed, playback)
        Text(
            if (previewed != null) "Live picture, no sound. Select opens the channel full screen." else "Move to a game to see what the channel shows.",
            style = Tokens.Body, color = Tokens.TextSecondary,
        )
    }
    }
}

/** Width of the preview inset beside a channel list (a 16:9 box of this width). */
private val PREVIEW_WIDTH = 520.dp
