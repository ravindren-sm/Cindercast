package io.github.ravindren_sm.openstream.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.LiveChannelPage
import io.github.ravindren_sm.openstream.provider.LiveGenre

/** Result of a catalog call. [Failed] carries fixed, user-facing text: no addresses, MACs or tokens. */
sealed interface CatalogResult<out T> {
    class Ok<T>(val value: T) : CatalogResult<T>
    class Failed(val message: String) : CatalogResult<Nothing>
}

/**
 * Live TV catalog calls for the Live screen. Every call runs on the portal session's own thread
 * (see [PortalSession.withSession]) and blocks, so call it off the main thread. Re-authentication
 * on an expired session is handled by the session manager, once per call.
 */
class LiveCatalog(private val session: PortalSession) {

    /** Genres, always starting with an "All channels" entry (id "*") even if the portal omits one. */
    fun genres(): CatalogResult<List<LiveGenre>> {
        var parsed: List<LiveGenre> = emptyList()
        val result = session.withSession { m ->
            m.call { c -> val (r, genres) = c.liveGenres(); parsed = genres; r }
        } ?: return CatalogResult.Failed(NOT_CONNECTED)
        if (!result.ok) return CatalogResult.Failed(NO_GENRES)
        val all = if (parsed.any { it.id == ALL_ID }) parsed else listOf(LiveGenre(ALL_ID, ALL_TITLE)) + parsed
        return CatalogResult.Ok(all)
    }

    /** One 1-based page of channels in [genreId]. */
    fun channels(genreId: String, page: Int): CatalogResult<LiveChannelPage> {
        var parsed: LiveChannelPage? = null
        val result = session.withSession { m ->
            m.call { c -> val (r, p) = c.liveChannels(genreId, page); parsed = p; r }
        } ?: return CatalogResult.Failed(NOT_CONNECTED)
        val p = parsed
        return if (result.ok && p != null) CatalogResult.Ok(p) else CatalogResult.Failed(NO_CHANNELS)
    }

    /** One 1-based page of channels whose name matches [query] (the portal's own name search). */
    fun search(query: String, page: Int): CatalogResult<LiveChannelPage> {
        var parsed: LiveChannelPage? = null
        val result = session.withSession { m ->
            m.call { c -> val (r, p) = c.channelsSearch(query, page); parsed = p; r }
        } ?: return CatalogResult.Failed(NOT_CONNECTED)
        val p = parsed
        return if (result.ok && p != null) CatalogResult.Ok(p) else CatalogResult.Failed(NO_CHANNELS)
    }

    companion object {
        const val ALL_ID = "*"
        const val ALL_TITLE = "All channels"
        const val NOT_CONNECTED = "Not connected to the portal."
        const val NO_GENRES = "The portal did not return the channel groups."
        const val NO_CHANNELS = "The portal did not return the channel list."
    }
}

/**
 * Channels of one genre, loaded a server page at a time (never the whole catalog: this portal has
 * about 5,000 live channels). The UI calls [loadNext] from a background thread when the user nears
 * the end of the list. State is Compose state, so the screen can read it directly.
 *
 * A failed page does not advance, so [loadNext] retries the same page. A page that adds nothing, or
 * a total reached, ends paging.
 */
class PagedChannels(private val fetchPage: (Int) -> CatalogResult<LiveChannelPage>) {
    var items: List<LiveChannel> by mutableStateOf(emptyList())
        private set
    var total: Int? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var loading: Boolean by mutableStateOf(false)
        private set
    var exhausted: Boolean by mutableStateOf(false)
        private set

    private var nextPage = 1

    companion object {
        /** A list that is already complete (the favorite channels): one page, nothing more to fetch. */
        fun fixed(channels: List<LiveChannel>) = PagedChannels { page ->
            if (page == 1) CatalogResult.Ok(LiveChannelPage(1, channels, channels.size, maxOf(channels.size, 1))) else CatalogResult.Failed("")
        }.also { it.loadNext() }
    }

    /** Loads one more page. Blocking. Does nothing if a load is running or paging is finished. */
    fun loadNext() {
        synchronized(this) {
            if (loading || exhausted) return
            loading = true
        }
        error = null
        try {
            when (val r = fetchPage(nextPage)) {
                is CatalogResult.Ok -> {
                    val page = r.value
                    // A catalog that shifts between pages can repeat a channel; keep the first copy.
                    val known = items.mapTo(HashSet()) { it.id }
                    items = items + page.items.filter { it.id !in known }
                    total = page.totalItems
                    nextPage++
                    exhausted = !page.hasMore(items.size)
                }
                is CatalogResult.Failed -> error = r.message
            }
        } finally {
            loading = false
        }
    }
}
