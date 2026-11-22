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
import io.github.ravindren_sm.openstream.data.PagedTitles
import io.github.ravindren_sm.openstream.data.PosterUrl
import io.github.ravindren_sm.openstream.data.VodCatalog
import io.github.ravindren_sm.openstream.data.VodCategory
import io.github.ravindren_sm.openstream.data.VodKind
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.provider.ShownRating
import io.github.ravindren_sm.openstream.provider.VodItem
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.OsFocusable
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

    val categoryListState = LazyListState()
    val gridState = LazyGridState()

    /** Loads the categories and selects the first. Blocking: call off the main thread. */
    fun loadCategories() {
        when (val r = catalog.categories(kind)) {
            is CatalogResult.Ok -> { categories = r.value; categoriesLoaded = true; categoriesError = null; select(0, resetScroll = false) }
            is CatalogResult.Failed -> categoriesError = r.message
        }
    }

    /** Shows the titles of category [index], from the first page. */
    fun select(index: Int, resetScroll: Boolean = true) {
        val category = categories.getOrNull(index) ?: return
        selected = index
        titles = PagedTitles { page -> catalog.titles(category.id, page) }
        if (resetScroll) gridState.requestScrollToItem(0)
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
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(connected) {
        if (connected && !state.categoriesLoaded) withContext(Dispatchers.IO) { state.loadCategories() }
    }

    Row(
        modifier.fillMaxSize().padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S4),
    ) {
        Column(Modifier.width(440.dp).fillMaxHeight()) {
            when {
                state.categoriesLoaded && state.categories.isEmpty() -> Message("No categories.")
                state.categoriesLoaded -> LazyColumn(state = state.categoryListState, verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                    itemsIndexed(state.categories, key = { _, c -> c.id }) { index, category ->
                        OsFocusable(
                            id = categoryFocusId(index), memory = memory, variant = FocusVariant.ROW,
                            modifier = Modifier.fillMaxWidth(), onSelect = { state.select(index) },
                        ) { _ ->
                            Text(
                                category.title, style = Tokens.BodyLarge, maxLines = 1,
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
            if (state.categoriesLoaded && state.categories.isNotEmpty()) TitleGrid(state, portalUrl, memory, onOpen)
        }
    }
}

@Composable
private fun TitleGrid(state: VodBrowseState, portalUrl: String, memory: FocusMemory, onOpen: (Int, VodItem) -> Unit) {
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

    Text(
        paged.total?.let { "%,d titles".format(it) } ?: " ",
        style = Tokens.Body, color = Tokens.TextSecondary, modifier = Modifier.padding(bottom = Spacing.S1),
    )
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
                    TitleCard(index, item, portalUrl, memory, onOpen)
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
private fun TitleCard(index: Int, item: VodItem, portalUrl: String, memory: FocusMemory, onOpen: (Int, VodItem) -> Unit) {
    OsFocusable(
        id = titleFocusId(index), memory = memory, variant = FocusVariant.CARD,
        modifier = Modifier.fillMaxWidth(), onSelect = { onOpen(index, item) },
    ) { _ ->
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                Poster(PosterUrl.resolve(item.posterUri, portalUrl), Modifier.fillMaxSize(), targetWidthPx = 300)
                item.shownRating?.let { RatingBadge(it, Modifier.align(Alignment.TopEnd).padding(Spacing.S1)) }
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
