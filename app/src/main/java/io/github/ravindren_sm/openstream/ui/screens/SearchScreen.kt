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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.ravindren_sm.openstream.ui.focus.keepHorizontalFocus
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.PosterUrl
import io.github.ravindren_sm.openstream.data.IndexStatus
import io.github.ravindren_sm.openstream.data.FilterRules
import io.github.ravindren_sm.openstream.data.SearchFilters
import io.github.ravindren_sm.openstream.data.SearchRanking
import io.github.ravindren_sm.openstream.data.dropsCategoriesOrSubtitles
import io.github.ravindren_sm.openstream.data.SearchResults
import io.github.ravindren_sm.openstream.data.VodCategory
import io.github.ravindren_sm.openstream.data.Suggestion
import io.github.ravindren_sm.openstream.data.TitleGroup
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.provider.VodItem
import io.github.ravindren_sm.openstream.ui.components.Choice
import io.github.ravindren_sm.openstream.ui.components.ChoicePicker
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
const val SEARCH_GO_ID = "search-go"
const val SEARCH_BROWSE_ID = "search-browse"
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
    /** The filters in force, and the portal's categories they can name (adult categories left out). */
    var filters: SearchFilters by mutableStateOf(SearchFilters())
    var categories: List<VodCategory> by mutableStateOf(emptyList())
    /** The saved default filters, if any. */
    var defaultFilters: SearchFilters? by mutableStateOf(null)
    /**
     * The last results shown and the search they answer. They stay on screen while a newer search runs (or the connection
     * blinks), so the rows do not vanish and come back under the viewer's focus.
     */
    var shown: SearchResults? by mutableStateOf(null)
    var shownKey: String? by mutableStateOf(null)
    /** Set when a suggestion was picked: the rows under focus go away, so focus moves to the first result once it arrives. */
    var focusResultsWhenDone by mutableStateOf(false)
}

/** How many type-ahead suggestions are listed; fixed so the results below never jump when a search finishes. */
const val SUGGESTION_ROWS = 5

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
    onFiltersChange: (SearchFilters) -> Unit,
    onSaveDefaultFilters: () -> Unit,
    /** Lists titles with the current filters without any typed text (opens a saved search made from them). */
    onBrowseFilters: (SearchFilters) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var picker: FilterPicker? by remember { mutableStateOf(null) }
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
    LaunchedEffect(state.phase) {
        val done = state.phase as? SearchPhase.Done ?: return@LaunchedEffect
        if (!state.focusResultsWhenDone) return@LaunchedEffect
        state.focusResultsWhenDone = false
        val r = done.results
        val target = when { r.movies.isNotEmpty() -> searchMovieFocusId(0); r.shows.isNotEmpty() -> searchShowFocusId(0); r.channels.isNotEmpty() -> searchChannelFocusId(0); else -> SEARCH_INPUT_ID }
        // The result row may take a few frames to appear (seen on the TV: focus stayed on the box), so keep asking for up to
        // a second, and stop if the viewer has already moved focus themselves.
        repeat(10) {
            delay(100)
            val here = memory.focusedId
            if (here != SEARCH_INPUT_ID && here?.startsWith("nav-") != true) return@LaunchedEffect
            if (memory.tryFocus(target) && memory.focusedId == target) return@LaunchedEffect
        }
        memory.tryFocus(SEARCH_INPUT_ID)
    }
    Box(modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S2),
        verticalArrangement = Arrangement.spacedBy(Spacing.S3),
    ) {
        // Filters sit above the box so Down from the box goes straight to suggestions and results.
        FilterBar(state, memory, onOpen = { picker = it }, onChange = onFiltersChange, onSaveDefault = onSaveDefaultFilters)
        // Up from the box goes to the first button of the row just above it: the second row (Save as default, Use default) when
        // it is shown, else the filter buttons. "Clear filters" is not a target: it ends the filter row, so a viewer pressing
        // Up to reach the tabs cannot land on it and wipe the filters with one stray Select.
        val saved = state.defaultFilters
        val f = state.filters
        val upTarget = when {
            f.isActive && saved != f -> "search-filter-save"
            saved != null && saved != f -> "search-filter-usedefault"
            else -> "search-filter-language"
        }
        OsTextField(
            id = SEARCH_INPUT_ID, memory = memory, value = state.query, onValueChange = { state.query = it }, upId = upTarget,
            label = "Search", hint = "Movies, shows and channels. Back closes the keyboard; then Down reaches the results.", imeAction = ImeAction.Done,
            onDone = { state.submitTick++ },
        )
        // A visible way to run the search (the keyboard's Done key and typing also run it), and a way to use the filters on their
        // own: a search needs at least two typed letters, so filters alone list nothing.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
            OsFocusable(id = SEARCH_GO_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = { state.submitTick++ }) { _ ->
                Text("Search", style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1, modifier = Modifier.padding(horizontal = Spacing.S3, vertical = Spacing.S1))
            }
            if (state.filters.isActive) OsFocusable(id = SEARCH_BROWSE_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = { onBrowseFilters(state.filters) }) { _ ->
                Text("Browse titles with these filters", style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1, modifier = Modifier.padding(horizontal = Spacing.S3, vertical = Spacing.S1))
            }
        }
        if (state.filters.isActive && state.query.trim().length < SearchRanking.MIN_QUERY) Text(
            "Filters narrow a search: type at least two letters, or choose \"Browse titles with these filters\" to list matching titles without typing" +
                if (dropsCategoriesOrSubtitles(state.filters)) " (category and subtitle choices are not used there)." else ".",
            style = Tokens.Body, color = Tokens.TextSecondary,
        )
        if (state.suggestions.isNotEmpty()) {
            Text(if (state.query.isBlank()) "Recent searches" else "Suggestions", style = Tokens.TitleMedium, color = Tokens.TextPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                state.suggestions.take(SUGGESTION_ROWS).forEachIndexed { i, sug ->
                    OsFocusable(id = searchSuggestionFocusId(i), memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth(), onSelect = {
                        // The suggestion rows change under focus once the search runs; hold focus on the box, then move to the results.
                        state.focusResultsWhenDone = true; memory.tryFocus(SEARCH_INPUT_ID); onPickSuggestion(sug)
                    }) { _ ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.S2, vertical = Spacing.S1), verticalAlignment = Alignment.CenterVertically) {
                            Text(PortalText.decodeEntities(sug.text), style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                            Text(sug.source.label, style = Tokens.Body, color = Tokens.TextSecondary)
                        }
                    }
                }
            }
        }
        // One status line whose place never moves, then the latest results (kept while a newer search runs).
        val phase = state.phase
        val shown = (phase as? SearchPhase.Done)?.results ?: state.shown?.takeIf { state.query.trim().length >= 2 }
        when (phase) {
            SearchPhase.Idle -> if (shown == null) Text(
                if (connected) "Type at least two letters." else "Waiting for the portal connection…",
                style = Tokens.Body, color = Tokens.TextSecondary,
            )
            SearchPhase.Loading -> Text("Searching…", style = Tokens.Body, color = Tokens.TextSecondary)
            is SearchPhase.Failed -> Text(phase.message, style = Tokens.BodyLarge, color = Tokens.StatusError)
            is SearchPhase.Done -> Unit
        }
        if (shown != null && phase !is SearchPhase.Failed) {
            Results(shown, state.query.trim(), state.filters.isActive, state.filters.summary { id -> state.categories.firstOrNull { it.id == id }?.title }, portalUrl, memory, isFavoriteVod, isFavoriteChannel, onOpenChannel, onOpenMovie, onOpenShow)
        }
        IndexPanel(state.index, memory, onEnableIndex, onPauseIndex)
    }
    picker?.let { kind -> FilterPickerHost(kind, state, memory, onFiltersChange) { picker = null } }
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
    r: SearchResults, query: String, filtersOn: Boolean, filterSummary: String, portalUrl: String, memory: FocusMemory, isFavoriteVod: (VodItem) -> Boolean,
    isFavoriteChannel: (LiveChannel) -> Boolean, onOpenChannel: (Int) -> Unit, onOpenMovie: (TitleGroup) -> Unit, onOpenShow: (TitleGroup) -> Unit,
) {
    if (r.isEmpty) {
        Text("No matches for “$query”" + if (filtersOn) " with these filters" else "", style = Tokens.BodyLarge, color = Tokens.TextSecondary)
    }
    if (r.relaxed) Text(
        "Nothing matched “$query” with your filters ($filterSummary). These match without them; use Clear filters or the Any choices above to search this way.",
        style = Tokens.BodyLarge, color = Tokens.StatusWarning,
    )
    r.note?.let { Text(it, style = Tokens.Body, color = Tokens.StatusWarning) }
    if (r.channels.isNotEmpty()) {
        Text("Channels", style = Tokens.TitleMedium, color = Tokens.TextPrimary)
        LazyRow(modifier = Modifier.keepHorizontalFocus(memory, searchChannelFocusId(0)), horizontalArrangement = Arrangement.spacedBy(Spacing.S3), contentPadding = Spacing.CardRow) {
            itemsIndexed(r.channels, key = { _, c -> c.id }) { i, c ->
                ChannelCard(searchChannelFocusId(i), c.number, c.name + if (isFavoriteChannel(c)) "  $HEART" else "", memory, { onOpenChannel(i) })
            }
        }
    }
    if (r.channelsFailed) Text("Channels could not be searched.", style = Tokens.Body, color = Tokens.StatusWarning)
    PosterRow("Movies", r.movies, ::searchMovieFocusId, query, portalUrl, memory, isFavoriteVod, onOpenMovie)
    PosterRow("Shows", r.shows, ::searchShowFocusId, query, portalUrl, memory, isFavoriteVod, onOpenShow)
}

@Composable
private fun PosterRow(
    heading: String, items: List<TitleGroup>, idOf: (Int) -> String, query: String, portalUrl: String, memory: FocusMemory,
    isFavorite: (VodItem) -> Boolean, onOpen: (TitleGroup) -> Unit,
) {
    if (items.isEmpty()) return
    Text(heading, style = Tokens.TitleMedium, color = Tokens.TextPrimary)
    LazyRow(modifier = Modifier.keepHorizontalFocus(memory, idOf(0)), horizontalArrangement = Arrangement.spacedBy(Spacing.S3), contentPadding = Spacing.CardRow) {
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
                    // Say why a title whose name lacks the text was found (for example a cast member's name).
                    // The line is always there (blank when the name shows the match) so every card in the row has the same
                    // height; a taller card would count as "below" its neighbours and catch Down on the bottom row.
                    Text(io.github.ravindren_sm.openstream.data.SearchRanking.matchReason(v, query)?.let { "Found in: $it" } ?: " ",
                        style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1, modifier = Modifier.padding(start = Spacing.S1, end = Spacing.S1, bottom = Spacing.S1))
                }
            }
        }
    }
}


/** Which filter list is open. */
enum class FilterPicker { LANGUAGE, GENRE, CATEGORY, RATING }

private fun chipId(kind: FilterPicker) = "search-filter-${kind.name.lowercase()}"

private fun chipLabel(name: String, chosen: List<String>) = when {
    chosen.isEmpty() -> name
    chosen.size == 1 -> "$name: ${chosen[0]}"
    else -> "$name: ${chosen[0]} +${chosen.size - 1}"
}

/**
 * Filter buttons under the search box. Language, genre, category and rating combine (all must hold); the buttons show what is
 * chosen. Save as default keeps the current set for every later visit; Clear turns them all off.
 */
@Composable
private fun FilterBar(
    state: SearchState, memory: FocusMemory, onOpen: (FilterPicker) -> Unit, onChange: (SearchFilters) -> Unit, onSaveDefault: () -> Unit,
) {
    val f = state.filters
    val titles = state.categories.associate { it.id to it.title }
    // Save, Use default and Clear disappear once they have done their job; without this focus would fall to the tab bar.
    // Focus goes to the search box (the next thing the viewer does is search), not to Language: after Clear filters the
    // only button left is "Use default", directly under Language, so Down then Select there put the filters straight back.
    var refocus by remember { mutableIntStateOf(0) }
    LaunchedEffect(refocus) {
        if (refocus > 0) { delay(200); memory.tryFocusFirst(SEARCH_INPUT_ID, chipId(FilterPicker.LANGUAGE)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S1)) {
            FilterChip(chipId(FilterPicker.LANGUAGE), chipLabel("Language", f.languages.sorted()), f.languages.isNotEmpty(), memory) { onOpen(FilterPicker.LANGUAGE) }
            FilterChip(chipId(FilterPicker.GENRE), chipLabel("Genre", f.genres.sorted()), f.genres.isNotEmpty(), memory) { onOpen(FilterPicker.GENRE) }
            FilterChip(chipId(FilterPicker.CATEGORY), chipLabel("Category", f.categoryIds.mapNotNull { titles[it] }.sorted()), f.categoryIds.isNotEmpty(), memory) { onOpen(FilterPicker.CATEGORY) }
            FilterChip(chipId(FilterPicker.RATING), if (f.minRating == null) "Rating" else "Rating ${"%.0f".format(f.minRating)}+", f.minRating != null, memory) { onOpen(FilterPicker.RATING) }
            FilterChip("search-filter-subs", if (f.englishSubtitles) "✓ English subtitles" else "English subtitles", f.englishSubtitles, memory) { onChange(f.copy(englishSubtitles = !f.englishSubtitles)) }
            if (f.isActive) FilterChip("search-filter-clear", "Clear filters", false, memory) { onChange(SearchFilters()); refocus++ }
        }
        val saved = state.defaultFilters
        if (saved != f && (f.isActive || saved != null)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                if (f.isActive && saved != f) FilterChip("search-filter-save", "Save as default", false, memory, onSelect = { onSaveDefault(); refocus++ })
                if (saved != null && saved != f) FilterChip("search-filter-usedefault", "Use default (${saved.summary(titles::get)})", false, memory) { onChange(saved); refocus++ }
            }
        }
        if (f.isActive) Text("Filters apply to movies and shows; channels are hidden while a filter is on.", style = Tokens.Body, color = Tokens.TextSecondary)
        if (f.englishSubtitles) Text("English subtitles: only titles whose name or category says so; the portal does not list subtitles in its catalog, so some titles that have them are missed.", style = Tokens.Body, color = Tokens.TextSecondary)
    }
}

@Composable
private fun FilterChip(id: String, label: String, active: Boolean, memory: FocusMemory, onSelect: () -> Unit) {
    OsFocusable(id = id, memory = memory, variant = FocusVariant.BUTTON, onSelect = onSelect) { _ ->
        Text(label, style = Tokens.Body, color = if (active) Tokens.AccentPrimary else Tokens.TextPrimary, maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1))
    }
}

/** Builds the options for [kind] from the state and shows the picker. */
@Composable
private fun FilterPickerHost(kind: FilterPicker, state: SearchState, memory: FocusMemory, onChange: (SearchFilters) -> Unit, onClose: () -> Unit) {
    val f = state.filters
    when (kind) {
        FilterPicker.LANGUAGE -> ChoicePicker(
            "Language", FilterRules.languageOptions(state.categories).map { Choice(it, it) }, f.languages, multi = true,
            note = if (state.categories.isEmpty()) "The portal's categories have not loaded yet." else "Languages are read from category names.",
            memory = memory, returnFocusId = chipId(kind),
            onToggle = { id -> onChange(f.copy(languages = f.languages.toggled(id))) }, onClose = onClose,
            clearLabel = "Any language", onClear = { onChange(f.copy(languages = emptySet())) },
        )
        FilterPicker.GENRE -> ChoicePicker(
            "Genre", SearchFilters.GENRES.map { Choice(it, it) }, f.genres, multi = true, note = "Matches the genres each title lists.",
            memory = memory, returnFocusId = chipId(kind), onToggle = { id -> onChange(f.copy(genres = f.genres.toggled(id))) }, onClose = onClose,
            clearLabel = "Any genre", onClear = { onChange(f.copy(genres = emptySet())) },
        )
        FilterPicker.CATEGORY -> ChoicePicker(
            "Category", state.categories.map { Choice(it.id, PortalText.decodeEntities(it.title)) }, f.categoryIds, multi = true,
            note = if (state.categories.isEmpty()) "The portal's categories have not loaded yet." else "Titles in any of the chosen categories.",
            memory = memory, returnFocusId = chipId(kind), onToggle = { id -> onChange(f.copy(categoryIds = f.categoryIds.toggled(id))) }, onClose = onClose,
            clearLabel = "Any category", onClear = { onChange(f.copy(categoryIds = emptySet())) },
        )
        FilterPicker.RATING -> ChoicePicker(
            "Minimum rating", listOf(Choice("any", "Any rating")) + SearchFilters.RATING_STEPS.map { Choice("$it", "${"%.0f".format(it)} and above") },
            setOf(f.minRating?.toString() ?: "any"), multi = false, note = "The portal's own rating: IMDb when it has one, otherwise Kinopoisk. Titles with no rating are left out.",
            memory = memory, returnFocusId = chipId(kind), onToggle = { id -> onChange(f.copy(minRating = id.toDoubleOrNull())) }, onClose = onClose,
        )
    }
}

private fun Set<String>.toggled(id: String): Set<String> = if (id in this) this - id else this + id
