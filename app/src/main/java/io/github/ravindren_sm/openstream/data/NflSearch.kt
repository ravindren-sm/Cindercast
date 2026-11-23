package io.github.ravindren_sm.openstream.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.LiveGenre
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Finds NFL games on live channels. The portal has no bulk guide (get_epg_info answers empty), so each channel's short guide
 * is asked for on its own (about a quarter of a second, through [GuideLoader], which keeps answers for ten minutes). To show
 * games quickly the search runs in stages, most likely channels first:
 *
 * 1. channels whose name says NFL (the portal's own name search) and every channel in a group named for the NFL;
 * 2. the network channels (FOX, CBS, NBC, ABC, ESPN, Prime, Netflix, TSN...) in US and Canadian groups, and the sports groups
 *    named for those carriers (ESPN+, Paramount+, Peacock, DAZN);
 * 3. on request ([extended]), every other sports group not named for another sport.
 *
 * Copies of one channel that differ only by quality ("4K", "HD", "FHD", "RAW") are checked once. Results appear as they are
 * found. Compose state; [run] blocks on portal calls, so call it from a background coroutine.
 */
class NflSearch(private val catalog: LiveCatalog, private val loader: GuideLoader, private val nowSec: () -> Long = { System.currentTimeMillis() / 1000 }) {
    var listings: List<SportsLogic.NflListing> by mutableStateOf(emptyList())
        private set
    var checked: Int by mutableIntStateOf(0)
        private set
    var queued: Int by mutableIntStateOf(0)
        private set
    /** What is being read now, for the status line ("SPORTS | ESPN+"). */
    var current: String by mutableStateOf("")
        private set
    var done: Boolean by mutableStateOf(false)
        private set
    /** True once the extended stage (every other sports group) has run or is running. */
    var extended: Boolean by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set

    private val seenKeys = HashSet<String>()
    private var finishedAtMillis = 0L

    /** True when a finished search is older than the guide cache, so its "on now" lines may be stale. */
    fun stale(nowMillis: Long = System.currentTimeMillis()) = done && nowMillis - finishedAtMillis > 10 * 60_000

    /** Starts over (for example when the results are stale). */
    fun reset() { listings = emptyList(); checked = 0; queued = 0; current = ""; done = false; extended = false; error = null; seenKeys.clear(); groupChannels.clear() }

    /** Runs stages 1 and 2, or stage 3 when [more] is set. Cancellable between channels. */
    suspend fun run(genres: List<LiveGenre>, more: Boolean = false) {
        done = false; error = null
        if (more) {
            extended = true
            for (g in otherSportsGroups(genres)) scanGroup(g, networksOnly = false)
        } else {
            current = "channels named NFL"
            checkAll(searchByName("NFL"))
            for (g in genres.filter { SportsLogic.isNflChannel(it.title) }) scanGroup(g, networksOnly = false)
            for (g in genres.filter { SportsLogic.nflScan(it.title) == SportsLogic.NflScan.ALL_CHANNELS && isCarrierGroup(it.title) }) scanGroup(g, networksOnly = false)
            for (g in genres.filter { SportsLogic.nflScan(it.title) == SportsLogic.NflScan.NETWORKS_ONLY }) scanGroup(g, networksOnly = true)
            // The network channels (ESPN, FOX Sports, CBS Sports, NFL Network...) of the general sports groups, such as one
            // plainly called "SPORTS"; the rest of those groups waits for stage 3.
            for (g in otherSportsGroups(genres)) scanGroup(g, networksOnly = true)
        }
        current = ""; done = true; finishedAtMillis = System.currentTimeMillis()
    }

    private fun otherSportsGroups(genres: List<LiveGenre>) =
        genres.filter { SportsLogic.nflScan(it.title) == SportsLogic.NflScan.ALL_CHANNELS && !isCarrierGroup(it.title) && !SportsLogic.isNflChannel(it.title) }

    private fun isCarrierGroup(title: String) = SportsLogic.isNetworkChannel(title) || title.uppercase().let { t -> listOf("ESPN", "PARAMOUNT", "PEACOCK", "DAZN", "PRIME", "NETFLIX").any { it in t } }

    private fun searchByName(query: String): List<LiveChannel> {
        val out = ArrayList<LiveChannel>()
        var page = 1
        while (page <= 5) {
            val r = catalog.search(query, page)
            if (r !is CatalogResult.Ok) break
            out += r.value.items
            if (!r.value.hasMore(out.size)) break
            page++
        }
        return out.filter { SportsLogic.isNflChannel(it.name) }
    }

    /** Channel lists already read, so stage 3 does not page through a group stage 2 read. */
    private val groupChannels = HashMap<String, List<LiveChannel>>()

    private suspend fun scanGroup(g: LiveGenre, networksOnly: Boolean) {
        current = g.title
        groupChannels[g.id]?.let { known -> checkAll(if (networksOnly) known.filter { SportsLogic.isNetworkChannel(it.name) } else known); return }
        val channels = ArrayList<LiveChannel>()
        var page = 1
        while (page <= 40) {
            coroutineContext.ensureActive()
            when (val r = catalog.channels(g.id, page)) {
                is CatalogResult.Ok -> {
                    channels += r.value.items
                    if (!r.value.hasMore(channels.size) || r.value.items.isEmpty()) break
                }
                is CatalogResult.Failed -> { error = r.message; break }
            }
            page++
        }
        groupChannels[g.id] = channels
        checkAll(if (networksOnly) channels.filter { SportsLogic.isNetworkChannel(it.name) } else channels)
    }

    private suspend fun checkAll(channels: List<LiveChannel>) {
        val fresh = channels.filter { seenKeys.add(SportsLogic.channelKey(it.name)) }
        queued += fresh.size
        for (ch in fresh) {
            coroutineContext.ensureActive()
            val programmes = loader.load(ch)
            checked++
            if (programmes != null) {
                val found = SportsLogic.listings(ch, programmes, nowSec())
                if (found.isNotEmpty()) listings = SportsLogic.order(listings + found)
            }
        }
    }
}
