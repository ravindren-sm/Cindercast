package io.github.ravindren_sm.openstream.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.CatalogResult
import io.github.ravindren_sm.openstream.data.GuideLoader
import io.github.ravindren_sm.openstream.data.GuideLogic
import io.github.ravindren_sm.openstream.data.LiveCatalog
import io.github.ravindren_sm.openstream.data.PagedChannels
import io.github.ravindren_sm.openstream.data.SportsLogic
import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.LiveGenre
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.ui.components.Choice
import io.github.ravindren_sm.openstream.ui.components.ChoicePicker
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

fun guideRowFocusId(i: Int) = "guide-row-$i"
const val GUIDE_GROUP_ID = "guide-group"
const val GUIDE_SEARCH_ID = "guide-search"
const val FAVORITES_GROUP = "fav"

/**
 * What the guide keeps while the viewer is elsewhere (held by the shell so the choice survives leaving the screen): which
 * channels it lists (the favorites, or one portal group), how far the time window has been scrolled, and the programme search.
 */
class GuideState {
    var groupId by mutableStateOf<String?>(null)
    var groupTitle by mutableStateOf("")
    var channels: PagedChannels by mutableStateOf(PagedChannels { CatalogResult.Failed(LiveCatalog.NOT_CONNECTED) })
        private set
    /** The time window starts this many half hours after the current half hour. */
    var slotOffset by mutableIntStateOf(0)
    /** The programme search is open (it replaces the grid, so coming back from the player lands in it). */
    var searchOpen by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    /** Index of the search result last opened, so focus can return to it. */
    var searchResultIndex by mutableIntStateOf(0)

    fun select(id: String, title: String, newChannels: PagedChannels) { groupId = id; groupTitle = title; channels = newChannels; slotOffset = 0 }
}

internal fun timeText(context: android.content.Context, sec: Long) = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(sec * 1000))

/**
 * TV Guide: a time grid. Rows are the channels of a group (or the favorites) with their number and name at the left; programme
 * cells run across a window of about two and a half hours that scrolls through what the loaded guides cover. The portal gives a
 * guide one channel at a time, so a row asks for its channel's programmes when it first appears on screen (one request at a
 * time) and the loader keeps them for ten minutes. Select plays the channel; the list is what zapping then moves through.
 * "Search programmes" looks through the loaded guides and the favorites' channels (see [ProgrammeSearchLayer]).
 */
@Composable
fun GuideScreen(
    state: GuideState,
    favoriteCount: Int,
    genres: List<LiveGenre>,
    loader: GuideLoader,
    connected: Boolean,
    memory: FocusMemory,
    onChooseGroup: (id: String, title: String) -> Unit,
    onOpenChannel: (channels: List<LiveChannel>, index: Int) -> Unit,
    modifier: Modifier = Modifier,
    /** The favorites' channels, searched by the programme search (asked when the search opens). */
    favoriteChannels: () -> List<LiveChannel> = { emptyList() },
) {
    var picking by remember { mutableStateOf(false) }
    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowSec = System.currentTimeMillis() / 1000 } }

    val paged = state.channels
    val listState = rememberLazyListState()
    LaunchedEffect(paged) {
        if (paged.items.isEmpty() && !paged.exhausted) withContext(Dispatchers.IO) { paged.loadNext() }
        snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to paged.items.size }.collect { (last, loaded) ->
            if (loaded > 0 && last >= loaded - 4 && !paged.exhausted && paged.error == null) withContext(Dispatchers.IO) { paged.loadNext() }
        }
    }
    // Closing the search puts focus back on its button (the grid is composed again a frame later).
    var searchWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.searchOpen) {
        if (state.searchOpen) searchWasOpen = true
        else if (searchWasOpen) { searchWasOpen = false; withFrameNanos { }; withFrameNanos { }; memory.tryFocus(GUIDE_SEARCH_ID) }
    }

    Box(modifier.fillMaxSize()) {
        if (state.searchOpen) {
            ProgrammeSearchLayer(
                state = state, loader = loader, favoriteChannels = favoriteChannels, nowSec = nowSec, memory = memory,
                onOpenChannel = onOpenChannel, onClose = { state.searchOpen = false },
            )
        } else Column(Modifier.fillMaxSize().padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S2), verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
            // Down from either button enters the first row at its channel name (geometry alone would pick a cell, or skip the row).
            val downToFirstRow = Modifier.focusProperties { down = if (paged.items.isNotEmpty()) memory.requesterFor(guideRowFocusId(0)) else FocusRequester.Default }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S3)) {
                Text("TV Guide", style = Tokens.TitleLarge, color = Tokens.TextPrimary)
                OsFocusable(id = GUIDE_GROUP_ID, memory = memory, variant = FocusVariant.BUTTON, modifier = downToFirstRow, onSelect = { picking = true }) { _ ->
                    Text("Group: ${state.groupTitle.ifEmpty { "…" }}", style = Tokens.BodyLarge, color = Tokens.AccentPrimary,
                        modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1))
                }
                OsFocusable(id = GUIDE_SEARCH_ID, memory = memory, variant = FocusVariant.BUTTON, modifier = downToFirstRow, onSelect = { state.searchOpen = true }) { _ ->
                    Text("Search programmes", style = Tokens.BodyLarge, color = Tokens.AccentPrimary,
                        modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1))
                }
            }
            when {
                paged.items.isEmpty() && paged.error == null && !paged.exhausted ->
                    Text(if (connected) "Loading channels…" else "Waiting for the portal connection…", style = Tokens.BodyLarge, color = Tokens.TextSecondary)
                paged.items.isEmpty() && paged.error != null -> Text(paged.error!!, style = Tokens.BodyLarge, color = Tokens.StatusError)
                paged.items.isEmpty() -> Text("No channels in this group.", style = Tokens.BodyLarge, color = Tokens.TextSecondary)
                else -> GuideTimeGrid(
                    state = state, channels = paged.items, listState = listState, loader = loader, nowSec = nowSec, memory = memory,
                    onOpenChannel = { i -> onOpenChannel(paged.items, i) }, modifier = Modifier.weight(1f),
                )
            }
        }
        if (picking) {
            val options = buildList {
                if (favoriteCount > 0) add(Choice(FAVORITES_GROUP, "Favorites"))
                genres.forEach { add(Choice(it.id, PortalText.decodeEntities(it.title))) }
            }
            ChoicePicker(
                "Group", options, setOf(state.groupId.orEmpty()), multi = false,
                note = "The guide is asked for one channel at a time as rows appear.", memory = memory, returnFocusId = GUIDE_GROUP_ID,
                onToggle = { id -> onChooseGroup(id, options.firstOrNull { it.id == id }?.label ?: id) }, onClose = { picking = false },
            )
        }
    }
}

/**
 * One line under a channel name in lists (Live TV): what is on now with the time left, then what is next. Asks the portal
 * for the channel's short guide when the row first appears; [loader] keeps it for ten minutes, so scrolling back is free.
 * Shows nothing until the guide arrives, and nothing for a channel without one, so rows never jump in height.
 */
@Composable
fun NowNextLine(channel: LiveChannel, loader: GuideLoader, nowSec: Long, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var programmes: List<EpgProgramme>? by remember(channel.id) { mutableStateOf(null) }
    LaunchedEffect(channel.id) {
        // A short pause first, so rows flicked past while scrolling do not each send a request.
        delay(250)
        programmes = withContext(Dispatchers.IO) { loader.load(channel) }
    }
    // Filler entries ("No details available") say nothing: treat them as no guide.
    val nn = programmes?.filterNot { SportsLogic.isPlaceholder(it.name, channel.name) }?.let { GuideLogic.nowAndNext(it, nowSec) }
    val text = when {
        nn == null -> " "
        nn.now != null -> {
            val left = ((nn.now.stopSec - nowSec) / 60).coerceAtLeast(1)
            "Now: ${PortalText.decodeEntities(nn.now.name)} · ${if (left >= 60) "${left / 60} h ${left % 60} min" else "$left min"} left" +
                (nn.next?.let { " · Next ${timeText(context, it.startSec)}: ${PortalText.decodeEntities(it.name)}" } ?: "")
        }
        nn.next != null -> "Next ${timeText(context, nn.next.startSec)}: ${PortalText.decodeEntities(nn.next.name)}"
        else -> " "
    }
    Text(text, style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1, modifier = modifier)
}

/**
 * One channel with what is on now (title, times, progress) and next. Asks the portal for the channel's short guide when the
 * row first appears (kept by [loader] for ten minutes). Used by the TV Guide and the Sports tab.
 */
@Composable
fun ChannelGuideRow(
    id: String, channel: LiveChannel, loader: GuideLoader, nowSec: Long, memory: FocusMemory, modifier: Modifier = Modifier,
    /** Told, once the guide is read, whether the channel has anything to show (a group with none can say so once, see the Sports tab). */
    onGuide: (hasGuide: Boolean) -> Unit = {},
    /** When true a channel without a guide shows nothing after its name (the screen already says so for the whole group). */
    quietWhenEmpty: Boolean = false,
    /** Told when this row gains or loses focus. */
    onFocusChange: (Boolean) -> Unit = {},
    onSelect: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // null = still asking; an empty list = the channel has no guide; failed = the portal could not be asked.
    var programmes: List<EpgProgramme>? by remember(channel.id) { mutableStateOf(null) }
    var failed by remember(channel.id) { mutableStateOf(false) }
    LaunchedEffect(channel.id) {
        val r = withContext(Dispatchers.IO) { loader.load(channel) }
        if (r == null) failed = true else {
            programmes = r
            onGuide(GuideLogic.nowAndNext(r.filterNot { SportsLogic.isPlaceholder(it.name, channel.name) }, nowSec).let { it.now != null || it.next != null })
        }
    }
    OsFocusable(id = id, memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth().then(modifier), onSelect = onSelect, onFocusChange = onFocusChange) { _ ->
        Row(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = Spacing.S2), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(340.dp)) {
                Text(channel.number, style = Tokens.Mono, color = Tokens.TextSecondary, maxLines = 1)
                Text(PortalText.decodeEntities(channel.name), style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1)
            }
            val list = programmes
            when {
                list == null -> Text(if (failed) "Guide unavailable" else "…", style = Tokens.Body, color = Tokens.TextSecondary)
                else -> {
                    // Filler entries ("No details available", common on event channels) say nothing: treat them as no guide.
                    val nn = GuideLogic.nowAndNext(list.filterNot { SportsLogic.isPlaceholder(it.name, channel.name) }, nowSec)
                    if (nn.now == null && nn.next == null) { if (!quietWhenEmpty) Text("No details in the guide", style = Tokens.Body, color = Tokens.TextSecondary) }
                    else {
                        Column(Modifier.weight(1f).padding(end = Spacing.S3), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            nn.now?.let { p ->
                                Text("${PortalText.decodeEntities(p.name)}  ${timeText(context, p.startSec)}–${timeText(context, p.stopSec)}", style = Tokens.Body, color = Tokens.TextPrimary, maxLines = 1)
                                Box(Modifier.fillMaxWidth().height(5.dp).background(Tokens.Surface3)) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth(GuideLogic.progress(p, nowSec)).background(Tokens.AccentPrimary))
                                }
                            } ?: Text("Nothing listed right now", style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1)
                            nn.next?.let { p -> Text("Next: ${PortalText.decodeEntities(p.name)}  ${timeText(context, p.startSec)}", style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}
