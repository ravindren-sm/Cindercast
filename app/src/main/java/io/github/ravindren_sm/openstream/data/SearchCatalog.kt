package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.provider.VodItem

/** What a search found, grouped for the screen. [channelsFailed] means the channel list could not be read (movies and shows still count). */
class SearchResults(
    val movies: List<VodItem>,
    val shows: List<VodItem>,
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

/**
 * Search over the portal: titles through the portal's own `search` parameter (a few pages), channels by filtering the
 * list of all channels, which is read once per portal and kept for the session. Blocking: call off the main thread.
 */
class SearchCatalog(private val session: PortalSession, private val maxPages: Int = 3) {
    @Volatile private var channelCache: List<LiveChannel>? = null

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
        val channels = allChannels()
        return CatalogResult.Ok(
            SearchResults(
                movies = ranked.filter { !it.isSeries }, shows = ranked.filter { it.isSeries },
                channels = channels?.let { SearchRanking.matchChannels(it, q) }.orEmpty(), channelsFailed = channels == null,
            ),
        )
    }

    private fun rank(items: List<VodItem>, q: String) = SearchRanking.rank(items, q)

    /** All channels, from the cache or one request; null if they could not be read (the next search tries again). */
    private fun allChannels(): List<LiveChannel>? {
        channelCache?.let { return it }
        var parsed: List<LiveChannel>? = null
        val result = session.withSession { m -> m.call { c -> val (r, l) = c.allChannels(); parsed = l; r } } ?: return null
        return parsed?.takeIf { result.ok }?.also { channelCache = it }
    }

    companion object { const val NO_RESULTS = "The portal did not answer the search." }
}
