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
import io.github.ravindren_sm.openstream.data.ContentPolicy
import io.github.ravindren_sm.openstream.data.ContentPolicyStore
import io.github.ravindren_sm.openstream.data.DefaultScope
import io.github.ravindren_sm.openstream.data.GuideLoader
import io.github.ravindren_sm.openstream.data.IndexedTitles
import io.github.ravindren_sm.openstream.data.VodHydrator
import io.github.ravindren_sm.openstream.data.HomeFeed
import io.github.ravindren_sm.openstream.data.HomeRowPlan
import io.github.ravindren_sm.openstream.data.PinKinds
import io.github.ravindren_sm.openstream.data.PinnedRow
import io.github.ravindren_sm.openstream.ui.screens.homePinFocusId
import io.github.ravindren_sm.openstream.ui.components.PosterLoader
import io.github.ravindren_sm.openstream.ui.screens.SettingsModel
import io.github.ravindren_sm.openstream.ui.screens.SettingsScreen
import io.github.ravindren_sm.openstream.ui.screens.DETAIL_PLAY_ID
import io.github.ravindren_sm.openstream.ui.screens.SHOW_PLAY_ID
import io.github.ravindren_sm.openstream.ui.focus.enterAt
import androidx.compose.ui.focus.FocusDirection
import io.github.ravindren_sm.openstream.data.NextEpisodeFinder
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.ui.screens.FAVORITES_GROUP
import io.github.ravindren_sm.openstream.ui.screens.GUIDE_GROUP_ID
import io.github.ravindren_sm.openstream.ui.screens.GuideScreen
import io.github.ravindren_sm.openstream.ui.screens.SportsScreen
import io.github.ravindren_sm.openstream.ui.screens.categoryFocusId
import io.github.ravindren_sm.openstream.ui.screens.SportsState
import io.github.ravindren_sm.openstream.ui.screens.sportsEntries
import io.github.ravindren_sm.openstream.ui.screens.sportsRowFocusId
import io.github.ravindren_sm.openstream.ui.screens.GuideState
import io.github.ravindren_sm.openstream.ui.screens.guideRowFocusId
import io.github.ravindren_sm.openstream.data.SearchCatalog
import io.github.ravindren_sm.openstream.data.SearchFilters
import io.github.ravindren_sm.openstream.data.SearchSources
import io.github.ravindren_sm.openstream.data.TitleIndexer
import io.github.ravindren_sm.openstream.data.KidsRules
import io.github.ravindren_sm.openstream.data.KidsTags
import io.github.ravindren_sm.openstream.data.tmdb.TagStorage
import io.github.ravindren_sm.openstream.data.tmdb.TaggerCounts
import io.github.ravindren_sm.openstream.data.tmdb.TitleTagger
import io.github.ravindren_sm.openstream.data.tmdb.TmdbClient
import io.github.ravindren_sm.openstream.data.tmdb.TmdbKeyStore
import io.github.ravindren_sm.openstream.data.imdb.ImdbRatingsLoader
import io.github.ravindren_sm.openstream.data.imdb.ImdbResult
import io.github.ravindren_sm.openstream.ui.components.TextEntryDialog
import io.github.ravindren_sm.openstream.profiles.CategoryRules
import io.github.ravindren_sm.openstream.profiles.ParentalPolicy
import io.github.ravindren_sm.openstream.profiles.PinResult
import io.github.ravindren_sm.openstream.profiles.ProfileInfo
import io.github.ravindren_sm.openstream.ui.components.PinDialog
import io.github.ravindren_sm.openstream.ui.screens.ProfileDraft
import io.github.ravindren_sm.openstream.ui.screens.ProfilesModel
import io.github.ravindren_sm.openstream.ui.screens.ProfilesScreen
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
import io.github.ravindren_sm.openstream.ui.screens.genreFocusId
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
import io.github.ravindren_sm.openstream.data.GamesSearch
import androidx.lifecycle.compose.currentStateAsState
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
import io.github.ravindren_sm.openstream.ui.screens.UnavailableScreen
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
    val liveGroupPrefs = LocalContext.current.let { c -> remember { c.applicationContext.getSharedPreferences("browse", android.content.Context.MODE_PRIVATE) } }
    val liveState = remember(portalEpoch) {
        LiveBrowseState(LiveCatalog(session)).also { s ->
            s.rememberedGroupId = { liveGroupPrefs.getString("live_group", null) }
            s.onGroupChosen = { id -> liveGroupPrefs.edit().putString("live_group", id).apply() }
        }
    }
    val livePlayback = remember(portalEpoch) { LivePlayback(session) }
    // TV Guide: which channels it lists, and the per-channel guide cache.
    val liveCatalog = remember(portalEpoch) { LiveCatalog(session) }
    val guideLoader = remember(portalEpoch) { GuideLoader(session) }
    val appContextForFiles = LocalContext.current.applicationContext
    // Game listings are kept in the app's private storage so Sports shows them at once after a restart.
    val gamesFile = remember { java.io.File(appContextForFiles.filesDir, "sports-listings.json") }
    val sportsState = remember(portalEpoch) {
        SportsState(liveCatalog, guideLoader, saveGames = { text -> runCatching { gamesFile.writeText(text) } }).also { st ->
            runCatching { if (gamesFile.exists()) GamesSearch.decode(gamesFile.readText()).let { (list, at) -> st.games.restore(list, at) } }
        }
    }
    val guideState = remember(portalEpoch) { GuideState() }
    // Movies and Shows get the same treatment, plus the portal address (poster paths are relative to it) and
    // the resume positions, which live on the device.
    var portalUrl by remember { mutableStateOf((loaded as? SettingsStore.Loaded.Found)?.settings?.portalUrl.orEmpty()) }
    val vodCatalog = remember(portalEpoch) { VodCatalog(session) }
    val viewsContext = LocalContext.current.applicationContext
    val browseViews = remember { io.github.ravindren_sm.openstream.ui.screens.BrowseViewStore(viewsContext.getSharedPreferences("browse", android.content.Context.MODE_PRIVATE)) }
    val moviesState = remember(portalEpoch) { VodBrowseState(vodCatalog, VodKind.MOVIES).also { it.viewStore = browseViews } }
    val showsState = remember(portalEpoch) { VodBrowseState(vodCatalog, VodKind.SHOWS).also { it.viewStore = browseViews } }
    val vodPlayback = remember(portalEpoch) { VodPlayback(session) }
    // The show page being viewed (seasons, loaded episodes, scroll), kept while an episode plays.
    var showDetail: ShowDetailState? by remember(portalEpoch) { mutableStateOf(null) }
    val appContext = LocalContext.current
    // The person watching. Until the profile picker exists there is one default profile ("Family"), made on first
    // use; history and resume positions belong to it.
    val repo = remember { ProfileRepository(OpenStreamDb.get(appContext)) }
    var profileId by remember { mutableLongStateOf(0L) }
    var profiles by remember { mutableStateOf<List<ProfileInfo>>(emptyList()) }
    val profilePrefs = remember { appContext.getSharedPreferences("profiles", android.content.Context.MODE_PRIVATE) }
    // "Who's watching?": shown at launch when there is more than one profile (the choice is then required), or on request.
    var profileScreen by remember { mutableStateOf<String?>(null) }            // null, "pick" or "manage"
    var profileScreenRequired by remember { mutableStateOf(false) }
    // The element that opened the profiles layer (for example Settings' Manage row): focus goes back there when it closes.
    var profileReturnFocus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(profileScreen) {
        if (profileScreen == null) profileReturnFocus?.let { id ->
            androidx.compose.runtime.withFrameNanos { }
            memory.tryFocus(id); profileReturnFocus = null
        }
    }
    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) { repo.ensureDefaultProfile(); repo.profiles() }
        profiles = list
        if (list.size > 1) { profileScreen = "pick"; profileScreenRequired = true }
        else profileId = list.first().id
    }
    val profile: ProfileInfo? = profiles.firstOrNull { it.id == profileId }
    val isKids = profile?.isKids == true
    val resumeStore = remember { ProfileResumeStore(PrefsResumeStore(appContext)) { profileId } }
    val recorder = remember { RoomHistoryRecorder(repo, { profileId }, io.github.ravindren_sm.openstream.data.AppScope.io) }
    // Favorites (channels, movies, shows) of the current profile, shared by every screen that shows a heart.
    val favorites = remember { FavoritesState(repo, { profileId }, io.github.ravindren_sm.openstream.data.AppScope.io) }
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
    // Set when an episode hands over to the next one ("Up next"); cleared when the player is left.
    var playOverride: PlayTarget? by remember { mutableStateOf(null) }
    val nextFinder = remember(portalEpoch) { NextEpisodeFinder(vodCatalog) }
    val connected = session.status.let { it is SessionStatus.Ready || it is SessionStatus.Degraded }
    // A portal already saved: hand it to the session (it connects while the app is in the foreground).
    LaunchedEffect(Unit) { (loaded as? SettingsStore.Loaded.Found)?.let { session.configure(it.settings) } }

    // Search: the box text and the last outcome live here so they survive opening a result and coming back.
    val searchCatalog = remember(portalEpoch) { SearchCatalog(session) }
    val searchState = remember(portalEpoch) { SearchState() }
    val db = remember { OpenStreamDb.get(appContext) }
    // The profile's content filter (rating floor), kept per profile on the device.
    val policyStore = remember { ContentPolicyStore(appContext.getSharedPreferences("search", android.content.Context.MODE_PRIVATE)) }
    var contentPolicy by remember { mutableStateOf(ContentPolicy()) }
    LaunchedEffect(profileId) { if (profileId > 0L) contentPolicy = policyStore.load(profileId) }
    // Optional online tags (themoviedb.org): the viewer's own key, sealed on the device; a background crawl that fills in age certifications.
    val tagPrefs = remember { appContext.getSharedPreferences("search", android.content.Context.MODE_PRIVATE) }
    val tagKeyStore = remember { TmdbKeyStore(java.io.File(appContext.filesDir, "tag-key.bin"), io.github.ravindren_sm.openstream.data.AndroidKeystoreKey.box()) }
    var tagKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { tagKey = withContext(Dispatchers.IO) { tagKeyStore.load() } }
    val tagEnricher = remember { io.github.ravindren_sm.openstream.data.TagEnricher(db.titleTags()) }
    var tagTick by remember { mutableIntStateOf(0) }
    var tagKeyDialog by remember { mutableStateOf(false) }
    val tagger = remember(tagKey) {
        val client = tagKey?.let { TmdbClient(it) }
        val tags = db.titleTags()
        TitleTagger(
            storage = object : TagStorage {
                override suspend fun nextUntagged(limit: Int) = tags.untagged(limit)
                override suspend fun nextNeedingImdbId(limit: Int) = tags.needingImdbId(limit)
                override suspend fun save(tag: io.github.ravindren_sm.openstream.profiles.db.TitleTagEntity) = tags.put(tag)
                override suspend fun counts() = TaggerCounts(tags.checked(), tags.matched(), tags.graded())
            },
            api = { client }, enabled = { tagPrefs.getBoolean("tagging_on", false) }, setEnabled = { tagPrefs.edit().putBoolean("tagging_on", it).apply() },
        )
    }
    LaunchedEffect(tagger, tagTick) { withContext(Dispatchers.IO) { tagger.refreshStatus(tagKey != null) } }
    // IMDb's free ratings file, for personal use (the viewer presses Download; nothing is sent, the file comes to the device).
    var imdbBusy by remember { mutableStateOf(false) }
    var imdbMessage by remember { mutableStateOf<String?>(null) }
    var imdbCounts by remember { mutableStateOf(0 to 0) }                  // titles with an id, titles rated
    LaunchedEffect(tagTick, imdbBusy, tagger.status.counts.checked / 1000) { imdbCounts = withContext(Dispatchers.IO) { db.titleTags().withImdbId() to db.titleTags().imdbScored() } }
    var kidsTags by remember { mutableStateOf(KidsTags.NONE) }
    LaunchedEffect(profileId, profiles, tagger.status.counts.checked / 200) {
        kidsTags = if (profile?.isKids == true) withContext(Dispatchers.IO) {
            val rows = db.titleTags().kidsView()
            KidsTags(rows.filter { it.certAge != null }.associate { it.id to it.certAge!! }, rows.filter { it.flagged }.mapTo(HashSet()) { it.id })
        } else KidsTags.NONE
    }
    // A Kids profile's rules sit on top of the rating floor: age label, word list, allowed categories, no Live TV.
    var kidsAllowed by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(profileId, profiles) { kidsAllowed = if (profile?.isKids == true) withContext(Dispatchers.IO) { repo.kidsAllowed(profileId) } else emptySet() }
    val effectivePolicy = remember(contentPolicy, profile?.id, profile?.isKids, profile?.maxAgeLevel, kidsTags) {
        if (profile?.isKids == true) contentPolicy.copy(kids = KidsRules(profile.maxAgeLevel, tags = kidsTags)) else contentPolicy
    }
    val vodCategoryRule: (io.github.ravindren_sm.openstream.data.VodCategory) -> Boolean = remember(profile, kidsAllowed) {
        { c -> profile == null || ParentalPolicy.categoryVisible(profile, c.id, io.github.ravindren_sm.openstream.provider.PortalText.decodeEntities(c.title), kidsAllowed) }
    }
    // The household PIN: ask for it before an action, or set one first when none exists yet.
    var pinFlow by remember { mutableStateOf<Triple<String, Boolean, () -> Unit>?>(null) }     // title, choosing a new PIN, what to do afterwards
    val shellScope = rememberCoroutineScope()
    fun withPin(title: String, action: () -> Unit) {
        shellScope.launch { if (!withContext(Dispatchers.IO) { repo.pins.isSet() }) action() else pinFlow = Triple(title, false, action) }
    }
    fun choosePin(title: String, action: () -> Unit) { pinFlow = Triple(title, true, action) }

    // ---- profile actions ----
    var pinIsSet by remember { mutableStateOf(false) }
    LaunchedEffect(pinFlow == null, profiles) { pinIsSet = withContext(Dispatchers.IO) { repo.pins.isSet() } }
    fun startAs(p: ProfileInfo) {
        profileId = p.id
        profilePrefs.edit().putLong("last_profile", p.id).apply()
        profileScreen = null; profileScreenRequired = false
        searchState.query = ""; searchState.phase = SearchPhase.Idle; searchState.shown = null; searchState.shownKey = null; showDetail = null
        stack = NavStack.start(Route.HOME)
    }
    // Leaving a Kids profile needs the PIN (when one is set); every other switch is free.
    fun chooseProfile(p: ProfileInfo) { if (profile?.isKids == true && p.id != profileId) withPin("Enter the PIN to switch profile") { startAs(p) } else startAs(p) }
    var kidsChoices by remember { mutableStateOf<List<io.github.ravindren_sm.openstream.ui.components.Choice>>(emptyList()) }
    var kidsPickAllowed by remember { mutableStateOf<Set<String>>(emptySet()) }
    fun saveProfile(d: ProfileDraft) {
        val before = profiles.firstOrNull { it.id == d.id }
        val needsPin = (d.isKids && before?.isKids != true) || (d.showAdult && before?.showAdultCategories != true)
        fun doSave() {
            shellScope.launch(Dispatchers.IO) {
                val info = ProfileInfo(d.id, d.name, d.isKids, d.maxAgeLevel, d.showAdult, d.colorIndex)
                val saved = if (d.id == 0L) repo.create(d.name, d.colorIndex, d.isKids, d.maxAgeLevel, d.showAdult) else { repo.update(info, d.colorIndex); info }
                // A new Kids profile starts with the categories whose names look kid-friendly; an adult can change them afterwards.
                if (d.isKids && before?.isKids != true) {
                    val cats = listOf(VodKind.MOVIES, VodKind.SHOWS).flatMap { k -> (vodCatalog.categories(k) as? CatalogResult.Ok)?.value.orEmpty() }
                    repo.seedKidsAllowed(saved.id, cats.map { it.id to PortalText.decodeEntities(it.title) })
                }
                profiles = repo.profiles()
                if (saved.id == profileId) kidsAllowed = if (saved.isKids) repo.kidsAllowed(saved.id) else emptySet()
            }
        }
        // Kids profiles and adult categories only mean something with a PIN: set one first if the household has none.
        if (needsPin) shellScope.launch { if (!withContext(Dispatchers.IO) { repo.pins.isSet() }) choosePin("Choose a PIN to protect Kids profiles") { doSave() } else doSave() } else doSave()
    }
    fun deleteProfile(p: ProfileInfo) {
        shellScope.launch(Dispatchers.IO) {
            repo.delete(p.id)
            val rest = repo.profiles(); profiles = rest
            if (p.id == profileId) withContext(Dispatchers.Main) { rest.firstOrNull()?.let(::startAs) }
        }
    }
    suspend fun verifyPin(pin: String): String? = when (val r = withContext(Dispatchers.IO) { repo.pins.verify(pin) }) {
        PinResult.Ok, PinResult.NotSet -> null
        is PinResult.Wrong -> "Wrong PIN. ${r.attemptsLeft} ${if (r.attemptsLeft == 1) "try" else "tries"} left."
        is PinResult.Locked -> "Too many tries. Wait ${((r.untilMillis - System.currentTimeMillis()) / 1000 + 1).coerceAtLeast(1)} seconds."
    }
    // Per-profile switches: order search by viewing stats (on by default), and use the saved search defaults beyond search (off by default).
    var orderByStats by remember { mutableStateOf(true) }
    var applyDefaultsEverywhere by remember { mutableStateOf(false) }
    LaunchedEffect(profileId) {
        if (profileId > 0L) { orderByStats = policyStore.flag(profileId, "order_by_stats", true); applyDefaultsEverywhere = policyStore.flag(profileId, "apply_defaults", false) }
    }
    var personalization by remember { mutableStateOf(io.github.ravindren_sm.openstream.data.Personalization()) }
    var statsTick by remember { mutableIntStateOf(0) }
    var statsSummary by remember { mutableStateOf("Nothing yet") }
    LaunchedEffect(profileId, statsTick) {
        if (profileId <= 0L) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            personalization = repo.personalization(profileId)
            suspend fun top(kind: String) = repo.topStats(profileId, kind, 3).joinToString(", ") { it.label }
            val parts = listOf("Shows" to top("TITLE"), "Genres" to top("GENRE"), "Languages" to top("LANGUAGE")).filter { it.second.isNotEmpty() }
            statsSummary = if (parts.isEmpty()) "Nothing yet" else parts.joinToString(" · ") { "${it.first}: ${it.second}" }
        }
    }
    val tileScopeForSearch = rememberCoroutineScope()
    fun recordSearch(text: String) { tileScopeForSearch.launch(Dispatchers.IO) { repo.recordSearch(profileId, text) } }
    LaunchedEffect(searchState.query, searchState.submitTick, connected, searchState.filters, effectivePolicy, orderByStats) {
        val q = searchState.query.trim()
        if (q.length < SearchRanking.MIN_QUERY) { searchState.phase = SearchPhase.Idle; searchState.shown = null; searchState.shownKey = null; return@LaunchedEffect }
        // A dropped connection keeps what is on screen; the search runs again once it is back.
        if (!connected) return@LaunchedEffect
        val submitted = searchState.submitTick > 0 && searchState.submitTick != lastSubmitHandled
        // The same search already answered (for example the connection blinked, or the policy object was rebuilt): keep it.
        val key = listOf(q, searchState.filters.encode(), effectivePolicy.hashCode(), orderByStats).joinToString("\u0001")
        if (key == searchState.shownKey && searchState.phase is SearchPhase.Done) { lastSubmitHandled = searchState.submitTick; return@LaunchedEffect }
        // Wait for a pause in typing (shorter once there are three letters), unless Done was pressed.
        delay(if (submitted) 0L else if (q.length >= 3) 350L else 800L)
        lastSubmitHandled = searchState.submitTick
        searchState.phase = SearchPhase.Loading
        val result = withContext(Dispatchers.IO) { searchCatalog.search(q, searchState.filters, searchState.categories, effectivePolicy, if (orderByStats) personalization else null, tagEnricher::enrich) }
        searchState.phase = when (result) {
            is CatalogResult.Ok -> { searchState.shown = result.value; searchState.shownKey = key; SearchPhase.Done(result.value) }
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
    // Pinned categories and the live group, shown as rows on Home. Pins are per profile; each row asks the portal once and is kept 10 minutes.
    var pins: List<io.github.ravindren_sm.openstream.profiles.db.PinnedCategoryEntity> by remember { mutableStateOf(emptyList()) }
    suspend fun loadPins() {
        pins = listOf(PinKinds.MOVIES, PinKinds.SHOWS, PinKinds.LIVE).flatMap { repo.pinnedCategories(profileId, it) }
    }
    LaunchedEffect(profileId) { if (profileId > 0L) withContext(Dispatchers.IO) { loadPins() } }
    val homeFeed = remember(portalEpoch) { HomeFeed(vodCatalog, liveCatalog) }
    var pinnedRows: List<PinnedRow> by remember { mutableStateOf(emptyList()) }
    fun togglePin(kind: String, id: String, title: String) {
        tileScopeForSearch.launch(Dispatchers.IO) {
            val exists = pins.any { it.kind == kind && it.categoryId == id }
            if (exists) repo.unpinCategory(profileId, kind, id)
            else {
                if (kind == PinKinds.LIVE) pins.filter { it.kind == PinKinds.LIVE }.forEach { repo.unpinCategory(profileId, PinKinds.LIVE, it.categoryId) }
                repo.pinCategory(profileId, kind, id, title)
            }
            loadPins()
        }
    }
    // The guide's first group: favorites when there are some, otherwise the portal's first group ("All channels").
    val onGuide = stack.current.route == Route.TV_GUIDE
    LaunchedEffect(onGuide, connected, favorites.all.size) {
        if (!onGuide || !connected || guideState.groupId != null) return@LaunchedEffect
        if (!liveState.genresLoaded) withContext(Dispatchers.IO) { liveState.loadGenres() }
        val favs = favorites.channels()
        if (favs.isNotEmpty()) guideState.select(FAVORITES_GROUP, "Favorites", PagedChannels.fixed(favs.map(FavoriteItems::toChannel)))
        else liveState.genres.firstOrNull()?.let { g -> guideState.select(g.id, PortalText.decodeEntities(g.title), PagedChannels { page -> liveCatalog.channels(g.id, page) }) }
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
    // Saved searches: named searches over the on-device title index (per profile; adult profiles only).
    val savedStore = remember { io.github.ravindren_sm.openstream.data.SavedSearchStore(appContext.getSharedPreferences("saved_searches", android.content.Context.MODE_PRIVATE)) }
    val savedRunner = remember { io.github.ravindren_sm.openstream.data.SavedSearchRunner(db.catalogIndex(), tagEnricher) }
    var savedList by remember(profileId) { mutableStateOf(savedStore.list(profileId)) }
    var savedSelectedId: String? by remember(profileId) { mutableStateOf(null) }
    var savedResults: io.github.ravindren_sm.openstream.ui.screens.SavedResults? by remember { mutableStateOf(null) }
    var savedLoading by remember { mutableStateOf(false) }
    LaunchedEffect(savedSelectedId, savedList, effectivePolicy, profile?.showAdultCategories) {
        val sel = savedList.firstOrNull { it.id == savedSelectedId }
        if (sel == null) { savedResults = null; return@LaunchedEffect }
        savedLoading = true
        val res = withContext(Dispatchers.IO) { savedRunner.run(sel) }
        // The same rules as the browse screens: adult categories only for a profile that shows them, and the profile's rating policy.
        val adultIds = (moviesState.categories + showsState.categories).filter { CategoryRules.isAdult(io.github.ravindren_sm.openstream.provider.PortalText.decodeEntities(it.title)) }.mapTo(HashSet()) { it.id }
        val items = res.items.filter { (profile?.showAdultCategories == true || it.categoryId !in adultIds) && effectivePolicy.passes(it) }
        savedResults = io.github.ravindren_sm.openstream.ui.screens.SavedResults(items, if (items.size == res.items.size) res.total else items.size)
        savedLoading = false
    }
    var indexTick by remember { mutableIntStateOf(0) }
    // The profile's content filter (rating floor), kept per profile on the device, and the on-device copy of the catalog.
    val indexedTitles = remember { IndexedTitles(db.catalogIndex(), enricher = tagEnricher) }
    val hydrator = remember(portalEpoch) { VodHydrator(session) }
    androidx.compose.runtime.SideEffect {
        for (st in listOf(moviesState, showsState)) { st.indexed = indexedTitles; st.enricher = tagEnricher::enrich; st.ready = { id -> id in indexer.readyCategories } }
    }
    LaunchedEffect(effectivePolicy, moviesState, showsState) { moviesState.applyProfilePolicy(effectivePolicy); showsState.applyProfilePolicy(effectivePolicy) }
    LaunchedEffect(vodCategoryRule, moviesState, showsState) { moviesState.applyCategoryRule(vodCategoryRule); showsState.applyCategoryRule(vodCategoryRule) }
    // The saved search defaults, when this profile asked for them everywhere.
    val defaultScope = if (applyDefaultsEverywhere) searchState.defaultFilters?.takeIf { it.isActive }?.let(::DefaultScope) else null
    // The live groups the viewer may see (the set the TV Guide offers): the Sports rail and the NFL search use these.
    val visibleGenres = liveState.allGenres.filter { g -> liveState.genres.any { it.id == g.id } && (defaultScope?.groupAllowed(g) ?: true) }

    // The games search runs in the background, at most once an hour, so Sports has games ready when opened. Never while
    // something plays (a stream request must not wait behind guide requests), never on a Kids profile, and only while the app
    // is on screen: in the background (another IPTV app open, the stick asleep) it would be a second session on the account.
    val lifecycleState by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val appOnScreen = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    val playing = stack.current.route == Route.LIVE_PLAYER || stack.current.route == Route.VOD_PLAYER
    val nflGenres by androidx.compose.runtime.rememberUpdatedState(visibleGenres)
    LaunchedEffect(connected, playing, appOnScreen, isKids, portalEpoch) {
        if (!connected || playing || !appOnScreen || isKids) return@LaunchedEffect
        delay(20_000)   // let start-up and whatever the viewer opens first go ahead
        if (!liveState.genresLoaded) withContext(Dispatchers.IO) { liveState.loadGenres() }
        while (true) {
            // The groups reach this state on the redraw after they load; check again shortly instead of waiting a full minute
            // (a run once lost its pass that way: the viewer started a game inside that minute).
            if (nflGenres.isEmpty()) { delay(2_000); continue }
            val games = sportsState.games
            if ((games.running || games.due(GAMES_REFRESH_MILLIS)) && nflGenres.isNotEmpty()) withContext(Dispatchers.IO) { games.refresh(nflGenres) }
            delay(60_000)
        }
    }
    LaunchedEffect(defaultScope?.summary(), moviesState, showsState, liveState, profile?.showAdultCategories) {
        moviesState.applyScope(defaultScope); showsState.applyScope(defaultScope)
        liveState.applyGroupFilter { g -> (profile?.showAdultCategories == true || !CategoryRules.isAdult(io.github.ravindren_sm.openstream.provider.PortalText.decodeEntities(g.title))) && (defaultScope?.groupAllowed(g) ?: true) }
    }
    val canIndex = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    androidx.compose.runtime.SideEffect {
        canIndex.set(connected && stack.current.route !in setOf(Route.LIVE_PLAYER, Route.VOD_PLAYER, Route.SETUP) && searchState.phase !is SearchPhase.Loading)
        searchState.index = indexer.status
    }
    val canTag = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    androidx.compose.runtime.SideEffect { canTag.set(stack.current.route !in setOf(Route.LIVE_PLAYER, Route.VOD_PLAYER, Route.SETUP)) }
    LaunchedEffect(tagger, tagTick) { if (tagKey != null && tagPrefs.getBoolean("tagging_on", false)) withContext(Dispatchers.IO) { tagger.run { canTag.get() } } }
    // While the title index is building keep this window awake, or the screensaver would stop the crawl (the app releases its
    // portal session when it is not in front). A flag on this app's own window: no device setting changes.
    val rootView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect { rootView.keepScreenOn = indexer.status.enabled && !indexer.status.complete }
    LaunchedEffect(portalEpoch) { if (portalEpoch > 0) indexer.clear() }
    LaunchedEffect(indexer) { withContext(Dispatchers.IO) { indexer.refreshStatus() } }
    LaunchedEffect(indexer, indexTick) { if (indexer.enabled) withContext(Dispatchers.IO) { indexer.run { canIndex.get() } } }
    // Suggestions follow the typed text only. Once a query has a list it stays put while the viewer moves through it, even if the
    // title index or the channel list grows in the background (that used to reorder the rows under the viewer's focus).
    var suggestedFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(searchState.query, favorites.all, channelsWarm, indexer.status.titles / 500, profileId, onSearchRoute) {
        if (!onSearchRoute) return@LaunchedEffect
        val key = "$profileId\u0001${searchState.query}"
        if (key == suggestedFor && searchState.suggestions.isNotEmpty()) return@LaunchedEffect
        delay(120)
        searchState.suggestions = withContext(Dispatchers.IO) { searchSources.suggestions(profileId, searchState.query, effectivePolicy.kids) }
        suggestedFor = key
    }
    // A picker for the versions of a title opened from Search, and which versions the open page has.
    var pickerOpen by remember { mutableStateOf(false) }
    var autoPlayNext by remember { mutableStateOf(searchPrefs.getBoolean("autoplay_next", true)) }
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
                        stack = stack.withPreviousFocus(when (from) { "home" -> homeFavChannelFocusId(i); "search" -> searchChannelFocusId(i); "guide" -> guideRowFocusId(i); "sports" -> sportsRowFocusId(i); "homepin" -> homePinFocusId(stack.current.params["row"]?.toIntOrNull() ?: 0, i); else -> favChannelFocusId(i) })
                    } else {
                        stack = stack.withPreviousFocus(channelFocusId(i))
                        liveState.channelListState.requestScrollToItem(maxOf(i - 3, 0))
                    }
                },
            )
        } else if (stack.current.route == Route.VOD_PLAYER) {
            val target: PlayTarget? = if (playOverride != null) {
                playOverride
            } else if (stack.current.params["play"] == "history") {
                historyTarget
            } else if (stack.current.params["play"] == "episode") {
                showDetail?.playTarget
            } else if (stack.current.params["src"] == "fav") {
                favItem?.let { PlayTarget(it, it.name, ResumeKeys.movie(it.id)) }
            } else {
                val browse = if (stack.current.params["kind"] == VodKind.SHOWS.name) showsState else moviesState
                browse.titles.items.getOrNull(stack.current.params["item"]?.toIntOrNull() ?: -1)
                    ?.let { PlayTarget(it, it.name, ResumeKeys.movie(it.id), languageText = browse.categories.getOrNull(browse.selected)?.title) }
            }
            VodPlayerScreen(target = target, playback = vodPlayback, resume = resumeStore, memory = memory, portalUrl = portalUrl, history = recorder,
                findNext = nextFinder::find, autoPlayNext = autoPlayNext,
                onPlayNext = { next ->
                    playOverride = next
                    // Opened from the show page: Back lands on the episode now playing, not the one first chosen.
                    val below = stack.frames.getOrNull(stack.frames.size - 2)?.route
                    val page = showDetail
                    val key = io.github.ravindren_sm.openstream.data.NextEpisodeLogic.parseKey(next.resumeKey)
                    if (below == Route.SHOW_DETAIL && page != null && key != null && key.first == page.show.id) {
                        page.showPlayed(key.second, key.third)?.let { stack = stack.withPreviousFocus(it) }
                    }
                })
        } else {
            TopNavBar(
                activeTab = topTab,
                memory = memory,
                onSelectTab = { tab ->
                    // A Kids profile needs the PIN to reach Settings (where profiles and the PIN live).
                    if (tab == Route.SETTINGS && isKids) withPin("Enter the PIN to open Settings") { stack = stack.resetToTab(tab) }
                    else stack = stack.resetToTab(tab)
                },
                tabs = if (isKids) Route.TABS.filter { it != Route.LIVE_TV && it != Route.TV_GUIDE && it != Route.SPORTS && it != Route.SAVED } else Route.TABS,
            )
            if (stack.current.route == Route.HOME) {
                HomeScreen(
                    continueTiles = continueTiles, recentTiles = recentTiles, portalUrl = portalUrl, memory = memory,
                    pinnedRows = pinnedRows,
                    onOpenPinnedTitle = { item ->
                        if (item.isSeries) {
                            if (showDetail?.show?.id != item.id) showDetail = ShowDetailState(item, vodCatalog)
                            stack = stack.navigate(Route.SHOW_DETAIL, emptyMap(), currentFocusId = memory.focusedId)
                        } else {
                            favItem = item; detailVersions = emptyList()
                            stack = stack.navigate(Route.MOVIE_DETAIL, mapOf("src" to "fav"), currentFocusId = memory.focusedId)
                        }
                    },
                    onOpenPinnedChannel = { r, list, i ->
                        favPlayer = PagedChannels.fixed(list)
                        stack = stack.navigate(Route.LIVE_PLAYER, mapOf("item" to "$i", "src" to "fav", "from" to "homepin", "row" to "$r"), currentFocusId = memory.focusedId)
                    },
                    favoriteChannels = favorites.channels(),
                    onRemoveFavoriteChannel = { f -> favorites.toggle(f.kind, f.refId, f.name, f.posterUri, f) },
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
                    onDoor = { route -> stack = stack.enterTab(route).let { s -> memory.focusedId?.let(s::withPreviousFocus) ?: s } },   // Back returns to the button used, not the tab
                    liveAllowed = !isKids,
                    savedSearches = if (isKids) emptyList() else savedList,
                    onOpenSaved = { s ->
                        savedSelectedId = s.id
                        stack = stack.enterTab(Route.SAVED).let { st -> memory.focusedId?.let(st::withPreviousFocus) ?: st }   // Back returns to the tile used
                    },
                )
            } else if (stack.current.route == Route.MOVIES || stack.current.route == Route.SHOWS) {
                val browse = if (stack.current.route == Route.SHOWS) showsState else moviesState
                VodBrowseScreen(
                    state = browse, connected = connected, portalUrl = portalUrl, memory = memory, isFavorite = favorites::isVod,
                    isPinned = { c -> pins.any { it.kind == (if (browse.kind == VodKind.SHOWS) PinKinds.SHOWS else PinKinds.MOVIES) && it.categoryId == c.id } },
                    onTogglePin = { c -> togglePin(if (browse.kind == VodKind.SHOWS) PinKinds.SHOWS else PinKinds.MOVIES, c.id, c.title) },
                    onOpen = { index, entry ->
                        // The portal keeps shows among the movies: the title's own flag decides which page opens.
                        val focusId = titleFocusId(index)
                        fun open(item: VodItem) {
                            val route = if (item.isSeries) Route.SHOW_DETAIL else Route.MOVIE_DETAIL
                            if (item.isSeries && showDetail?.show?.id != item.id) showDetail = ShowDetailState(item, vodCatalog)
                            stack = stack.navigate(route, mapOf("item" to "$index", "kind" to browse.kind.name), currentFocusId = focusId)
                        }
                        val category = browse.categories.getOrNull(browse.selected)
                        if (browse.usingIndex && entry.description == null && category != null) {
                            // A row from the device copy has no description or cast: fetch the full entry, then open.
                            tileScopeForSearch.launch {
                                val full = withContext(Dispatchers.IO) { tagEnricher.enrich(listOf(hydrator.hydrate(entry, category.id))).first() }
                                browse.titles.replaceAt(index, full)
                                open(full)
                            }
                        } else open(entry)
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
                    // Down from the tab bar comes back to Play, not to whichever button sits under the tab.
                    modifier = Modifier.enterAt(memory, setOf(FocusDirection.Down)) { DETAIL_PLAY_ID },
                )
            } else if (stack.current.route == Route.SHOW_DETAIL) {
                val detail = showDetail
                if (detail == null) {
                    UnavailableScreen()
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
                        modifier = Modifier.enterAt(memory, setOf(FocusDirection.Down)) { SHOW_PLAY_ID },
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
            } else if (stack.current.route == Route.SETTINGS) {
                SettingsScreen(
                    memory = memory,
                    model = SettingsModel(
                        portalStatus = when (val st = session.status) {
                            is SessionStatus.Ready -> "Connected" + (st.expiry?.let { " · account active until $it" } ?: "")
                            SessionStatus.Connecting -> "Connecting…"
                            SessionStatus.Degraded -> "Connection check failed; retrying"
                            is SessionStatus.Failed -> st.message
                            SessionStatus.Idle -> "Not connected"
                        },
                        index = indexer.status,
                        defaultFilters = searchState.defaultFilters?.summary(),
                        autoPlayNext = autoPlayNext,
                        posterCacheMb = PosterLoader.cacheBytes() / (1024 * 1024),
                        version = runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }.getOrNull().orEmpty(),
                        onChangePortal = { stack = NavStack.start(Route.SETUP) },
                        onBuildIndex = { indexer.enable(); indexTick++ },
                        onPauseIndex = { indexer.pause(); tileScopeForSearch.launch(Dispatchers.IO) { delay(300); indexer.refreshStatus() } },
                        onClearIndex = { tileScopeForSearch.launch(Dispatchers.IO) { indexer.clear() } },
                        onClearDefaultFilters = { searchPrefs.edit().remove("default_filters").apply(); searchState.defaultFilters = null },
                        onClearRecentSearches = { tileScopeForSearch.launch(Dispatchers.IO) { repo.clearRecentSearches(profileId) } },
                        onClearHistory = { tileScopeForSearch.launch(Dispatchers.IO) { repo.clearHistory(profileId); loadTiles() } },
                        onClearFavorites = { tileScopeForSearch.launch(Dispatchers.IO) { repo.clearFavorites(profileId); favorites.reload() } },
                        onToggleAutoPlay = { autoPlayNext = !autoPlayNext; searchPrefs.edit().putBoolean("autoplay_next", autoPlayNext).apply() },
                        onClearPosterCache = { PosterLoader.clearCache() },
                        contentPolicy = contentPolicy,
                        onSetMinRating = { v -> contentPolicy = contentPolicy.copy(minRating = v).also { policyStore.save(profileId, it) } },
                        orderByStats = orderByStats, statsSummary = statsSummary,
                        onToggleOrderByStats = { orderByStats = !orderByStats; policyStore.setFlag(profileId, "order_by_stats", orderByStats) },
                        onResetStats = { tileScopeForSearch.launch(Dispatchers.IO) { repo.clearStats(profileId); statsTick++ } },
                        applyDefaultsEverywhere = applyDefaultsEverywhere,
                        defaultsSummary = searchState.defaultFilters?.summary()?.ifEmpty { null } ?: "None saved (set them in Search, Save as default)",
                        onToggleApplyDefaults = { applyDefaultsEverywhere = !applyDefaultsEverywhere; policyStore.setFlag(profileId, "apply_defaults", applyDefaultsEverywhere) },
                        profileSummary = profile?.let { "${it.name} (${if (it.isKids) "Kids" else "Adult"})" } ?: "No profile",
                        onSwitchProfile = { profileReturnFocus = memory.focusedId; profileScreen = "pick"; profileScreenRequired = false },
                        onManageProfiles = { profileReturnFocus = memory.focusedId; withPin("Enter the PIN to manage profiles") { profileScreen = "manage"; profileScreenRequired = false } },
                        tagStatus = tagger.status,
                        onSetTagKey = { tagKeyDialog = true },
                        onToggleTagging = { tagPrefs.edit().putBoolean("tagging_on", !tagPrefs.getBoolean("tagging_on", false)).apply(); tagTick++ },
                        onClearTags = { shellScope.launch(Dispatchers.IO) { tagPrefs.edit().putBoolean("tagging_on", false).apply(); tagKeyStore.clear(); db.titleTags().clear(); tagKey = null; tagTick++ } },
                        imdbWithId = imdbCounts.first, imdbScored = imdbCounts.second, imdbBusy = imdbBusy, imdbMessage = imdbMessage,
                        onDownloadImdb = {
                            imdbBusy = true; imdbMessage = null
                            shellScope.launch(Dispatchers.IO) {
                                val result = ImdbRatingsLoader(db.titleTags()).load()
                                imdbMessage = when (result) { is ImdbResult.Done -> null; is ImdbResult.Failed -> result.message }
                                imdbBusy = false
                            }
                        },
                        onClearImdb = { shellScope.launch(Dispatchers.IO) { db.titleTags().forgetImdbScores(); tagTick++ } },
                        onToggleHideUnrated = { contentPolicy = contentPolicy.copy(hideUnrated = !contentPolicy.hideUnrated).also { policyStore.save(profileId, it) } },
                    ),
                )
            } else if (stack.current.route == Route.SPORTS) {
                // The groups the viewer may see (the same set the TV Guide offers), and the movie and show categories.
                LaunchedEffect(connected) {
                    if (!connected) return@LaunchedEffect
                    withContext(Dispatchers.IO) {
                        if (!liveState.genresLoaded) liveState.loadGenres()
                        if (!moviesState.categoriesLoaded) moviesState.loadCategories()
                        if (!showsState.categoriesLoaded) showsState.loadCategories()
                    }
                }
                val entries = remember(visibleGenres, moviesState.categories, showsState.categories) { sportsEntries(visibleGenres, moviesState.categories, showsState.categories) }
                SportsScreen(
                    state = sportsState, entries = entries, allGenres = visibleGenres,
                    loading = !liveState.genresLoaded || !moviesState.categoriesLoaded || !showsState.categoriesLoaded,
                    loader = guideLoader, playback = livePlayback, connected = connected, memory = memory,
                    onOpenChannel = { list, i ->
                        favPlayer = PagedChannels.fixed(list)
                        stack = stack.navigate(Route.LIVE_PLAYER, mapOf("item" to "$i", "src" to "fav", "from" to "sports"), currentFocusId = memory.focusedId)
                    },
                    onOpenOnDemand = { e ->
                        val browse = if (e.kind == VodKind.SHOWS) showsState else moviesState
                        val index = browse.categories.indexOfFirst { it.id == e.category.id }
                        if (index >= 0) {
                            browse.select(index)
                            browse.categoryListState.requestScrollToItem(maxOf(index - 3, 0))
                            stack = stack.navigate(if (e.kind == VodKind.SHOWS) Route.SHOWS else Route.MOVIES, emptyMap(), currentFocusId = memory.focusedId)
                        }
                    },
                )
            } else if (stack.current.route == Route.TV_GUIDE) {
                GuideScreen(
                    state = guideState, favoriteCount = favorites.channels().size, genres = liveState.allGenres.filter { g -> liveState.genres.any { it.id == g.id } && (defaultScope?.groupAllowed(g) ?: true) }, loader = guideLoader,
                    connected = connected, memory = memory,
                    onChooseGroup = { id, title ->
                        guideState.select(id, title,
                            if (id == FAVORITES_GROUP) PagedChannels.fixed(favorites.channels().map(FavoriteItems::toChannel))
                            else PagedChannels { page -> liveCatalog.channels(id, page) })
                    },
                    onOpenChannel = { list, i ->
                        favPlayer = PagedChannels.fixed(list)
                        stack = stack.navigate(Route.LIVE_PLAYER, mapOf("item" to "$i", "src" to "fav", "from" to "guide"), currentFocusId = memory.focusedId)
                    },
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
            } else if (stack.current.route == Route.SAVED) {
                io.github.ravindren_sm.openstream.ui.screens.SavedSearchesScreen(
                    searches = savedList, selectedId = savedSelectedId, results = savedResults, loading = savedLoading,
                    indexReady = indexer.status.titles > 0, portalUrl = portalUrl, memory = memory, isFavorite = favorites::isVod,
                    onSelect = { savedSelectedId = it.id },
                    onOpen = { item ->
                        // A row from the device copy has no description or cast: fetch the full entry, then open it like the browse screens do.
                        tileScopeForSearch.launch {
                            val full = withContext(Dispatchers.IO) { tagEnricher.enrich(listOf(hydrator.hydrate(item, item.categoryId ?: ""))).first() }
                            favItem = full; detailVersions = emptyList()
                            if (full.isSeries) {
                                if (showDetail?.show?.id != full.id) showDetail = ShowDetailState(full, vodCatalog)
                                stack = stack.navigate(Route.SHOW_DETAIL, emptyMap(), currentFocusId = memory.focusedId)
                            } else stack = stack.navigate(Route.MOVIE_DETAIL, mapOf("src" to "fav"), currentFocusId = memory.focusedId)
                        }
                    },
                    onSave = { s -> savedList = savedStore.save(profileId, s); savedSelectedId = s.id },
                    onDelete = { s -> savedList = savedStore.delete(profileId, s.id); if (savedSelectedId == s.id) savedSelectedId = null },
                    newId = savedStore::newId,
                )
            } else if (stack.current.route == Route.LIVE_TV) {
                LiveTvScreen(
                    state = liveState,
                    connected = connected,
                    memory = memory,
                    isFavorite = favorites::isChannel, onToggleFavorite = favorites::toggleChannel,
                    pinnedGenreId = pins.firstOrNull { it.kind == PinKinds.LIVE }?.categoryId,
                    onTogglePinGenre = { g -> togglePin(PinKinds.LIVE, g.id, g.title) },
                    guide = guideLoader,
                    onOpenChannel = { index, _ ->
                        stack = stack.navigate(Route.LIVE_PLAYER, mapOf("item" to "$index"), currentFocusId = memory.focusedId)
                    },
                )
            } else {
                UnavailableScreen(message = "This screen is not available.")
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
        if (tagKeyDialog) {
            TextEntryDialog(
                title = "Online tag service key", label = "Key", memory = memory,
                note = "Paste your free key from themoviedb.org (Settings, API). The app then sends only title names and years to that site, one title at a time, and keeps what comes back on this device. The key is stored sealed and never shown again.",
                onSave = { k -> tagKeyDialog = false; shellScope.launch(Dispatchers.IO) { tagKeyStore.save(k); tagKey = k; tagTick++ } }, onCancel = { tagKeyDialog = false },
            )
        }
        profileScreen?.let { which ->
            ProfilesScreen(
                model = ProfilesModel(
                    profiles = profiles, currentId = profileId, pinIsSet = pinIsSet,
                    onClose = if (profileScreenRequired) null else ({ profileScreen = null }),
                    onChoose = ::chooseProfile, onSave = ::saveProfile, onDelete = ::deleteProfile,
                    onChangePin = { if (pinIsSet) withPin("Enter the current PIN") { choosePin("Choose a new PIN") {} } else choosePin("Choose a household PIN") {} },
                    onRemovePin = { withPin("Enter the PIN to remove it") { shellScope.launch(Dispatchers.IO) { repo.pins.clear(); pinIsSet = false } } },
                    guardManage = { proceed -> withPin("Enter the PIN to manage profiles", proceed) },
                    kidsChoices = kidsChoices, kidsAllowed = kidsPickAllowed,
                    onOpenKidsCategories = { p ->
                        tileScopeForSearch.launch(Dispatchers.IO) {
                            val cats = listOf(VodKind.MOVIES, VodKind.SHOWS).flatMap { k -> (vodCatalog.categories(k) as? CatalogResult.Ok)?.value.orEmpty() }
                            kidsChoices = cats.filter { !CategoryRules.isAdult(it.title) }.distinctBy { it.id }.map { io.github.ravindren_sm.openstream.ui.components.Choice(it.id, PortalText.decodeEntities(it.title)) }
                            kidsPickAllowed = repo.kidsAllowed(p.id)
                        }
                    },
                    onToggleKidsCategory = { p, id ->
                        tileScopeForSearch.launch(Dispatchers.IO) {
                            if (id in kidsPickAllowed) repo.disallowKidsCategory(p.id, id)
                            else repo.allowKidsCategory(p.id, id, kidsChoices.firstOrNull { it.id == id }?.label ?: id)
                            kidsPickAllowed = repo.kidsAllowed(p.id)
                            if (p.id == profileId) kidsAllowed = kidsPickAllowed
                        }
                    },
                ),
                memory = memory, startInManage = which == "manage",
            )
        }
        pinFlow?.let { (title, choosing, action) ->
            PinDialog(
                title = title, memory = memory, confirmTwice = choosing,
                onSubmit = { pin -> if (choosing) { withContext(Dispatchers.IO) { repo.pins.set(pin) }; null } else verifyPin(pin) },
                onDone = { pinFlow = null; action() }, onCancel = { pinFlow = null },
            )
        }
    }

    LaunchedEffect(stack.current.route == Route.VOD_PLAYER) { if (stack.current.route != Route.VOD_PLAYER) playOverride = null }
    // Memory is tight on this stick (about 1.7 GB for everything): when a player opens, let go of the poster pictures kept for browsing.
    val inPlayer = stack.current.route == Route.LIVE_PLAYER || stack.current.route == Route.VOD_PLAYER
    // The screen a player was opened from keeps its loaded titles (Back returns to the same place); the other tab's pages are dropped.
    val routeBeforePlayer = remember { arrayOf<Route?>(null) }
    if (!inPlayer) routeBeforePlayer[0] = stack.current.route
    LaunchedEffect(inPlayer) {
        if (inPlayer) {
            PosterLoader.clearCache()
            if (routeBeforePlayer[0] != Route.MOVIES) moviesState.release()
            if (routeBeforePlayer[0] != Route.SHOWS) showsState.release()
            System.gc()
        } else { delay(800); statsTick++ }
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

    // Pinned rows: placeholders at once, then one portal request per row (cached), only while Home is showing and connected.
    LaunchedEffect(onHome, connected, pins, effectivePolicy, defaultScope?.summary()) {
        if (!onHome || !connected) return@LaunchedEffect
        val wanted = HomeRowPlan.titlePins(pins) + listOfNotNull(HomeRowPlan.livePin(pins))
        pinnedRows = wanted.map { PinnedRow(it, null, null, false) }
        wanted.forEachIndexed { i, pin ->
            val row = withContext(Dispatchers.IO) {
                if (pin.kind == PinKinds.LIVE) homeFeed.channelsOf(pin.categoryId)?.let { PinnedRow(pin, null, it, false) }
                else homeFeed.titlesOf(pin.categoryId, keep = { effectivePolicy.passes(it) && (defaultScope?.titlePasses(it) ?: true) })?.let { PinnedRow(pin, it, null, false) }
            } ?: PinnedRow(pin, null, null, true)
            pinnedRows = pinnedRows.toMutableList().also { if (i < it.size) it[i] = row }
        }
    }

    // After each stack change, once the new screen is on screen, put focus where it belongs. On Home that waits for
    // the history, so the first Continue watching tile (not a door) gets it.
    val homeReady = !onHome || tilesFresh
    LaunchedEffect(stack, homeReady) {
        if (!homeReady) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }   // let the new screen compose and attach
        // Movies, Shows and Live TV open on the category or group being shown (Sports can open them on any category).
        fun browseTarget() = when (stack.current.route) {
            Route.MOVIES -> categoryFocusId(moviesState.selected); Route.SHOWS -> categoryFocusId(showsState.selected)
            Route.LIVE_TV -> genreFocusId(liveState.selected); else -> null
        }
        val browseFocus = browseTarget()
        // A tab opened from a Home door starts inside its page, but the page's first item may still be loading (the channel
        // groups, the categories; right after launch the portal may still be connecting): wait for it, up to 8 s, instead of
        // settling on the tab bar. The target is read again each time, since the chosen group is known only once they load.
        if (stack.restoreFocusId == null && stack.current.route.isTab && stack.current.route != Route.HOME) {
            var tries = 0
            while (tries++ < 40) {
                val target = browseTarget() ?: initialFocusId(stack.current.route)
                if (memory.tryFocus(target)) return@LaunchedEffect
                delay(200)
            }
        }
        memory.tryFocusFirst(stack.restoreFocusId, browseFocus, initialFocusId(stack.current.route), if (onHome) recentFocusId(0) else null, if (onHome) homeFavChannelFocusId(0) else null, if (onHome) doorFocusId(0) else null,
            *(if (stack.current.route == Route.FAVORITES) FAVORITES_INITIAL_IDS else emptyArray()), if (stack.current.route == Route.TV_GUIDE) GUIDE_GROUP_ID else null, if (stack.current.route == Route.SAVED) io.github.ravindren_sm.openstream.ui.screens.SAVED_NEW_ID else null, topTab?.let(::tabFocusId))
    }
}

/** How often the background games search reads the guides again. */
private const val GAMES_REFRESH_MILLIS = 60 * 60_000L
