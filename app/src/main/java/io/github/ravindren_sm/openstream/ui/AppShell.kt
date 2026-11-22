package io.github.ravindren_sm.openstream.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.ravindren_sm.openstream.data.LiveCatalog
import io.github.ravindren_sm.openstream.data.LivePlayback
import io.github.ravindren_sm.openstream.data.PlayTarget
import io.github.ravindren_sm.openstream.data.PortalSession
import io.github.ravindren_sm.openstream.data.PrefsResumeStore
import io.github.ravindren_sm.openstream.data.CatalogResult
import io.github.ravindren_sm.openstream.data.PagedChannels
import io.github.ravindren_sm.openstream.data.SearchCatalog
import io.github.ravindren_sm.openstream.data.SearchFilters
import io.github.ravindren_sm.openstream.data.SearchSources
import io.github.ravindren_sm.openstream.data.TitleIndexer
import io.github.ravindren_sm.openstream.ui.components.VersionPicker
import io.github.ravindren_sm.openstream.ui.screens.DETAIL_VERSIONS_ID
import io.github.ravindren_sm.openstream.ui.screens.SHOW_VERSIONS_ID
import io.github.ravindren_sm.openstream.data.SearchRanking
import io.github.ravindren_sm.openstream.ui.screens.SearchPhase
import io.github.ravindren_sm.openstream.ui.screens.SearchScreen
import io.github.ravindren_sm.openstream.ui.screens.SearchState
import io.github.ravindren_sm.openstream.ui.screens.searchChannelFocusId
import io.github.ravindren_sm.openstream.profiles.ContinueItems
import io.github.ravindren_sm.openstream.profiles.FavoriteItems
import io.github.ravindren_sm.openstream.profiles.FavoritesState
import io.github.ravindren_sm.openstream.profiles.Kinds
import io.github.ravindren_sm.openstream.ui.screens.FAVORITES_INITIAL_IDS
import io.github.ravindren_sm.openstream.ui.screens.FavoritesScreen
import io.github.ravindren_sm.openstream.ui.screens.favChannelFocusId
import io.github.ravindren_sm.openstream.ui.screens.homeFavChannelFocusId
import io.github.ravindren_sm.openstream.profiles.ContinueTile
import io.github.ravindren_sm.openstream.profiles.ProfileRepository
import io.github.ravindren_sm.openstream.profiles.ProfileResumeStore
import io.github.ravindren_sm.openstream.profiles.RoomHistoryRecorder
import io.github.ravindren_sm.openstream.profiles.db.OpenStreamDb
import io.github.ravindren_sm.openstream.provider.VodItem
import io.github.ravindren_sm.openstream.ui.screens.HomeScreen
import io.github.ravindren_sm.openstream.ui.screens.TileAction
import kotlinx.coroutines.launch
import io.github.ravindren_sm.openstream.ui.screens.doorFocusId
import io.github.ravindren_sm.openstream.ui.screens.recentFocusId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import io.github.ravindren_sm.openstream.data.ResumeKeys
import io.github.ravindren_sm.openstream.data.SessionStatus
import io.github.ravindren_sm.openstream.data.VodCatalog
import io.github.ravindren_sm.openstream.data.VodKind
import io.github.ravindren_sm.openstream.data.VodPlayback
import io.github.ravindren_sm.openstream.data.SettingsStore
import io.github.ravindren_sm.openstream.nav.NavStack
import io.github.ravindren_sm.openstream.nav.Route
import io.github.ravindren_sm.openstream.nav.tabFocusId
import io.github.ravindren_sm.openstream.ui.components.TopNavBar
import io.github.ravindren_sm.openstream.ui.focus.FocusMemory
import io.github.ravindren_sm.openstream.ui.screens.LiveBrowseState
import io.github.ravindren_sm.openstream.ui.screens.LivePlayerScreen
import io.github.ravindren_sm.openstream.ui.screens.LiveTvScreen
import io.github.ravindren_sm.openstream.ui.screens.MovieDetailScreen
import io.github.ravindren_sm.openstream.ui.screens.ShowDetailScreen
import io.github.ravindren_sm.openstream.ui.screens.ShowDetailState
import io.github.ravindren_sm.openstream.ui.screens.VodBrowseScreen
import io.github.ravindren_sm.openstream.ui.screens.VodBrowseState
import io.github.ravindren_sm.openstream.ui.screens.VodPlayerScreen
import io.github.ravindren_sm.openstream.ui.screens.titleFocusId
import io.github.ravindren_sm.openstream.ui.screens.PlaceholderScreen
import io.github.ravindren_sm.openstream.ui.screens.SessionStatusLine
import io.github.ravindren_sm.openstream.ui.screens.SetupScreen
import io.github.ravindren_sm.openstream.ui.screens.channelFocusId
import io.github.ravindren_sm.openstream.ui.screens.initialFocusId
import io.github.ravindren_sm.openstream.ui.theme.Tokens

/**
 * App frame: top tab bar plus the current screen, driven by an immutable [NavStack].
 *
 * Focus rule (from the prototype): exactly one element is focused at any time. After every stack
 * change the shell restores the focus remembered by the screen being returned to
 * ([NavStack.restoreFocusId]); if that element no longer exists it falls back to the screen's
 * initial focus, then to the active tab. Back pops the stack; at the root it calls [onExit].
 */
@Composable
fun AppShell(store: SettingsStore, session: PortalSession, onExit: () -> Unit) {
    // First run (or unreadable settings) starts on the setup screen; otherwise on Home.
    val loaded = remember { store.load() }
    val notice = if (loaded is SettingsStore.Loaded.Unreadable) "The saved settings could not be opened on this device. Please enter them again." else null
    var stack by remember { mutableStateOf(NavStack.start(if (loaded is SettingsStore.Loaded.Found) Route.HOME else Route.SETUP)) }
    val memory = remember { FocusMemory() }
    // Live TV browsing state lives here so it survives opening a channel and coming back. A new
    // portal (saved from setup) gets a fresh one, so nothing from the old portal is shown.
    var portalEpoch by remember { mutableIntStateOf(0) }
    val liveState = remember(portalEpoch) { LiveBrowseState(LiveCatalog(session)) }
    val livePlayback = remember(portalEpoch) { LivePlayback(session) }
    // Movies and Shows get the same treatment, plus the portal address (poster paths are relative to it) and
    // the resume positions, which live on the device.
    var portalUrl by remember { mutableStateOf((loaded as? SettingsStore.Loaded.Found)?.settings?.portalUrl.orEmpty()) }
    val vodCatalog = remember(portalEpoch) { VodCatalog(session) }
    val moviesState = remember(portalEpoch) { VodBrowseState(vodCatalog, VodKind.MOVIES) }
    val showsState = remember(portalEpoch) { VodBrowseState(vodCatalog, VodKind.SHOWS) }
    val vodPlayback = remember(portalEpoch) { VodPlayback(session) }
    // The show page being viewed (seasons, loaded episodes, scroll), kept while an episode plays.
    var showDetail: ShowDetailState? by remember(portalEpoch) { mutableStateOf(null) }
    val appContext = LocalContext.current
    // The person watching. Until the profile picker exists there is one default profile ("Family"), made on first
    // use; history and resume positions belong to it.
    val repo = remember { ProfileRepository(OpenStreamDb.get(appContext)) }
    var profileId by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { profileId = withContext(Dispatchers.IO) { repo.ensureDefaultProfile().id } }
    val resumeStore = remember { ProfileResumeStore(PrefsResumeStore(appContext)) { profileId } }
    val recorder = remember { RoomHistoryRecorder(repo, { profileId }, CoroutineScope(SupervisorJob() + Dispatchers.IO)) }
    // Favorites (channels, movies, shows) of the current profile, shared by every screen that shows a heart.
    val favorites = remember { FavoritesState(repo, { profileId }, CoroutineScope(SupervisorJob() + Dispatchers.IO)) }
    LaunchedEffect(profileId) { if (profileId > 0L) withContext(Dispatchers.IO) { favorites.reload() } }
    // The favorite channels being played (a fixed list to zap through), and the favorite movie whose page is open.
    var favPlayer: PagedChannels? by remember { mutableStateOf(null) }
    var favItem: VodItem? by remember { mutableStateOf(null) }
    var lastSubmitHandled by remember { mutableIntStateOf(0) }
    // Continue watching, re-read each time Home is shown (null until the first read finishes).
    var continueTiles: List<ContinueTile>? by remember { mutableStateOf(null) }
    var tilesFresh by remember { mutableStateOf(false) }   // Home's rows were re-read since it was entered
    var recentTiles: List<ContinueTile> by remember { mutableStateOf(emptyList()) }
    val tileScope = rememberCoroutineScope()
    suspend fun loadTiles() {
        val (unfinished, finished) = withContext(Dispatchers.IO) { repo.continueWatching(profileId, 30) to repo.recentlyFinished(profileId, 30) }
        val keep = ContinueItems.from(unfinished)
        continueTiles = keep
        // A show already under Continue watching is not repeated under Recently watched.
        recentTiles = ContinueItems.from(finished, exclude = keep.mapTo(HashSet()) { it.titleKey })
    }
    // The title chosen from Continue watching, kept while it plays.
    var historyTarget: PlayTarget? by remember { mutableStateOf(null) }
    val connected = session.status.let { it is SessionStatus.Ready || it is SessionStatus.Degraded }
    // A portal already saved: hand it to the session (it connects while the app is in the foreground).
    LaunchedEffect(Unit) { (loaded as? SettingsStore.Loaded.Found)?.let { session.configure(it.settings) } }

    // Search: the box text and the last outcome live here so they survive opening a result and coming back.
    val searchCatalog = remember(portalEpoch) { SearchCatalog(session) }
    val searchState = remember(portalEpoch) { SearchState() }
    val db = remember { OpenStreamDb.get(appContext) }
    val tileScopeForSearch = rememberCoroutineScope()
    fun recordSearch(text: String) { tileScopeForSearch.launch(Dispatchers.IO) { repo.recordSearch(profileId, text) } }
    LaunchedEffect(searchState.query, searchState.submitTick, connected, searchState.filters) {
        val q = searchState.query.trim()
        if (q.length < SearchRanking.MIN_QUERY) { searchState.phase = SearchPhase.Idle; return@LaunchedEffect }
        if (!connected) { searchState.phase = SearchPhase.Idle; return@LaunchedEffect }
        // Wait for a pause in typing (shorter once there are three letters), unless Done was pressed.
        val submitted = searchState.submitTick > 0 && searchState.submitTick != lastSubmitHandled
        delay(if (submitted) 0L else if (q.length >= 3) 350L else 800L)
        lastSubmitHandled = searchState.submitTick
        searchState.phase = SearchPhase.Loading
        val result = withContext(Dispatchers.IO) { searchCatalog.search(q, searchState.filters, searchState.categories) }
        searchState.phase = when (result) {
            is CatalogResult.Ok -> SearchPhase.Done(result.value)
            is CatalogResult.Failed -> SearchPhase.Failed(result.message)
        }
        if (submitted && result is CatalogResult.Ok) recordSearch(q)
    }

    // Filters: the portal's categories (for the pickers and the language choices) and the saved default set.
    val searchPrefs = remember { appContext.getSharedPreferences("search", android.content.Context.MODE_PRIVATE) }
    LaunchedEffect(portalEpoch) {
        val saved = searchPrefs.getString("default_filters", null)?.let(SearchFilters::decode)?.takeIf { it.isActive }
        searchState.defaultFilters = saved
        if (saved != null) searchState.filters = saved           // the default applies from the start of each visit
    }
    LaunchedEffect(stack.current.route == Route.SEARCH, connected, vodCatalog) {
        if (stack.current.route == Route.SEARCH && connected && searchState.categories.isEmpty()) {
            when (val r = withContext(Dispatchers.IO) { vodCatalog.allCategories() }) {
                is CatalogResult.Ok -> searchState.categories = r.value.filterNot { io.github.ravindren_sm.openstream.profiles.CategoryRules.isAdult(it.title) }
                is CatalogResult.Failed -> Unit
            }
        }
    }
    // Type-ahead suggestions from text already on the device; the channel list is read ahead once Search is first opened.
    val onSearchRoute = stack.current.route == Route.SEARCH
    var channelsWarm by remember(portalEpoch) { mutableStateOf(false) }
    LaunchedEffect(onSearchRoute, connected, searchCatalog) {
        if (onSearchRoute && connected && !channelsWarm) { withContext(Dispatchers.IO) { searchCatalog.warmChannels() }; channelsWarm = true }
    }
    val searchSources = remember(portalEpoch) { SearchSources(repo, favorites, db.catalogIndex()) { searchCatalog.cachedChannelNames() } }
    // The title index: opt-in, gentle, runs only while connected and not playing.
    val indexer = remember(portalEpoch) { TitleIndexer(vodCatalog, db.catalogIndex(), appContext.getSharedPreferences("search", android.content.Context.MODE_PRIVATE)) }
    var indexTick by remember { mutableIntStateOf(0) }
    val canIndex = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    androidx.compose.runtime.SideEffect {
        canIndex.set(connected && stack.current.route !in setOf(Route.LIVE_PLAYER, Route.VOD_PLAYER, Route.SETUP) && searchState.phase !is SearchPhase.Loading)
        searchState.index = indexer.status
    }
    LaunchedEffect(portalEpoch) { if (portalEpoch > 0) indexer.clear() }
    LaunchedEffect(indexer) { withContext(Dispatchers.IO) { indexer.refreshStatus() } }
    LaunchedEffect(indexer, indexTick) { if (indexer.enabled) withContext(Dispatchers.IO) { indexer.run { canIndex.get() } } }
    LaunchedEffect(searchState.query, favorites.all, channelsWarm, indexer.status.titles / 500, profileId, onSearchRoute) {
        if (!onSearchRoute) return@LaunchedEffect
        delay(120)
        searchState.suggestions = withContext(Dispatchers.IO) { searchSources.suggestions(profileId, searchState.query) }
    }
    // A picker for the versions of a title opened from Search, and which versions the open page has.
    var pickerOpen by remember { mutableStateOf(false) }
    var detailVersions: List<VodItem> by remember { mutableStateOf(emptyList()) }

    BackHandler {
        if (memory.justFinishedEditing()) return@BackHandler    // that Back closed a keyboard
        val previous = stack.goBack()
        if (previous == null) onExit() else stack = previous
    }

    val topTab: Route? = stack.frames.lastOrNull { it.route.isTab }?.route

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Tokens.BgCanvas)) {
    Column(Modifier.fillMaxSize()) {
        if (stack.current.route == Route.SETUP) {
            SetupScreen(
                store = store,
                memory = memory,
                notice = notice,
                onSaved = { settings -> session.configure(settings); portalUrl = settings.portalUrl; portalEpoch++; stack = NavStack.start(Route.HOME) },
                onSkip = { stack = NavStack.start(Route.HOME) },
            )
        } else if (stack.current.route == Route.LIVE_PLAYER) {
            // Full screen: no tab bar or status line over the video (a focused tab would leave on Select).
            val index = stack.current.params["item"]?.toIntOrNull() ?: -1
            val fromFavorites = stack.current.params["src"] == "fav"
            val from = stack.current.params["from"]
            LivePlayerScreen(
                channels = (if (fromFavorites) favPlayer else null) ?: liveState.channels, startIndex = index, playback = livePlayback, memory = memory,
                onChannelChanged = { i ->
                    // Back should land on the channel being watched, with its row on screen.
                    if (fromFavorites) {
                        stack = stack.withPreviousFocus(when (from) { "home" -> homeFavChannelFocusId(i); "search" -> searchChannelFocusId(i); else -> favChannelFocusId(i) })
                    } else {
                        stack = stack.withPreviousFocus(channelFocusId(i))
                        liveState.channelListState.requestScrollToItem(maxOf(i - 3, 0))
                    }
                },
            )
        } else if (stack.current.route == Route.VOD_PLAYER) {
            val target: PlayTarget? = if (stack.current.params["play"] == "history") {
                historyTarget
            } else if (stack.current.params["play"] == "episode") {
                showDetail?.playTarget
            } else if (stack.current.params["src"] == "fav") {
                favItem?.let { PlayTarget(it, it.name, ResumeKeys.movie(it.id)) }
            } else {
                val browse = if (stack.current.params["kind"] == VodKind.SHOWS.name) showsState else moviesState
                browse.titles.items.getOrNull(stack.current.params["item"]?.toIntOrNull() ?: -1)
                    ?.let { PlayTarget(it, it.name, ResumeKeys.movie(it.id)) }
            }
            VodPlayerScreen(target = target, playback = vodPlayback, resume = resumeStore, memory = memory, portalUrl = portalUrl, history = recorder)
        } else {
            TopNavBar(
                activeTab = topTab,
                memory = memory,
                onSelectTab = { tab -> stack = stack.resetToTab(tab) },
            )
            if (stack.current.route == Route.HOME) {
                HomeScreen(
                    continueTiles = continueTiles, recentTiles = recentTiles, portalUrl = portalUrl, memory = memory,
                    favoriteChannels = favorites.channels(),
                    onOpenFavoriteChannel = { i ->
                        favPlayer = PagedChannels.fixed(favorites.channels().map(FavoriteItems::toChannel))
                        stack = stack.navigate(Route.LIVE_PLAYER, mapOf("item" to "$i", "src" to "fav", "from" to "home"), currentFocusId = memory.focusedId)
                    },
                    onTileAction = { tile, action ->
                        tileScope.launch {
                            withContext(Dispatchers.IO) {
                                when (action) {
                                    TileAction.REMOVE -> { repo.forget(profileId, tile.resumeKey); resumeStore.clear(tile.resumeKey) }
                                    TileAction.TOGGLE_WATCHED -> { repo.setWatched(profileId, tile.resumeKey, !tile.completed); resumeStore.clear(tile.resumeKey) }
                                }
                            }
                            loadTiles()
                        }
                    },
                    onResume = { tile ->
                        // Rebuild the play target from what was stored: the command plays it, the resume key finds the position.
                        val file = VodItem(tile.resumeKey, tile.title, false, false, false, tile.playCmd, emptyList())
                        historyTarget = PlayTarget(
                            file, if (tile.subtitle != null) "${tile.title}: ${tile.subtitle}" else tile.title, tile.resumeKey,
                            kind = tile.kind, titleId = tile.titleId, subtitle = tile.subtitle, posterUri = tile.posterUri, historyTitle = tile.title,
                        )
                        stack = stack.navigate(Route.VOD_PLAYER, mapOf("play" to "history"), currentFocusId = memory.focusedId)
                    },
                    onDoor = { route -> stack = stack.resetToTab(route) },
                )
            } else if (stack.current.route == Route.MOVIES || stack.current.route == Route.SHOWS) {
                val browse = if (stack.current.route == Route.SHOWS) showsState else moviesState
                VodBrowseScreen(
                    state = browse, connected = connected, portalUrl = portalUrl, memory = memory, isFavorite = favorites::isVod,
                    onOpen = { index, item ->
                        // The portal keeps shows among the movies: the title's own flag decides which page opens.
                        val route = if (item.isSeries) Route.SHOW_DETAIL else Route.MOVIE_DETAIL
                        if (item.isSeries && showDetail?.show?.id != item.id) showDetail = ShowDetailState(item, vodCatalog)
                        stack = stack.navigate(route, mapOf("item" to "$index", "kind" to browse.kind.name), currentFocusId = titleFocusId(index))
                    },
                )
            } else if (stack.current.route == Route.MOVIE_DETAIL) {
                val browse = if (stack.current.params["kind"] == VodKind.SHOWS.name) showsState else moviesState
                val index = stack.current.params["item"]?.toIntOrNull() ?: -1
                val movie = if (stack.current.params["src"] == "fav") favItem else browse.titles.items.getOrNull(index)
                val movieVersions = if (detailVersions.any { it.id == movie?.id }) detailVersions else emptyList()
                MovieDetailScreen(
                    item = movie, portalUrl = portalUrl, memory = memory,
                    onShowVersions = if (movieVersions.size > 1) ({ pickerOpen = true }) else null, versionCount = movieVersions.size,
                    favorite = movie?.let(favorites::isVod) ?: false,
                    onToggleFavorite = { movie?.let(favorites::toggleVod) },
                    onPlay = { stack = stack.navigate(Route.VOD_PLAYER, stack.current.params, currentFocusId = memory.focusedId) },
                )
            } else if (stack.current.route == Route.SHOW_DETAIL) {
                val detail = showDetail
                if (detail == null) {
                    PlaceholderScreen(entry = stack.current, memory = memory, onOpenDetail = { _, _ -> })
                } else {
                    val showVersions = if (detailVersions.any { it.id == detail.show.id }) detailVersions else emptyList()
                    ShowDetailScreen(
                        state = detail, connected = connected, portalUrl = portalUrl, resume = resumeStore, memory = memory,
                        onShowVersions = if (showVersions.size > 1) ({ pickerOpen = true }) else null, versionCount = showVersions.size,
                        favorite = favorites.isVod(detail.show), onToggleFavorite = { favorites.toggleVod(detail.show) },
                        onPlay = { target, returnFocusId ->
                            detail.playTarget = target
                            stack = stack.navigate(Route.VOD_PLAYER, mapOf("play" to "episode"), currentFocusId = returnFocusId)
                        },
                    )
                }
            } else if (stack.current.route == Route.FAVORITES) {
                FavoritesScreen(
                    favorites = favorites, portalUrl = portalUrl, memory = memory,
                    onOpenChannel = { i ->
                        favPlayer = PagedChannels.fixed(favorites.channels().map(FavoriteItems::toChannel))
                        stack = stack.navigate(Route.LIVE_PLAYER, mapOf("item" to "$i", "src" to "fav", "from" to "tab"), currentFocusId = memory.focusedId)
                    },
                    onOpenTitle = { f ->
                        val item = FavoriteItems.toVod(f)
                        if (f.kind == Kinds.SHOW) {
                            if (showDetail?.show?.id != item.id) showDetail = ShowDetailState(item, vodCatalog)
                            stack = stack.navigate(Route.SHOW_DETAIL, emptyMap(), currentFocusId = memory.focusedId)
                        } else {
                            favItem = item
                            stack = stack.navigate(Route.MOVIE_DETAIL, mapOf("src" to "fav"), currentFocusId = memory.focusedId)
                        }
                    },
                    onRemove = { f -> favorites.toggle(f.kind, f.refId, f.name, f.posterUri, f) },
                )
            } else if (stack.current.route == Route.SEARCH) {
                SearchScreen(
                    state = searchState, portalUrl = portalUrl, memory = memory, connected = connected,
                    isFavoriteVod = favorites::isVod, isFavoriteChannel = favorites::isChannel,
                    onOpenChannel = { i ->
                        (searchState.phase as? SearchPhase.Done)?.let { done ->
                            favPlayer = PagedChannels.fixed(done.results.channels)
                            stack = stack.navigate(Route.LIVE_PLAYER, mapOf("item" to "$i", "src" to "fav", "from" to "search"), currentFocusId = memory.focusedId)
                        }
                    },
                    onOpenMovie = { group ->
                        recordSearch(searchState.query)
                        favItem = group.best; detailVersions = group.versions
                        stack = stack.navigate(Route.MOVIE_DETAIL, mapOf("src" to "fav"), currentFocusId = memory.focusedId)
                    },
                    onOpenShow = { group ->
                        recordSearch(searchState.query)
                        detailVersions = group.versions
                        if (showDetail?.show?.id != group.best.id) showDetail = ShowDetailState(group.best, vodCatalog)
                        stack = stack.navigate(Route.SHOW_DETAIL, emptyMap(), currentFocusId = memory.focusedId)
                    },
                    onPickSuggestion = { sug -> searchState.query = sug.text; searchState.submitTick++ },
                    onEnableIndex = { indexer.enable(); indexTick++ },
                    onFiltersChange = { searchState.filters = it },
                    onSaveDefaultFilters = {
                        searchPrefs.edit().putString("default_filters", searchState.filters.encode()).apply()
                        searchState.defaultFilters = searchState.filters
                    },
                    onPauseIndex = { indexer.pause(); tileScopeForSearch.launch(Dispatchers.IO) { delay(300); indexer.refreshStatus() } },
                )
            } else if (stack.current.route == Route.LIVE_TV) {
                LiveTvScreen(
                    state = liveState,
                    connected = connected,
                    memory = memory,
                    isFavorite = favorites::isChannel, onToggleFavorite = favorites::toggleChannel,
                    onOpenChannel = { index, _ ->
                        stack = stack.navigate(Route.LIVE_PLAYER, mapOf("item" to "$index"), currentFocusId = memory.focusedId)
                    },
                )
            } else {
                PlaceholderScreen(
                    entry = stack.current,
                    memory = memory,
                    onOpenDetail = { route, params ->
                        stack = stack.navigate(route, params, currentFocusId = memory.focusedId)
                    },
                )
            }
        }
    }
    // Connection state: small, bottom left, off the player screens.
    if (stack.current.route !in setOf(Route.SETUP, Route.LIVE_PLAYER, Route.VOD_PLAYER)) SessionStatusLine(session.status, Modifier.align(androidx.compose.ui.Alignment.BottomStart))
    if (pickerOpen) {
        val onShow = stack.current.route == Route.SHOW_DETAIL
        VersionPicker(
            versions = detailVersions, current = if (onShow) showDetail?.show else favItem, memory = memory,
            returnFocusId = if (onShow) SHOW_VERSIONS_ID else DETAIL_VERSIONS_ID,
            onPick = { v ->
                pickerOpen = false
                if (onShow) showDetail = ShowDetailState(v, vodCatalog) else favItem = v
            },
            onDismiss = { pickerOpen = false },
        )
    }
    }

    // Read the history whenever Home is (re)entered; a short wait lets the player's last save land first.
    val onHome = stack.current.route == Route.HOME
    LaunchedEffect(onHome, profileId) {
        if (!onHome) { tilesFresh = false; return@LaunchedEffect }
        if (profileId <= 0L) return@LaunchedEffect
        delay(300)
        loadTiles()
        tilesFresh = true
    }

    // After each stack change, once the new screen is on screen, put focus where it belongs. On Home that waits for
    // the history, so the first Continue watching tile (not a door) gets it.
    val homeReady = !onHome || tilesFresh
    LaunchedEffect(stack, homeReady) {
        if (!homeReady) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }   // let the new screen compose and attach
        memory.tryFocusFirst(stack.restoreFocusId, initialFocusId(stack.current.route), if (onHome) recentFocusId(0) else null, if (onHome) homeFavChannelFocusId(0) else null, if (onHome) doorFocusId(0) else null,
            *(if (stack.current.route == Route.FAVORITES) FAVORITES_INITIAL_IDS else emptyArray()), topTab?.let(::tabFocusId))
    }
}
