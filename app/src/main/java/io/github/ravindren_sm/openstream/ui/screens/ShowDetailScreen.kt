package io.github.ravindren_sm.openstream.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.ravindren_sm.openstream.data.CatalogResult
import io.github.ravindren_sm.openstream.data.LiveCatalog
import io.github.ravindren_sm.openstream.data.PagedTitles
import io.github.ravindren_sm.openstream.data.PlayTarget
import io.github.ravindren_sm.openstream.data.PosterUrl
import io.github.ravindren_sm.openstream.data.ResumeKeys
import io.github.ravindren_sm.openstream.data.ResumeStore
import io.github.ravindren_sm.openstream.data.TimeText
import io.github.ravindren_sm.openstream.data.VodCatalog
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.provider.VodItem
import io.github.ravindren_sm.openstream.ui.components.FocusVariant
import io.github.ravindren_sm.openstream.ui.components.HEART
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
 * One show's page state, held by the shell so seasons, loaded episodes and scroll survive playing an episode and
 * coming back. The portal's levels: seasons, then a season's episodes (paged), then an episode's files.
 */
class ShowDetailState(val show: VodItem, private val catalog: VodCatalog) {
    var seasons: List<VodItem> by mutableStateOf(emptyList())
        private set
    var seasonsLoaded: Boolean by mutableStateOf(false)
        private set
    var seasonsError: String? by mutableStateOf(null)
        private set
    var selectedSeason: Int by mutableIntStateOf(0)
        private set
    var episodes: PagedTitles by mutableStateOf(PagedTitles { CatalogResult.Failed(LiveCatalog.NOT_CONNECTED) })
        private set
    val episodeListState = LazyListState()

    /** Set just before the player opens. */
    var playTarget: PlayTarget? by mutableStateOf(null)

    /** Loads the seasons and selects the first. Blocking: call off the main thread. */
    fun loadSeasons() {
        when (val r = catalog.seasons(show)) {
            is CatalogResult.Ok -> { seasons = r.value; seasonsLoaded = true; seasonsError = null; selectSeason(0, resetScroll = false) }
            is CatalogResult.Failed -> seasonsError = r.message
        }
    }

    fun selectSeason(index: Int, resetScroll: Boolean = true) {
        val season = seasons.getOrNull(index) ?: return
        selectedSeason = index
        episodes = PagedTitles { page -> catalog.episodes(show, season, page) }
        if (resetScroll) episodeListState.requestScrollToItem(0)
    }

    /** The playable files of [episode] in the selected season. Blocking. */
    fun filesFor(episode: VodItem): CatalogResult<List<VodItem>> {
        val season = seasons.getOrNull(selectedSeason) ?: return CatalogResult.Failed(VodCatalog.NO_SEASONS)
        return when (val r = catalog.files(show, season, episode)) {
            is CatalogResult.Ok -> CatalogResult.Ok(r.value.filter { it.playableCmd != null })
            is CatalogResult.Failed -> r
        }
    }

    fun targetFor(file: VodItem, episode: VodItem): PlayTarget {
        val season = seasons[selectedSeason]
        return PlayTarget(
            file, "${show.name}: ${episode.name}", ResumeKeys.episode(show.id, season.id, episode.id),
            kind = "EPISODE", titleId = show.id, subtitle = "${season.name} · ${episode.name}", posterUri = show.posterUri, historyTitle = show.name, genres = show.genres,
        )
    }

    fun resumeKeyFor(episode: VodItem): String? =
        seasons.getOrNull(selectedSeason)?.let { ResumeKeys.episode(show.id, it.id, episode.id) }
}

const val SHOW_PLAY_ID = "show-play"
const val SHOW_FAVORITE_ID = "show-fav"
const val SHOW_VERSIONS_ID = "show-versions"
fun seasonFocusId(index: Int) = "season-$index"
fun episodeFocusId(index: Int) = "episode-$index"
private fun fileFocusId(index: Int) = "file-$index"

private const val PREFETCH_EPISODES = 6

private class Chooser(val index: Int, val episode: VodItem, val files: List<VodItem>)

/**
 * A show's page: poster and metadata, Play (the first episode of the selected season), season chips and the
 * episode list. Selecting an episode opens its file; when an episode has several (language and quality) a short
 * chooser appears first. An episode with a saved position shows "Resume m:ss".
 */
@Composable
fun ShowDetailScreen(
    state: ShowDetailState,
    connected: Boolean,
    portalUrl: String,
    resume: ResumeStore,
    memory: FocusMemory,
    onPlay: (target: PlayTarget, returnFocusId: String) -> Unit,
    favorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onShowVersions: (() -> Unit)? = null,
    versionCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var chooser: Chooser? by remember { mutableStateOf(null) }
    var notice: String? by remember { mutableStateOf(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(connected) {
        if (connected && !state.seasonsLoaded) withContext(Dispatchers.IO) { state.loadSeasons() }
    }

    fun playEpisode(index: Int, episode: VodItem) {
        if (busy) return
        busy = true; notice = "Loading…"
        scope.launch {
            val r = withContext(Dispatchers.IO) { state.filesFor(episode) }
            busy = false; notice = null
            when (r) {
                is CatalogResult.Failed -> notice = r.message
                is CatalogResult.Ok -> when {
                    r.value.isEmpty() -> notice = VodCatalog.NO_FILES
                    r.value.size == 1 -> onPlay(state.targetFor(r.value[0], episode), episodeFocusId(index))
                    else -> chooser = Chooser(index, episode, r.value)
                }
            }
        }
    }

    // Back closes the chooser before it leaves the page, and puts focus back on the episode.
    BackHandler(enabled = chooser != null) {
        val index = chooser?.index
        chooser = null
        if (index != null) scope.launch { withFrameNanos { }; memory.tryFocus(episodeFocusId(index)) }
    }
    LaunchedEffect(chooser) { if (chooser != null) { withFrameNanos { }; memory.tryFocus(fileFocusId(0)) } }

    Box(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = Spacing.SafeHorizontal, vertical = Spacing.S2),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S4),
        ) {
            Poster(PosterUrl.resolve(state.show.posterUri, portalUrl), Modifier.width(320.dp).height(480.dp), targetWidthPx = 500)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
                val show = state.show
                Text(PortalText.decodeEntities(show.name), style = Tokens.TitleLarge, color = Tokens.TextPrimary, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S2)) {
                    show.year?.let { Text(it, style = Tokens.Body, color = Tokens.TextSecondary) }
                    show.age?.let { Text(PortalText.decodeEntities(it), style = Tokens.Body, color = Tokens.TextSecondary) }
                    show.shownRating?.let { Text("${it.source} %.1f".format(it.value), style = Tokens.Body, color = Tokens.StatusWarning) }
                    show.genres?.let { Text(PortalText.decodeEntities(it), style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1) }
                }
                show.description?.let { Text(PortalText.decodeEntities(it), style = Tokens.Body, color = Tokens.TextPrimary, maxLines = 3) }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S3), verticalAlignment = Alignment.CenterVertically) {
                    OsFocusable(id = SHOW_PLAY_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = {
                        state.episodes.items.firstOrNull()?.let { playEpisode(0, it) } ?: run { notice = "Episodes are still loading." }
                    }) { _ ->
                        Text("Play", style = Tokens.TitleMedium, color = Tokens.TextPrimary,
                            modifier = Modifier.padding(horizontal = Spacing.S4, vertical = Spacing.S1))
                    }
                    if (onShowVersions != null) OsFocusable(id = SHOW_VERSIONS_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = onShowVersions) { _ ->
                        Text("Versions ($versionCount)", style = Tokens.TitleMedium, color = Tokens.TextPrimary,
                            modifier = Modifier.padding(horizontal = Spacing.S4, vertical = Spacing.S1))
                    }
                    OsFocusable(id = SHOW_FAVORITE_ID, memory = memory, variant = FocusVariant.BUTTON, onSelect = onToggleFavorite) { _ ->
                        Text(if (favorite) "$HEART  In favorites" else "Add to favorites", style = Tokens.TitleMedium, color = if (favorite) Tokens.StatusError else Tokens.TextPrimary,
                            modifier = Modifier.padding(horizontal = Spacing.S4, vertical = Spacing.S1))
                    }
                    notice?.let { Text(it, style = Tokens.Body, color = if (busy) Tokens.TextSecondary else Tokens.StatusError) }
                }
                when {
                    state.seasonsError != null -> Retry(state.seasonsError!!, memory) {
                        scope.launch { withContext(Dispatchers.IO) { state.loadSeasons() } }
                    }
                    !state.seasonsLoaded -> Text(if (connected) "Loading seasons…" else "Waiting for the portal connection…", style = Tokens.BodyLarge, color = Tokens.TextSecondary)
                    state.seasons.isEmpty() -> Text("No seasons.", style = Tokens.BodyLarge, color = Tokens.TextSecondary)
                    else -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S1), modifier = Modifier.padding(top = Spacing.S1)) {
                            itemsIndexed(state.seasons, key = { _, s -> s.id }) { index, season ->
                                OsFocusable(id = seasonFocusId(index), memory = memory, variant = FocusVariant.TAB, onSelect = { state.selectSeason(index) }) { _ ->
                                    Text(
                                        PortalText.decodeEntities(season.name), style = Tokens.BodyLarge, maxLines = 1,
                                        color = if (index == state.selectedSeason) Tokens.AccentPrimary else Tokens.TextPrimary,
                                        modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1),
                                    )
                                }
                            }
                        }
                        EpisodeList(state, resume, memory, onSelect = { index, episode -> playEpisode(index, episode) })
                    }
                }
            }
        }

        chooser?.let { c ->
            Box(Modifier.fillMaxSize().background(Tokens.BgCanvas.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.width(900.dp).background(Tokens.Surface1, RoundedCornerShape(Radii.Lg)).padding(Spacing.S3),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S1),
                ) {
                    Text("Choose a version", style = Tokens.TitleMedium, color = Tokens.TextPrimary)
                    Text(PortalText.decodeEntities(c.episode.name), style = Tokens.Body, color = Tokens.TextSecondary, maxLines = 1)
                    c.files.forEachIndexed { i, file ->
                        OsFocusable(
                            id = fileFocusId(i), memory = memory, variant = FocusVariant.ROW, modifier = Modifier.fillMaxWidth(),
                            onSelect = { onPlay(state.targetFor(file, c.episode), episodeFocusId(c.index)) },
                        ) { _ ->
                            Text(PortalText.decodeEntities(file.name), style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1,
                                modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1 + 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeList(state: ShowDetailState, resume: ResumeStore, memory: FocusMemory, onSelect: (Int, VodItem) -> Unit) {
    val paged = state.episodes
    val listState = state.episodeListState
    val scope = rememberCoroutineScope()

    LaunchedEffect(paged) {
        if (paged.items.isEmpty() && !paged.exhausted) withContext(Dispatchers.IO) { paged.loadNext() }
        snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to paged.items.size }
            .collect { (lastVisible, loaded) ->
                if (loaded > 0 && lastVisible >= loaded - PREFETCH_EPISODES && !paged.exhausted && paged.error == null) {
                    withContext(Dispatchers.IO) { paged.loadNext() }
                }
            }
    }

    when {
        paged.items.isEmpty() && paged.error == null && !paged.exhausted -> Text("Loading episodes…", style = Tokens.BodyLarge, color = Tokens.TextSecondary)
        paged.items.isEmpty() && paged.error == null -> Text("No episodes in this season.", style = Tokens.BodyLarge, color = Tokens.TextSecondary)
        paged.items.isEmpty() -> Retry(paged.error!!, memory) { scope.launch { withContext(Dispatchers.IO) { paged.loadNext() } } }
        else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(Spacing.S1), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(paged.items, key = { _, e -> e.id }) { index, episode ->
                OsFocusable(
                    id = episodeFocusId(index), memory = memory, variant = FocusVariant.ROW,
                    // Up from the first episode goes to the season being shown, not to whichever chip is nearest
                    // the middle of this wide row.
                    modifier = Modifier.fillMaxWidth().then(
                        if (index == 0) Modifier.focusProperties { up = memory.requesterFor(seasonFocusId(state.selectedSeason)) } else Modifier,
                    ),
                    onSelect = { onSelect(index, episode) },
                ) { _ ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.S2, vertical = Spacing.S1 + 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(PortalText.decodeEntities(episode.name), style = Tokens.BodyLarge, color = Tokens.TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                        state.resumeKeyFor(episode)?.let(resume::get)?.let { saved ->
                            Text("Resume ${TimeText.format(saved)}", style = Tokens.Mono, color = Tokens.AccentPrimary, maxLines = 1)
                        }
                    }
                }
            }
            if (paged.error != null) item(key = "retry") {
                Retry(paged.error!!, memory) { scope.launch { withContext(Dispatchers.IO) { paged.loadNext() } } }
            }
        }
    }
}

@Composable
private fun Retry(message: String, memory: FocusMemory, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S1)) {
        Text(message, style = Tokens.BodyLarge, color = Tokens.StatusError)
        OsFocusable(id = "show-retry", memory = memory, variant = FocusVariant.BUTTON, onSelect = onRetry) { _ ->
            Text("Select to try again", style = Tokens.BodyLarge, color = Tokens.TextPrimary,
                modifier = Modifier.padding(horizontal = Spacing.S2, vertical = Spacing.S1))
        }
    }
}
