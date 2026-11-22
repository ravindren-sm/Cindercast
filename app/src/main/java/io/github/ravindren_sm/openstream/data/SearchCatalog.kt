package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.provider.VodItem

/** What a search found, grouped for the screen. [channelsFailed] means the channel list could not be read (movies and shows still count). */
class SearchResults(
    val movies: List<TitleGroup>,
    val shows: List<TitleGroup>,
    val channels: List<LiveChannel>,
    val channelsFailed: Boolean = false,
) {
    val isEmpty get() = movies.isEmpty() && shows.isEmpty() && channels.isEmpty()
}

/** Ordering and matching rules for search results. Plain Kotlin, tested without Android. */
object SearchRanking {
    const val MIN_QUERY = 2
    const val MAX_CHANNELS = 30

    /** The text as the viewer would compare it: HTML entities decoded, lower case, spaces collapsed. */
    fun normalize(text: String): String = PortalText.decodeEntities(text).lowercase().trim().replace(Regex("\\s+"), " ")

    /** Titles that start with the query first, then those containing it, then the rest (the portal matched them on other fields). Order within a group is kept. */
    fun rank(items: List<VodItem>, query: String): List<VodItem> {
        val q = normalize(query)
        fun group(item: VodItem): Int {
            val name = normalize(item.name)
            return when { name.startsWith(q) -> 0; q in name -> 1; else -> 2 }
        }
        return items.withIndex().sortedWith(compareBy({ group(it.value) }, { it.index })).map { it.value }
    }

    /** Channels the portal found for [query]: its order is kept, but names starting with the text come first and the list is capped. */
    fun matchChannelsFromServer(found: List<LiveChannel>, query: String): List<LiveChannel> {
        val q = normalize(query)
        return found.distinctBy { it.id }.sortedBy { if (normalize(it.name).startsWith(q)) 0 else 1 }.take(MAX_CHANNELS)
    }

    /** Channels whose name contains the query, or whose number equals it, name matches starting-with first; at most [MAX_CHANNELS]. */
    fun matchChannels(all: List<LiveChannel>, query: String): List<LiveChannel> {
        val q = normalize(query)
        if (q.length < MIN_QUERY && q.toIntOrNull() == null) return emptyList()
        val byNumber = q.toIntOrNull()?.let { n -> all.filter { it.number.trim().toIntOrNull() == n } }.orEmpty()
        val byName = all.filter { normalize(it.name).contains(q) }
            .sortedBy { if (normalize(it.name).startsWith(q)) 0 else 1 }      // stable: keeps the portal's order inside each group
        return (byNumber + byName).distinctBy { it.id }.take(MAX_CHANNELS)
    }
}

/** Debug-only observer of search steps: counts and which path was used, never the typed text. Set by the debug Application. */
object SearchHooks {
    @Volatile var onEvent: ((String) -> Unit)? = null
    internal fun event(text: String) { onEvent?.invoke(text) }
}

/**
 * Search over the portal: titles through the portal's own `search` parameter (a few pages), channels by filtering the
 * list of all channels, which is read once per portal and kept for the session. Blocking: call off the main thread.
 */
class SearchCatalog(private val session: PortalSession, private val maxPages: Int = 3) {
    @Volatile private var channelCache: List<LiveChannel>? = null
    /** Set once the portal has shown it ignores `search` on the channel list, so later searches skip that request. */
    @Volatile private var serverChannelSearchIgnored = false

    fun search(query: String): CatalogResult<SearchResults> {
        val q = query.trim()
        val titles = mutableListOf<VodItem>()
        for (page in 1..maxPages) {
            var parsed: io.github.ravindren_sm.openstream.provider.VodPage? = null
            val result = session.withSession { m -> m.call { c -> val (r, p) = c.vodSearch(q, page); parsed = p; r } }
                ?: return CatalogResult.Failed(LiveCatalog.NOT_CONNECTED)
            val p = parsed
            if (!result.ok || p == null) {
                if (page == 1) return CatalogResult.Failed(NO_RESULTS)
                break                                                  // keep what was read
            }
            val known = titles.mapTo(HashSet()) { it.id }
            titles += p.items.filter { it.id !in known }
            if (!p.hasMore(titles.size)) break
        }
        val ranked = rank(titles, q)
        val (channels, failed) = findChannels(q)
        val movies = TitleMerge.group(ranked.filter { !it.isSeries })
        val shows = TitleMerge.group(ranked.filter { it.isSeries })
        SearchHooks.event("titles=${titles.size} movies=${movies.size} shows=${shows.size} merged=${titles.size - movies.size - shows.size} channels=${channels.size} channelsFailed=$failed")
        return CatalogResult.Ok(SearchResults(movies = movies, shows = shows, channels = channels, channelsFailed = failed))
    }

    /**
     * Channels for [q]: first the portal's own `search` on the channel list (small answers). It is trusted only if it
     * answers with nothing, or with at least one channel whose name contains the text; a list that ignores the
     * parameter looks like the first page of everything. Otherwise the full channel list is filtered on the device.
     * Returns the channels and whether searching them failed altogether.
     */
    private fun findChannels(q: String): Pair<List<LiveChannel>, Boolean> {
        var page1: io.github.ravindren_sm.openstream.provider.LiveChannelPage? = null
        val first = if (serverChannelSearchIgnored) null else session.withSession { m -> m.call { c -> val (r, p) = c.channelsSearch(q, 1); page1 = p; r } }
        val p1 = page1
        if (first != null && first.ok && p1 != null) {
            val needle = SearchRanking.normalize(q)
            val honoured = p1.items.isEmpty() || p1.items.any { SearchRanking.normalize(it.name).contains(needle) }
            if (honoured) {
                val found = p1.items.toMutableList()
                if (p1.hasMore(found.size)) {
                    var p2: io.github.ravindren_sm.openstream.provider.LiveChannelPage? = null
                    val second = session.withSession { m -> m.call { c -> val (r, p) = c.channelsSearch(q, 2); p2 = p; r } }
                    if (second != null && second.ok) p2?.let { page -> found += page.items.filter { n -> found.none { it.id == n.id } } }
                }
                SearchHooks.event("channels path=server total=${p1.totalItems}")
                return SearchRanking.matchChannelsFromServer(found, q) to false
            }
            serverChannelSearchIgnored = true
            SearchHooks.event("channels path=server-ignored total=${p1.totalItems}")
        } else if (!serverChannelSearchIgnored) SearchHooks.event("channels path=server-failed")
        val all = allChannels()
        SearchHooks.event("channels path=client all=${all?.size ?: -1}")
        return if (all == null) emptyList<LiveChannel>() to true else SearchRanking.matchChannels(all, q) to false
    }

    private fun rank(items: List<VodItem>, q: String) = SearchRanking.rank(items, q)

    /** The channel names read so far (empty until the channel list has been downloaded). */
    fun cachedChannelNames(): List<String> = channelCache?.map { it.name }.orEmpty()

    /** Reads the channel list ahead of the first search so channel names can be suggested. Blocking. */
    fun warmChannels() { allChannels() }

    /** All channels, from the cache or one request; null if they could not be read (the next search tries again). */
    private fun allChannels(): List<LiveChannel>? {
        channelCache?.let { return it }
        var parsed: List<LiveChannel>? = null
        val result = session.withSession { m -> m.call { c -> val (r, l) = c.allChannels(); parsed = l; r } } ?: return null
        SearchHooks.event("all channels: " + result.summary.joinToString("; "))   // a count or a short reason, never content
        return parsed?.takeIf { result.ok }?.also { channelCache = it }
    }

    companion object { const val NO_RESULTS = "The portal did not answer the search." }
}
