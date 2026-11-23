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
    /** A line to show with the results, for example that only some matching categories were searched. */
    val note: String? = null,
    /** Nothing passed the filters, so these are the matches without them. */
    val relaxed: Boolean = false,
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
    fun rank(items: List<VodItem>, query: String, personal: Personalization? = null): List<VodItem> {
        val q = normalize(query)
        fun group(item: VodItem): Int {
            val name = normalize(item.name)
            return when { name.startsWith(q) -> 0; q in name -> 1; else -> 2 }
        }
        // Relevance first (starts with, contains, other); inside each group what the viewer watches comes first; then the portal's order.
        val scores = if (personal == null || personal.isEmpty) null else items.associateWith { personal.score(it) }
        return items.withIndex()
            .sortedWith(compareBy({ group(it.value) }, { -(scores?.get(it.value) ?: 0.0) }, { it.index }))
            .map { it.value }
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

    /**
     * Searches titles and channels (channels only when no filter is on, since channels have no category, genre or rating).
     *
     * Language and category filters: the portal is first asked once for all categories and the answer is narrowed here by
     * each title's `category_id`, so every matching category counts (asking one category per request could only cover a
     * few). Portals that do not send `category_id` are asked one category at a time, up to six. Genre and rating filters
     * are applied here to what the portal returns.
     *
     * When filters leave nothing but the text alone matches something, those unfiltered matches are returned with
     * [SearchResults.relaxed] set, so the viewer sees that the title exists and that the filters hid it.
     */
    fun search(query: String, filters: SearchFilters = SearchFilters(), categories: List<VodCategory> = emptyList(), policy: ContentPolicy = ContentPolicy(), personal: Personalization? = null, enrich: (List<VodItem>) -> List<VodItem> = { it }): CatalogResult<SearchResults> {
        val first = searchOnce(query, filters, categories, policy, personal, enrich)
        if (!filters.isActive) return first
        val r = (first as? CatalogResult.Ok)?.value ?: return first
        if (!(r.movies.isEmpty() && r.shows.isEmpty())) return first
        val loose = (searchOnce(query, SearchFilters(), categories, policy, personal, enrich) as? CatalogResult.Ok)?.value ?: return first
        if (loose.isEmpty) return first
        SearchHooks.event("relaxed movies=${loose.movies.size} shows=${loose.shows.size}")
        return CatalogResult.Ok(SearchResults(loose.movies, loose.shows, loose.channels, loose.channelsFailed, note = loose.note, relaxed = true))
    }

    private fun searchOnce(query: String, filters: SearchFilters, categories: List<VodCategory>, policy: ContentPolicy, personal: Personalization?, enrich: (List<VodItem>) -> List<VodItem>): CatalogResult<SearchResults> {
        val q = query.trim()
        val plan = FilterRules.plan(categories, filters)
        if (plan.none) return CatalogResult.Ok(SearchResults(emptyList(), emptyList(), emptyList(), note = "No category matches the language and category choices."))
        val titles = mutableListOf<VodItem>()
        val hinted = HashSet<String>()                    // ids of titles from a category whose name mentions English subtitles
        val subsCategories = categories.filter { FilterRules.mentionsEnglishSubtitles(it.title) }.mapTo(HashSet()) { it.id }
        var note: String? = null
        var truncated = false                             // the portal had more pages than were read

        /** Reads up to [pages] pages for [category] into [titles]: null if not connected, false if the first request failed. */
        fun read(category: String, pages: Int, onPage: (VodItem) -> Unit = {}): Boolean? {
            for (page in 1..pages) {
                var parsed: io.github.ravindren_sm.openstream.provider.VodPage? = null
                val result = session.withSession { m -> m.call { c -> val (r, p) = c.vodSearch(q, page, category); parsed = p; r } } ?: return null
                val p = parsed
                if (!result.ok || p == null) return page > 1                // keep what was read after page 1
                val known = titles.mapTo(HashSet()) { it.id }
                titles += p.items.filter { it.id !in known }
                p.items.forEach(onPage)
                if (!p.hasMore(p.items.size * page)) return true
                if (page == pages) truncated = true
            }
            return true
        }

        if (plan.ids == null) {
            when (read("*", maxPages)) { null -> return CatalogResult.Failed(LiveCatalog.NOT_CONNECTED); false -> return CatalogResult.Failed(NO_RESULTS); true -> Unit }
        } else {
            // One global search, narrowed by category here.
            when (read("*", GLOBAL_PAGES)) { null -> return CatalogResult.Failed(LiveCatalog.NOT_CONNECTED); false -> return CatalogResult.Failed(NO_RESULTS); true -> Unit }
            val carriesCategory = titles.isNotEmpty() && titles.all { it.categoryId != null }
            if (carriesCategory || titles.isEmpty()) {
                titles.retainAll { it.categoryId in plan.allIds }
                titles.forEach { if (it.categoryId in subsCategories) hinted += it.id }
                if (truncated) note = "The portal found more matches than were read; type more of the name to narrow it."
                SearchHooks.event("filtered globally categories=${plan.allIds.size}")
            } else {
                // This portal does not say which category a title is in: ask the matching categories one at a time.
                titles.clear()
                for (category in plan.ids) {
                    val mentionsSubs = category in subsCategories
                    if (read(category, 2) { if (mentionsSubs) hinted += it.id } == null) return CatalogResult.Failed(LiveCatalog.NOT_CONNECTED)
                }
                if (plan.capped) note = "Searched the first ${plan.ids.size} of ${plan.matching} matching categories."
            }
        }
        // The profile's rating floor applies unless the viewer chose a rating filter for this search.
        val enriched = enrich(titles)
        val kept = enriched.filter { FilterRules.passes(it, filters, categoryHint = it.id in hinted) && policy.passesAudience(it) && (filters.minRating != null || policy.passes(it)) }
        val ranked = SearchRanking.rank(kept, q, personal)
        val (channels, failed) = if (filters.isActive || policy.isKids) emptyList<LiveChannel>() to false else findChannels(q)
        val movies = TitleMerge.group(ranked.filter { !it.isSeries })
        val shows = TitleMerge.group(ranked.filter { it.isSeries })
        SearchHooks.event("titles=${titles.size} kept=${kept.size} movies=${movies.size} shows=${shows.size} channels=${channels.size} channelsFailed=$failed")
        return CatalogResult.Ok(SearchResults(movies = movies, shows = shows, channels = channels, channelsFailed = failed, note = note))
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

    companion object {
        const val NO_RESULTS = "The portal did not answer the search."
        /** Pages of the global answer read when a language or category filter is on (the portal sends about 14 per page). */
        const val GLOBAL_PAGES = 6
    }
}
