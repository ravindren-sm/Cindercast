package io.github.ravindren_sm.openstream.nav

/** Screens of the app (route list from the prototype's design notes). */
enum class Route(val path: String, val label: String, val isTab: Boolean = false) {
    HOME("/home", "Home", isTab = true),
    LIVE_TV("/live-tv", "Live TV", isTab = true),
    LIVE_PLAYER("/live-tv/player", "Live player"),
    TV_GUIDE("/tv-guide", "TV Guide", isTab = true),
    MOVIES("/movies", "Movies", isTab = true),
    MOVIE_DETAIL("/movies/:id", "Movie"),
    SHOWS("/shows", "Shows", isTab = true),
    SHOW_DETAIL("/shows/:id", "Show"),
    VOD_PLAYER("/vod/player", "VOD player"),
    SEARCH("/search", "Search", isTab = true),
    FAVORITES("/favorites", "Favorites", isTab = true),
    SETTINGS("/settings", "Settings", isTab = true),
    SETUP("/setup", "Setup");

    companion object {
        /** Top navigation bar order. */
        val TABS: List<Route> = entries.filter { it.isTab }
    }
}

/** Focus ID of a top-bar tab. */
fun tabFocusId(route: Route) = "nav-${route.name.lowercase()}"

/** One screen on the stack. [savedFocusId] is the element focused when the user left it. */
data class RouteEntry(
    val route: Route,
    val params: Map<String, String> = emptyMap(),
    val savedFocusId: String? = null,
)

/**
 * Immutable back stack with focus memory (behavior of the prototype's AppStateContext):
 *
 * - [navigate] remembers which element had focus on the screen being left, then pushes
 *   (or, with `replace`, swaps the top screen).
 * - [goBack] pops and returns a stack whose [restoreFocusId] is the focus remembered by the
 *   screen we are returning to. At the root it returns null, so the caller can exit.
 * - [resetToTab] switches top-level tab: stack becomes [Home, tab], so Back from a tab returns
 *   to Home with that tab's button focused.
 *
 * [restoreFocusId] is meant to be applied once after a change, then ignored.
 */
class NavStack private constructor(
    val frames: List<RouteEntry>,
    val restoreFocusId: String? = null,
) {
    init { require(frames.isNotEmpty()) { "stack must not be empty" } }

    val current: RouteEntry get() = frames.last()
    val depth: Int get() = frames.size
    val canGoBack: Boolean get() = frames.size > 1

    fun navigate(
        route: Route,
        params: Map<String, String> = emptyMap(),
        currentFocusId: String?,
        replace: Boolean = false,
    ): NavStack {
        val top = current.copy(savedFocusId = currentFocusId ?: current.savedFocusId)
        val base = frames.dropLast(1)
        val next = if (replace) base + RouteEntry(route, params, top.savedFocusId)
                   else base + top + RouteEntry(route, params)
        return NavStack(next, restoreFocusId = null)
    }

    fun goBack(): NavStack? {
        if (!canGoBack) return null
        val remaining = frames.dropLast(1)
        return NavStack(remaining, restoreFocusId = remaining.last().savedFocusId)
    }

    fun resetToTab(tab: Route): NavStack {
        require(tab.isTab) { "not a top-level tab" }
        if (tab == Route.HOME) return NavStack(listOf(RouteEntry(Route.HOME)), restoreFocusId = tabFocusId(tab))
        return NavStack(
            listOf(RouteEntry(Route.HOME, savedFocusId = tabFocusId(tab)), RouteEntry(tab)),
            restoreFocusId = tabFocusId(tab),
        )
    }

    companion object {
        fun start(route: Route = Route.HOME) = NavStack(listOf(RouteEntry(route)))
    }
}
