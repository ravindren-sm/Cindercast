package io.github.ravindren_sm.openstream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.CatalogResult
import io.github.ravindren_sm.openstream.data.LiveCatalog
import io.github.ravindren_sm.openstream.data.BrowseSort
import io.github.ravindren_sm.openstream.data.ContentPolicy
import io.github.ravindren_sm.openstream.data.KidsRules
import io.github.ravindren_sm.openstream.data.DefaultScope
import io.github.ravindren_sm.openstream.provider.VodPage
import io.github.ravindren_sm.openstream.data.IndexedTitles
import io.github.ravindren_sm.openstream.data.PagedTitles
import io.github.ravindren_sm.openstream.ui.components.Choice
import io.github.ravindren_sm.openstream.ui.components.ChoicePicker
import androidx.compose.runtime.mutableIntStateOf
import io.github.ravindren_sm.openstream.data.PosterUrl
import io.github.ravindren_sm.openstream.data.VodCatalog
import io.github.ravindren_sm.openstream.data.VodCategory
import io.github.ravindren_sm.openstream.data.VodKind
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.provider.ShownRating
import io.github.ravindren_sm.openstream.provider.VodItem
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.HEART
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
import io.github.ravindren_sm.openstream.ui.components.onMenuKey
import io.github.ravindren_sm.openstream.ui.components.Poster
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.theme.Radii
import io.github.ravindren_sm.openstream.ui.theme.Spacing
import io.github.ravindren_sm.openstream.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Browsing state for one VOD tab (Movies or Shows), held by the shell so opening a title and coming back
 * keeps the categories, the loaded pages and both scroll positions.
 */
class VodBrowseState(private val catalog: VodCatalog, val kind: VodKind) {
    private var allCategories: List<VodCategory> = emptyList()
    private var scope: DefaultScope? = null
    var categories: List<VodCategory> by mutableStateOf(emptyList())
        private set
    var categoriesLoaded: Boolean by mutableStateOf(false)
        private set
    var categoriesError: String? by mutableStateOf(null)
        private set
    var selected: Int by mutableIntStateOf(0)
        private set
    var titles: PagedTitles by mutableStateOf(PagedTitles { CatalogResult.Failed(LiveCatalog.NOT_CONNECTED) })
        private set

    /** The order and rating floor in force for this view. The rating floor starts from the profile's default (Settings). */
    var sort: BrowseSort by mutableStateOf(BrowseSort.NEWEST)
        private set
    var minRating: Double? by mutableStateOf(null)
        private set
    var hideUnrated: Boolean = false
    /** A genre and an earliest year chosen for this view (read from the on-device copy; they need it). */
    var genre: String? by mutableStateOf(null)
        private set
    var yearFrom: Int? by mutableStateOf(null)
        private set
    private val viewFilter: Boolean get() = genre != null || yearFrom != null

    /** Set by the shell: the on-device copy of the catalog, and which categories it holds completely. */
    var indexed: IndexedTitles? = null
    /** Adds the optional online tags to each page read from the portal. */
    var enricher: ((List<VodItem>) -> List<VodItem>)? = null
    var ready: (String) -> Boolean = { false }
    /** The list on screen is read from the device (sorted and filtered there), not from the portal. */
    var usingIndex: Boolean by mutableStateOf(false)
        private set
    /** A sort was chosen that needs the on-device copy of this category, which is not there yet. */
    var sortUnavailable: Boolean by mutableStateOf(false)
        private set

    val categoryListState = LazyListState()
    val gridState = LazyGridState()

    /** Loads the categories and selects the first. Blocking: call off the main thread. */
    fun loadCategories() {
        when (val r = catalog.categories(kind)) {
            is CatalogResult.Ok -> { allCategories = r.value; categories = visible(r.value); categoriesLoaded = true; categoriesError = null; select(0, resetScroll = false) }
            is CatalogResult.Failed -> categoriesError = r.message
        }
    }

    /** Shows the titles of category [index], from the first page. */
    fun select(index: Int, resetScroll: Boolean = true) {
        if (categories.getOrNull(index) == null) return
        selected = index
        reload()
        if (resetScroll) gridState.requestScrollToItem(0)
    }

    fun changeSort(newSort: BrowseSort) { sort = newSort; reload(); gridState.requestScrollToItem(0) }
    fun changeGenre(value: String?) { genre = value; reload(); gridState.requestScrollToItem(0) }
    fun changeYearFrom(value: Int?) { yearFrom = value; reload(); gridState.requestScrollToItem(0) }
    fun changeMinRating(value: Double?) { minRating = value; reload(); gridState.requestScrollToItem(0) }

    /** A Kids profile's own rules, and the profile's rule for which categories are listed (adult categories, a Kids allow-list). */
    private var kids: KidsRules? = null
    val isKidsList: Boolean get() = kids != null
    private var categoryRule: (VodCategory) -> Boolean = { true }

    private fun visible(all: List<VodCategory>): List<VodCategory> {
        val allowed = all.filter(categoryRule)
        return scope?.takeIf { it.active }?.let { sc -> allowed.filter(sc::categoryAllowed) } ?: allowed
    }

    /** Sets which categories the profile may see (called when the profile changes); the list is rebuilt from the first category. */
    fun applyCategoryRule(rule: (VodCategory) -> Boolean) {
        categoryRule = rule
        if (!categoriesLoaded) return
        categories = visible(allCategories)
        select(0, resetScroll = true)
    }

    /** Applies (or, with null, removes) the saved search defaults to this list: fewer categories, and a filter on each title. */
    fun applyScope(newScope: DefaultScope?) {
        scope = newScope
        if (!categoriesLoaded) return
        categories = visible(allCategories)
        select(0, resetScroll = true)
    }

    /** Takes the profile's default rating floor (called when Settings changes it and when the screen starts). */
    fun applyProfilePolicy(policy: ContentPolicy) { minRating = policy.minRating; hideUnrated = policy.hideUnrated; kids = policy.kids; if (categoriesLoaded) reload() }

    /** Drops the loaded pages (memory is tight while a video plays); the list is rebuilt, from its first page, when the tab is shown again. */
    fun release() { if (categoriesLoaded) { reload(); gridState.requestScrollToItem(0) } }

    /** Builds the list for the selected category: from the device copy when a sort, a rating floor or a default needs it and it is complete, else from the portal. */
    private fun reload() {
        val category = categories.getOrNull(selected) ?: run { titles = PagedTitles { CatalogResult.Ok(VodPage(1, emptyList(), 0, 14)) }; return }
        val policy = ContentPolicy(minRating, hideUnrated, kids)
        val sc = scope?.takeIf { it.active }
        val local = indexed
        val haveCopy = local != null && kids == null && ready(category.id)        // the device copy has no age or word filter, so a Kids profile reads the portal's pages
        val genres = genre?.let { listOf(it) } ?: sc?.indexGenres
        val deviceCanDoIt = sc == null || sc.indexGenres != null || genre != null                      // the subtitle wording check needs the portal's own pages
        if (haveCopy && deviceCanDoIt && (sort != BrowseSort.NEWEST || policy.active || sc != null || viewFilter)) {
            usingIndex = true; sortUnavailable = false
            // The defaults' rating floor also leaves out unrated titles, as it does in search.
            val floor = listOfNotNull(policy.minRating, sc?.minRating).maxOrNull()
            val effective = ContentPolicy(floor, policy.hideUnrated || sc?.minRating != null)
            titles = PagedTitles { page -> local!!.page(category.id, sort, effective, page, genres.orEmpty(), yearFrom) }
        } else {
            usingIndex = false; sortUnavailable = sort != BrowseSort.NEWEST || viewFilter
            // The portal's pages, with the rating floor and the defaults applied to each page as it arrives.
            titles = PagedTitles(keep = { policy.passes(it) && (sc?.titlePasses(it) ?: true) }) { page -> catalog.titles(category.id, page).let { r -> if (r is CatalogResult.Ok && enricher != null) CatalogResult.Ok(VodPage(r.value.page, enricher!!(r.value.items), r.value.totalItems, r.value.pageSize)) else r } }
        }
    }
}

fun categoryFocusId(index: Int) = "vodcat-$index"
fun titleFocusId(index: Int) = "title-$index"

private const val COLUMNS = 5
private const val PREFETCH_TITLES = 10   // two rows ahead

/** A VOD tab: category rail on the left, a paged poster grid on the right, titles loaded as the user nears the end. */
@Composable
fun VodBrowseScreen(
    state: VodBrowseState,
    connected: Boolean,
    portalUrl: String,
    memory: FocusMemory,
    onOpen: (index: Int, item: VodItem) -> Unit,
    isFavorite: (VodItem) -> Boolean = { false },
    /** Categories shown as "New in ..." rows on Home; Menu on a category rail row pins or unpins it. */
    isPinned: (VodCategory) -> Boolean = { false },
    onTogglePin: (VodCategory) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var picker by remember { mutableIntStateOf(0) }       // 0 none, 1 sort, 2 rating floor, 3 genre, 4 earliest year
    LaunchedEffect(connected) {
        if (connected && !state.categoriesLoaded) withContext(Dispatchers.IO) { state.loadCategories() }
    }

    Box(modifier.fillMaxSize()) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S4),
    ) {
        Column(Modifier.width(440.dp).fillMaxHeight()) {
            when {
                state.categoriesLoaded && state.categories.isEmpty() -> Message("No categories.")
                state.categoriesLoaded -> LazyColumn(state = state.categoryListState, verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                    itemsIndexed(state.categories, key = { _, c -> c.id }) { index, category ->
                        OsFocusable(
                            id = categoryFocusId(index), memory = memory, variant = FocusVariant.ROW,
                            modifier = Modifier.fillMaxWidth().onMenuKey { onTogglePin(category) }, onSelect = { state.select(index) },
                        ) { _ ->
                            Text(
                                category.title + if (isPinned(category)) "  ✓" else "", style = Tokens.BodyLarge, maxLines = 1,
                                color = if (index == state.selected) Tokens.AccentPrimary else Tokens.TextPrimary,
                                modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1 + 4.dp),
                            )
                        }
                    }
                }
                state.categoriesError != null -> RetryText(state.categoriesError!!, memory) {
                    scope.launch {
                        withContext(Dispatchers.IO) { state.loadCategories() }
                        if (state.categoriesLoaded) { withFrameNanos { }; memory.tryFocus(categoryFocusId(0)) }
                    }
                }
                else -> Message(if (connected) "Loading categories…" else "Waiting for the portal connection…")
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (state.categoriesLoaded && state.categories.isNotEmpty()) TitleGrid(state, portalUrl, memory, onOpen, isFavorite) { picker = it }
        }
    }
    when (picker) {
        1 -> {
            val category = state.categories.getOrNull(state.selected)
            val copy = category != null && state.indexed != null && state.ready(category.id)
            ChoicePicker(
                "Sort", BrowseSort.entries.map { Choice(it.name, it.label + if (it.needsIndex && !copy) "  (needs the title index)" else "") }, setOf(state.sort.name), multi = false,
                note = if (copy) "Sorted on this device from the saved title list." else "Only the portal's order (newest added) is available until the title index has this category: Settings, Search, Build.",
                memory = memory, returnFocusId = BROWSE_SORT_ID, onToggle = { id -> state.changeSort(BrowseSort.valueOf(id)) }, onClose = { picker = 0 },
            )
        }
        3 -> ChoicePicker(
            "Genre", listOf(Choice("any", "Any genre")) + GENRES.map { Choice(it, it) }, setOf(state.genre ?: "any"), multi = false,
            note = "From the saved title list (portal genres, or the online tags). Needs the title index for this category.",
            memory = memory, returnFocusId = BROWSE_GENRE_ID, onToggle = { id -> state.changeGenre(id.takeIf { it != "any" }) }, onClose = { picker = 0 },
        )
        4 -> ChoicePicker(
            "Released since", listOf(Choice("any", "Any year")) + YEAR_STEPS.map { Choice("$it", "$it or later") }, setOf(state.yearFrom?.toString() ?: "any"), multi = false,
            note = "Titles with no year are left out when a year is chosen.",
            memory = memory, returnFocusId = BROWSE_YEAR_ID, onToggle = { id -> state.changeYearFrom(id.toIntOrNull()) }, onClose = { picker = 0 },
        )
        2 -> ChoicePicker(
            "Hide titles rated below", listOf(Choice("any", "Show everything")) + ContentPolicy.STEPS.map { Choice("$it", "${"%.0f".format(it)}") },
            setOf(state.minRating?.toString() ?: "any"), multi = false,
            note = "The portal's rating: IMDb when it has one, otherwise Kinopoisk. This view only; the default is in Settings.",
            memory = memory, returnFocusId = BROWSE_RATING_ID, onToggle = { id -> state.changeMinRating(id.toDoubleOrNull()) }, onClose = { picker = 0 },
        )
    }
    }
}

const val BROWSE_SORT_ID = "browse-sort"
const val BROWSE_RATING_ID = "browse-rating"
const val BROWSE_GENRE_ID = "browse-genre"
const val BROWSE_YEAR_ID = "browse-year"
private val GENRES = listOf("Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family", "Fantasy", "History", "Horror", "Music", "Mystery", "Romance", "Science Fiction", "Thriller", "War", "Western")
private val YEAR_STEPS = listOf(2020, 2010, 2000, 1990, 1980)

@Composable
private fun TitleGrid(state: VodBrowseState, portalUrl: String, memory: FocusMemory, onOpen: (Int, VodItem) -> Unit, isFavorite: (VodItem) -> Boolean, onOpenPicker: (Int) -> Unit) {
    val paged = state.titles
    val gridState = state.gridState
    val scope = rememberCoroutineScope()

    LaunchedEffect(paged) {
        if (paged.items.isEmpty() && !paged.exhausted) withContext(Dispatchers.IO) { paged.loadNext() }
        snapshotFlow { (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to paged.items.size }
            .collect { (lastVisible, loaded) ->
                if (loaded > 0 && lastVisible >= loaded - PREFETCH_TITLES && !paged.exhausted && paged.error == null) {
                    withContext(Dispatchers.IO) { paged.loadNext() }
                }
            }
    }

    Row(Modifier.padding(bottom = Spacing.S1), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
        // A Kids list is filtered on the device, so the portal's total would overstate it: count what has passed so far.
        Text(if (state.isKidsList) "%,d%s titles".format(paged.items.size, if (paged.exhausted) "" else "+") else (paged.total?.let { "%,d titles".format(it) } ?: " "), style = Tokens.Body, color = Tokens.TextSecondary)
        OsFocusable(id = BROWSE_SORT_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = { onOpenPicker(1) }) { _ ->
            Text("Sort: ${state.sort.label}", style = Tokens.Body, color = if (state.sort != BrowseSort.NEWEST) Tokens.AccentPrimary else Tokens.TextPrimary, maxLines = 1,
                modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1))
        }
        OsFocusable(id = BROWSE_RATING_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = { onOpenPicker(2) }) { _ ->
            Text(state.minRating?.let { "Rating ${"%.0f".format(it)}+" } ?: "Rating: any", style = Tokens.Body,
                color = if (state.minRating != null) Tokens.AccentPrimary else Tokens.TextPrimary, maxLines = 1,
                modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1))
        }
        OsFocusable(id = BROWSE_GENRE_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = { onOpenPicker(3) }) { _ ->
            Text(state.genre ?: "Genre: any", style = Tokens.Body, color = if (state.genre != null) Tokens.AccentPrimary else Tokens.TextPrimary, maxLines = 1,
                modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1))
        }
        OsFocusable(id = BROWSE_YEAR_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = { onOpenPicker(4) }) { _ ->
            Text(state.yearFrom?.let { "$it+" } ?: "Year: any", style = Tokens.Body, color = if (state.yearFrom != null) Tokens.AccentPrimary else Tokens.TextPrimary, maxLines = 1,
                modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1))
        }
    }
    when {
        state.sortUnavailable -> Text("Sorting and filtering this category need the title index (Settings). Showing the portal's order, unfiltered.", style = Tokens.Body, color = Tokens.StatusWarning)
        state.usingIndex -> Text("Sorted on this device", style = Tokens.Body, color = Tokens.TextSecondary)
    }
    Box(Modifier.fillMaxSize()) {
        when {
            paged.items.isEmpty() && paged.error == null && !paged.exhausted -> Message("Loading titles…")
            paged.items.isEmpty() && paged.error == null -> Message("No titles in this category.")
            paged.items.isEmpty() -> RetryText(paged.error!!, memory) { scope.launch { withContext(Dispatchers.IO) { paged.loadNext() } } }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS), state = gridState,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S2), verticalArrangement = Arrangement.spacedBy(Spacing.S2),
            ) {
                itemsIndexed(paged.items, key = { _, t -> t.id }) { index, item ->
                    TitleCard(index, item, portalUrl, memory, onOpen, isFavorite(item))
                }
                if (paged.error != null) {
                    item(key = "retry", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(COLUMNS) }) {
                        RetryText(paged.error!!, memory) { scope.launch { withContext(Dispatchers.IO) { paged.loadNext() } } }
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleCard(index: Int, item: VodItem, portalUrl: String, memory: FocusMemory, onOpen: (Int, VodItem) -> Unit, favorite: Boolean) {
    OsFocusable(
        id = titleFocusId(index), memory = memory, variant = FocusVariant.CARD,
        modifier = Modifier.fillMaxWidth(), onSelect = { onOpen(index, item) },
    ) { _ ->
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                Poster(PosterUrl.resolve(item.posterUri, portalUrl), Modifier.fillMaxSize(), targetWidthPx = 300)
                item.shownRating?.let { RatingBadge(it, Modifier.align(Alignment.TopEnd).padding(Spacing.S1)) }
                if (favorite) Text("$HEART", style = Tokens.BodyLarge, color = Tokens.StatusError, modifier = Modifier.align(Alignment.TopStart).padding(Spacing.S1))
            }
            Text(
                PortalText.decodeEntities(item.name), style = Tokens.Body, color = Tokens.TextPrimary, maxLines = 2,
                modifier = Modifier.padding(horizontal = Spacing.S1, vertical = Spacing.S1),
            )
        }
    }
}

/** A rating on a card: a star for IMDb, "KP" for Kinopoisk. Shown only when the portal sent a real value. */
@Composable
fun RatingBadge(rating: ShownRating, modifier: Modifier = Modifier) {
    Text(
        (if (rating.source == "IMDb") "★ %.1f" else "KP %.1f").format(rating.value), style = Tokens.Body, color = Tokens.StatusWarning, maxLines = 1,
        modifier = modifier
            .background(Tokens.BgCanvas.copy(alpha = 0.8f), RoundedCornerShape(Radii.Sm))
            .padding(horizontal = Spacing.S1, vertical = 2.dp),
    )
}

@Composable
private fun RetryText(message: String, memory: FocusMemory, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
        Text(message, style = Tokens.BodyLarge, color = Tokens.StatusError)
        OsFocusable(id = "vod-retry", memory = memory, variant = FocusVariant.BUTTON, onSelect = onRetry) { _ ->
            Text("Select to try again", style = Tokens.BodyLarge, color = Tokens.TextPrimary,
                modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1))
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = Tokens.BodyLarge, color = Tokens.TextSecondary)
}
