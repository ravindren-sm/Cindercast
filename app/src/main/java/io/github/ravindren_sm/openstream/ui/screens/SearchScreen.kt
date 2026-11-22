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
import io.github.ravindren_sm.openstream.data.SearchResults
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
}

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
    onOpenMovie: (VodItem) -> Unit,
    onOpenShow: (VodItem) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        when (val phase = state.phase) {
            SearchPhase.Idle -> Text(
                if (connected) "Type at least two letters." else "Waiting for the portal connection…",
                style = Tokens.Body, color = Tokens.TextSecondary,
            )
            SearchPhase.Loading -> Text("Searching…", style = Tokens.BodyLarge, color = Tokens.TextSecondary)
            is SearchPhase.Failed -> Text(phase.message, style = Tokens.BodyLarge, color = Tokens.StatusError)
            is SearchPhase.Done -> Results(phase.results, state.query.trim(), portalUrl, memory, isFavoriteVod, isFavoriteChannel, onOpenChannel, onOpenMovie, onOpenShow)
        }
    }
}

@Composable
private fun Results(
    r: SearchResults, query: String, portalUrl: String, memory: FocusMemory, isFavoriteVod: (VodItem) -> Boolean,
    isFavoriteChannel: (LiveChannel) -> Boolean, onOpenChannel: (Int) -> Unit, onOpenMovie: (VodItem) -> Unit, onOpenShow: (VodItem) -> Unit,
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
    heading: String, items: List<VodItem>, idOf: (Int) -> String, portalUrl: String, memory: FocusMemory,
    isFavorite: (VodItem) -> Boolean, onOpen: (VodItem) -> Unit,
) {
    if (items.isEmpty()) return
    Text(heading, style = Tokens.TitleMedium, color = Tokens.TextPrimary)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S3), contentPadding = PaddingValues(end = Spacing.S1, top = Spacing.S1, bottom = Spacing.S1)) {
        itemsIndexed(items, key = { _, v -> v.id }) { i, v ->
            OsFocusable(id = idOf(i), memory = memory, variant = FocusVariant.CARD, onSelect = { onOpen(v) }, modifier = Modifier.width(190.dp)) { _ ->
                Column(Modifier.background(Tokens.Surface1)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                        Poster(PosterUrl.resolve(v.posterUri, portalUrl), Modifier.fillMaxSize(), targetWidthPx = 300)
                        if (isFavorite(v)) Text(HEART, style = Tokens.BodyLarge, color = Tokens.StatusError, modifier = Modifier.align(Alignment.TopStart).padding(Spacing.S1))
                    }
                    Text(PortalText.decodeEntities(v.name), style = Tokens.Body, color = Tokens.TextPrimary, maxLines = 1, modifier = Modifier.padding(Spacing.S1))
                }
            }
        }
    }
}
