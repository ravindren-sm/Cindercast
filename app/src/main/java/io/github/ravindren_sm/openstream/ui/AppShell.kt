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
import io.github.ravindren_sm.openstream.data.PagedChannels
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

    BackHandler {
        if (memory.justFinishedEditing()) return@BackHandler    // that Back closed a keyboard
        val previous = stack.goBack()
        if (previous == null) onExit() else stack = previous
    }

    val topTab: Route? = stack.frames.lastOrNull { it.route.isTab }?.route

    Column(Modifier.fillMaxSize().background(Tokens.BgCanvas)) {
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
                        stack = stack.withPreviousFocus(if (from == "home") homeFavChannelFocusId(i) else favChannelFocusId(i))
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
            SessionStatusLine(session.status)
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
                MovieDetailScreen(
                    item = movie, portalUrl = portalUrl, memory = memory,
                    favorite = movie?.let(favorites::isVod) ?: false,
                    onToggleFavorite = { movie?.let(favorites::toggleVod) },
                    onPlay = { stack = stack.navigate(Route.VOD_PLAYER, stack.current.params, currentFocusId = memory.focusedId) },
                )
            } else if (stack.current.route == Route.SHOW_DETAIL) {
                val detail = showDetail
                if (detail == null) {
                    PlaceholderScreen(entry = stack.current, memory = memory, onOpenDetail = { _, _ -> })
                } else {
                    ShowDetailScreen(
                        state = detail, connected = connected, portalUrl = portalUrl, resume = resumeStore, memory = memory,
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
