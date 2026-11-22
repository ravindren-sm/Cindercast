package io.github.ravindren_sm.openstream.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.provider.VodItem
import io.github.ravindren_sm.openstream.provider.VodLink
import io.github.ravindren_sm.openstream.provider.VodPage

/** The two VOD tabs. The portal keeps movies and shows in one list; see [VodCategory.kindOf]. */
enum class VodKind { MOVIES, SHOWS }

/** A catalog category. [title] is already cleaned of HTML entities. */
data class VodCategory(val id: String, val title: String) {
    companion object {
        private val SHOW_WORDS = Regex("(?i)\\b(series|shows?|seasons?|dramas?|serials?|soaps?|episodes?|sitcoms?|telenovelas?)\\b")

        /**
         * Which tab a category belongs to, from its title ("HULU | ENGLISH SERIES" is shows). The portal gives no
         * movie/show split of its own, so this is a heuristic; each title's own flag still decides whether it opens
         * as a movie or a show.
         */
        fun kindOf(title: String): VodKind = if (SHOW_WORDS.containsMatchIn(title)) VodKind.SHOWS else VodKind.MOVIES
    }
}

/**
 * VOD catalog calls for the Movies and Shows screens. Every call runs on the portal session's thread
 * and blocks: call off the main thread. Failures carry fixed text.
 */
class VodCatalog(private val session: PortalSession) {

    /** Categories of one tab, without the portal's mixed "All" entry, in the portal's order. */
    fun categories(kind: VodKind): CatalogResult<List<VodCategory>> {
        var parsed: List<Pair<String, String>> = emptyList()
        val result = session.withSession { m ->
            m.call { c -> val (r, cats) = c.vodCategories("vod"); parsed = cats; r }
        } ?: return CatalogResult.Failed(LiveCatalog.NOT_CONNECTED)
        if (!result.ok) return CatalogResult.Failed(NO_CATEGORIES)
        val list = parsed
            .filter { (id, _) -> id != "*" }
            .map { (id, title) -> VodCategory(id, PortalText.decodeEntities(title).trim().ifEmpty { "?" }) }
            .filter { VodCategory.kindOf(it.title) == kind }
        return CatalogResult.Ok(list)
    }

    /** One 1-based page of titles in [categoryId]. */
    fun titles(categoryId: String, page: Int): CatalogResult<VodPage> {
        var parsed: VodPage? = null
        val result = session.withSession { m ->
            m.call { c -> val (r, p) = c.vodTitles(categoryId, page); parsed = p; r }
        } ?: return CatalogResult.Failed(LiveCatalog.NOT_CONNECTED)
        val p = parsed
        return if (result.ok && p != null) CatalogResult.Ok(p) else CatalogResult.Failed(NO_TITLES)
    }

    // ---- shows: seasons, episodes, files (the portal's four levels) ----

    /** All seasons of [show]. A show has a handful, so every page is read (at most [MAX_CHILD_PAGES]). */
    fun seasons(show: VodItem): CatalogResult<List<VodItem>> = allChildren(show.id, "0", "0", NO_SEASONS)

    /** One 1-based page of the episodes of [season] (a season can have dozens: the screen pages them). */
    fun episodes(show: VodItem, season: VodItem, page: Int): CatalogResult<VodPage> = childPage(show.id, season.id, "0", page, NO_EPISODES)

    /** The files of one episode: one entry per language and quality. */
    fun files(show: VodItem, season: VodItem, episode: VodItem): CatalogResult<List<VodItem>> = allChildren(show.id, season.id, episode.id, NO_FILES)

    private fun childPage(movieId: String, seasonId: String, episodeId: String, page: Int, failure: String): CatalogResult<VodPage> {
        var parsed: VodPage? = null
        val result = session.withSession { m ->
            m.call { c -> val (r, p) = c.vodChildPage(movieId, seasonId, episodeId, page); parsed = p; r }
        } ?: return CatalogResult.Failed(LiveCatalog.NOT_CONNECTED)
        val p = parsed
        return if (result.ok && p != null) CatalogResult.Ok(p) else CatalogResult.Failed(failure)
    }

    private fun allChildren(movieId: String, seasonId: String, episodeId: String, failure: String): CatalogResult<List<VodItem>> {
        val all = mutableListOf<VodItem>()
        for (page in 1..MAX_CHILD_PAGES) {
            when (val r = childPage(movieId, seasonId, episodeId, page, failure)) {
                is CatalogResult.Failed -> return if (all.isEmpty()) r else CatalogResult.Ok(all)   // keep what was read
                is CatalogResult.Ok -> {
                    val known = all.mapTo(HashSet()) { it.id }
                    all += r.value.items.filter { it.id !in known }
                    if (!r.value.hasMore(all.size)) break
                }
            }
        }
        return CatalogResult.Ok(all)
    }

    companion object {
        const val NO_CATEGORIES = "The portal did not return the categories."
        const val NO_TITLES = "The portal did not return the titles."
        const val NO_SEASONS = "The portal did not return the seasons."
        const val NO_EPISODES = "The portal did not return the episodes."
        const val NO_FILES = "The portal did not return a playable file for this episode."
        private const val MAX_CHILD_PAGES = 10
    }
}

/** What the player should play: the file, how to title it, and the key its resume position is stored under. */
class PlayTarget(
    val file: VodItem, val title: String, val resumeKey: String,
    /** What Home needs to list and reopen it: MOVIE or EPISODE, the movie's or the show's id, the episode line, the poster. */
    val kind: String = "MOVIE", val titleId: String = file.id, val subtitle: String? = null, val posterUri: String? = file.posterUri,
    /** The name Home shows (an episode's show name; [title] is the longer line the player shows). */
    val historyTitle: String = title,
) {
    override fun toString() = "PlayTarget(title=$title)"
}

/** Resume keys: a movie by its id; an episode by show, season and episode, so each remembers its own position. */
object ResumeKeys {
    fun movie(id: String) = id
    fun episode(showId: String, seasonId: String, episodeId: String) = "$showId/$seasonId/$episodeId"
}

/** Resolves a movie (or an episode's file) to a stream address with the portal's VOD create_link. */
class VodPlayback(private val session: PortalSession) {

    /** Blocking: call off the main thread. [item] must carry a playable cmd. */
    fun resolve(item: VodItem): StreamLink {
        val cmd = item.playableCmd ?: return StreamLink.Failed(LivePlayback.NO_ADDRESS)
        var link: VodLink? = null
        val result = session.withSession { m ->
            m.call { c -> val (r, l) = c.vodLink(cmd); link = l; r }
        } ?: return StreamLink.Failed(LiveCatalog.NOT_CONNECTED)
        val found = link
        return if (result.ok && found != null) StreamLink.Ok(found.url, found.subtitles) else StreamLink.Failed(LivePlayback.NO_LINK)
    }
}

/**
 * Titles of one category, loaded a server page at a time (this portal has about 100,000 of them: never the
 * whole catalog). Same rules as [PagedChannels]: a failed page does not advance, a repeated title is kept once,
 * and a total reached or an empty page ends paging.
 */
class PagedTitles(private val fetchPage: (Int) -> CatalogResult<VodPage>) {
    var items: List<VodItem> by mutableStateOf(emptyList())
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

/** Remembers where each title was left, so playback can resume. Keys are portal title ids. */
interface ResumeStore {
    fun get(id: String): Long?
    fun put(id: String, positionMillis: Long)
    fun clear(id: String)
}

/** When a saved position is worth resuming from. */
object ResumePolicy {
    const val MIN_MILLIS = 15_000L          // before this the viewer has barely started: begin again
    const val NEAR_END_MILLIS = 45_000L     // within this of the end it counts as finished

    /** Where to start: the saved position if it is meaningful, else 0. [durationMillis] may be unknown (<= 0). */
    fun startPosition(savedMillis: Long?, durationMillis: Long): Long {
        val saved = savedMillis ?: return 0L
        if (saved < MIN_MILLIS) return 0L
        if (durationMillis > 0 && saved > durationMillis - NEAR_END_MILLIS) return 0L
        return saved
    }

    /** Whether a position reached while playing should be saved (not at the very start or end). */
    fun shouldSave(positionMillis: Long, durationMillis: Long): Boolean =
        positionMillis >= MIN_MILLIS && (durationMillis <= 0 || positionMillis <= durationMillis - NEAR_END_MILLIS)
}

/** Turns a poster address from the portal into one the image loader can fetch. */
object PosterUrl {
    /** [raw] as sent (absolute, protocol-relative, or a path on the portal host); [portalUrl] is the user's portal address. */
    fun resolve(raw: String?, portalUrl: String): String? {
        val r = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            r.startsWith("http://", ignoreCase = true) || r.startsWith("https://", ignoreCase = true) -> r
            r.startsWith("//") -> portalUrl.substringBefore("://", "http") + ":" + r
            r.startsWith("/") -> origin(portalUrl)?.let { it + r }
            else -> null
        }
    }

    private fun origin(portalUrl: String): String? {
        val scheme = portalUrl.substringBefore("://", "")
        val rest = portalUrl.substringAfter("://", "")
        val authority = rest.substringBefore('/').takeIf { it.isNotEmpty() } ?: return null
        return if (scheme.isEmpty()) null else "$scheme://$authority"
    }
}
