package io.github.ravindren_sm.openstream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.PosterUrl
import io.github.ravindren_sm.openstream.data.IndexStatus
import io.github.ravindren_sm.openstream.data.SearchResults
import io.github.ravindren_sm.openstream.data.Suggestion
import io.github.ravindren_sm.openstream.data.TitleGroup
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.provider.VodItem
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.components.OsTextField
import io.github.ravindren_sm.openstream.ui.components.Poster
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import io.github.ravindren_sm.openstream.ui.components.HEART

const val SEARCH_INPUT_ID = "search-input"
fun searchChannelFocusId(i: Int) = "search-ch-$i"
fun searchMovieFocusId(i: Int) = "search-mv-$i"
fun searchShowFocusId(i: Int) = "search-sh-$i"

/** Where a search stands. */
sealed interface SearchPhase {
    data object Idle : SearchPhase
    data object Loading : SearchPhase
    class Done(val results: SearchResults) : SearchPhase
    class Failed(val message: String) : SearchPhase
}

/** The search box text and the last outcome, kept by the shell so results survive opening one and coming back. */
class SearchState {
    var query by mutableStateOf("")
    var phase: SearchPhase by mutableStateOf(SearchPhase.Idle)
    /** Bumped when the viewer presses Done on the keyboard, so the search starts at once instead of after a pause. */
    var submitTick by mutableIntStateOf(0)
    /** Type-ahead suggestions from text the device already holds (recent searches, favorites, history, channels, the title index). */
    var suggestions: List<Suggestion> by mutableStateOf(emptyList())
    var index: IndexStatus by mutableStateOf(IndexStatus.OFF)
}

fun searchSuggestionFocusId(i: Int) = "search-sug-$i"

/**
 * Search: a text box, then results grouped as Channels, Movies and Shows. The search runs a moment after typing stops,
 * or at once on Done. Select opens a result like the other screens do.
 */
@Composable
fun SearchScreen(
    state: SearchState,
    portalUrl: String,
    memory: FocusMemory,
    connected: Boolean,
    isFavoriteVod: (VodItem) -> Boolean,
    isFavoriteChannel: (LiveChannel) -> Boolean,
    onOpenChannel: (index: Int) -> Unit,
    onOpenMovie: (TitleGroup) -> Unit,
    onOpenShow: (TitleGroup) -> Unit,
    onPickSuggestion: (Suggestion) -> Unit,
    onEnableIndex: () -> Unit,
    onPauseIndex: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // When the index button disappears (indexing finished or paused) focus would fall to the tab bar: bring it back to the box.
    LaunchedEffect(state.index.enabled, state.index.complete) {
        delay(150)
        if (memory.focusedId?.startsWith("nav-") != false) memory.tryFocus(SEARCH_INPUT_ID)
    }
    // The screen's first focus request can land before the text box is ready and leave focus on the tab bar:
    // ask again a few times while focus is still up there.
    LaunchedEffect(Unit) {
        repeat(6) {
            delay(80)
            if (memory.focusedId?.startsWith("nav-") != false) memory.tryFocus(SEARCH_INPUT_ID) else return@LaunchedEffect
        }
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S2),
        verticalArrangement = Arrangement.spacedBy(Spacing.S3),
    ) {
        OsTextField(
            id = SEARCH_INPUT_ID, memory = memory, value = state.query, onValueChange = { state.query = it },
            label = "Search", hint = "Movies, shows and channels", imeAction = ImeAction.Done,
            onDone = { state.submitTick++ },
        )
        if (state.suggestions.isNotEmpty()) {
            Text(if (state.query.isBlank()) "Recent searches" else "Suggestions", style = Tokens.TitleMedium, color = Tokens.TextPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                state.suggestions.take(if (state.phase is SearchPhase.Done) 3 else 6).forEachIndexed { i, sug ->
                    OsFocusable(id = searchSuggestionFocusId(i), memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth(), onSelect = { onPickSuggestion(sug) }) { _ ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.S2, vertical = Spacing.S1), verticalAlignment = Alignment.CenterVertically) {
                            Text(PortalText.decodeEntities(sug.text), style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                            Text(sug.source.label, style = Tokens.Body, color = Tokens.TextSecondary)
                        }
                    }
                }
            }
        }
        when (val phase = state.phase) {
            SearchPhase.Idle -> Text(
                if (connected) "Type at least two letters." else "Waiting for the portal connection…",
                style = Tokens.Body, color = Tokens.TextSecondary,
            )
            SearchPhase.Loading -> Text("Searching…", style = Tokens.BodyLarge, color = Tokens.TextSecondary)
            is SearchPhase.Failed -> Text(phase.message, style = Tokens.BodyLarge, color = Tokens.StatusError)
            is SearchPhase.Done -> Results(phase.results, state.query.trim(), portalUrl, memory, isFavoriteVod, isFavoriteChannel, onOpenChannel, onOpenMovie, onOpenShow)
        }
        IndexPanel(state.index, memory, onEnableIndex, onPauseIndex)
    }
}

/**
 * The local title index: off (offer to build it), building (progress and a way to pause), or ready. It is opt-in because
 * reading the whole catalog takes a couple of hours of gentle background requests while the app is open.
 */
@Composable
private fun IndexPanel(status: IndexStatus, memory: FocusMemory, onEnable: () -> Unit, onPause: () -> Unit) {
    val titles = "%,d".format(status.titles)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
        when {
            status.complete && status.enabled -> Text("Title index ready · $titles titles. Suggestions use it.", style = Tokens.Body, color = Tokens.TextSecondary)
            status.enabled -> {
                Text("Building the title index: ${status.percent}% · $titles titles so far. It runs while the app is open and not playing.", style = Tokens.Body, color = Tokens.TextSecondary)
                OsFocusable(id = "search-index-pause", memory = memory, variant = FocusVariant.BUTTON, onSelect = onPause) { _ ->
                    Text("Pause indexing", style = Tokens.BodyLarge, color = Tokens.TextPrimary, modifier = Modifier.padding(horizontal = Spacing.S3, vertical = Spacing.S1))
                }
            }
            else -> {
                Text(
                    if (status.titles > 0) "Title index paused at ${status.percent}% · $titles titles." else
                        "Build a title index so search can suggest instantly: it saves about 130,000 title names (around 20 MB) on this device, reading one page at a time over a couple of hours while the app is open.",
                    style = Tokens.Body, color = Tokens.TextSecondary,
                )
                OsFocusable(id = "search-index-enable", memory = memory, variant = FocusVariant.BUTTON, onSelect = onEnable) { _ ->
                    Text(if (status.titles > 0) "Resume indexing" else "Build title index", style = Tokens.BodyLarge, color = Tokens.TextPrimary, modifier = Modifier.padding(horizontal = Spacing.S3, vertical = Spacing.S1))
                }
            }
        }
        status.message?.let { Text(it, style = Tokens.Body, color = Tokens.StatusWarning) }
    }
}

@Composable
private fun Results(
    r: SearchResults, query: String, portalUrl: String, memory: FocusMemory, isFavoriteVod: (VodItem) -> Boolean,
    isFavoriteChannel: (LiveChannel) -> Boolean, onOpenChannel: (Int) -> Unit, onOpenMovie: (TitleGroup) -> Unit, onOpenShow: (TitleGroup) -> Unit,
) {
    if (r.isEmpty) {
        Text("No matches for “$query”", style = Tokens.BodyLarge, color = Tokens.TextSecondary)
    }
    if (r.channels.isNotEmpty()) {
        Text("Channels", style = Tokens.TitleMedium, color = Tokens.TextPrimary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S3), contentPadding = PaddingValues(end = Spacing.S1, top = Spacing.S1, bottom = Spacing.S1)) {
            itemsIndexed(r.channels, key = { _, c -> c.id }) { i, c ->
                ChannelCard(searchChannelFocusId(i), c.number, c.name + if (isFavoriteChannel(c)) "  $HEART" else "", memory, { onOpenChannel(i) })
            }
        }
    }
    if (r.channelsFailed) Text("Channels could not be searched.", style = Tokens.Body, color = Tokens.StatusWarning)
    PosterRow("Movies", r.movies, ::searchMovieFocusId, portalUrl, memory, isFavoriteVod, onOpenMovie)
    PosterRow("Shows", r.shows, ::searchShowFocusId, portalUrl, memory, isFavoriteVod, onOpenShow)
}

@Composable
private fun PosterRow(
    heading: String, items: List<TitleGroup>, idOf: (Int) -> String, portalUrl: String, memory: FocusMemory,
    isFavorite: (VodItem) -> Boolean, onOpen: (TitleGroup) -> Unit,
) {
    if (items.isEmpty()) return
    Text(heading, style = Tokens.TitleMedium, color = Tokens.TextPrimary)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S3), contentPadding = PaddingValues(end = Spacing.S1, top = Spacing.S1, bottom = Spacing.S1)) {
        itemsIndexed(items, key = { _, g -> g.best.id }) { i, g ->
            val v = g.best
            OsFocusable(id = idOf(i), memory = memory, variant = FocusVariant.CARD, onSelect = { onOpen(g) }, modifier = Modifier.width(190.dp)) { _ ->
                Column(Modifier.background(Tokens.Surface1)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                        Poster(PosterUrl.resolve(v.posterUri, portalUrl), Modifier.fillMaxSize(), targetWidthPx = 300)
                        if (g.versions.any(isFavorite)) Text(HEART, style = Tokens.BodyLarge, color = Tokens.StatusError, modifier = Modifier.align(Alignment.TopStart).padding(Spacing.S1))
                        // Merged entries: say how many versions there are.
                        if (g.count > 1) Text("${g.count} versions", style = Tokens.Body, color = Tokens.TextPrimary,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.S1).background(Tokens.BgCanvas.copy(alpha = 0.85f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).padding(horizontal = Spacing.S1, vertical = 2.dp))
                    }
                    Text(PortalText.decodeEntities(v.name), style = Tokens.Body, color = Tokens.TextPrimary, maxLines = 1, modifier = Modifier.padding(Spacing.S1))
                }
            }
        }
    }
}
