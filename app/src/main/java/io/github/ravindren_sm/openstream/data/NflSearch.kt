package io.github.ravindren_sm.openstream.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.LiveGenre
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.coroutines.coroutineContext

/**
 * Finds NFL games on live channels. The portal has no bulk guide (get_epg_info answers empty), so each channel's short guide
 * is asked for on its own (about a third of a second, through [GuideLoader]). A full pass over the likely channels takes a few
 * minutes, so the app runs it in the background (see the shell: never while something is playing) and keeps the answer:
 *
 * - A pass ([refresh]) reads, most likely channels first: channels whose name says NFL (the portal's name search) and every
 *   channel in a group named for the NFL; the sports groups named for the US carriers (ESPN+, Paramount+, Peacock, DAZN);
 *   the network channels (FOX, CBS, NBC, ABC, ESPN, TSN...) in the US, local and English groups and in the other sports groups.
 * - A pass can be cancelled (playback started) and resumed: channels already read in this pass are not read again.
 * - The listings shown stay those of the last finished pass until the next one finishes, so the list does not empty and refill.
 *   On the very first pass (nothing saved yet) results appear as they are found.
 * - [extended] adds every other sports group on request ("Check more channels").
 * - Listings are saved ([encode] / [decode]) so a restart shows them at once; [current] drops ended programmes and marks the
 *   ones now on as live.
 *
 * Copies of one channel that differ only by quality ("4K", "HD", "RAW") are read once. Compose state; [refresh] blocks on portal
 * calls, so call it from a background coroutine.
 */
class NflSearch(
    private val catalog: LiveCatalog,
    private val loader: GuideLoader,
    private val nowSec: () -> Long = { System.currentTimeMillis() / 1000 },
    /** Called with the encoded listings after each finished pass (the shell writes them to the app's private storage). */
    private val save: (String) -> Unit = {},
) {
    /** The listings to show: the last finished pass (or, on a first pass, what has been found so far). */
    var listings: List<SportsLogic.NflListing> by mutableStateOf(emptyList())
        private set
    var checked: Int by mutableIntStateOf(0)
        private set
    var queued: Int by mutableIntStateOf(0)
        private set
    /** What is being read now, for the status line ("SPORTS | ESPN+"); empty when no pass is running. */
    var current: String by mutableStateOf("")
        private set
    /** True while a pass is under way (it may be paused, for example during playback). */
    var running: Boolean by mutableStateOf(false)
        private set
    /** When the last pass finished (epoch milliseconds), or 0 if none has. */
    var finishedAt: Long by mutableLongStateOf(0L)
        private set
    /** True once the extended stage (every other sports group) has run or is running. */
    var extended: Boolean by mutableStateOf(false)
        private set

    /** One pass at a time: the background refresh and "Check more channels" never run together. */
    private val lock = Mutex()
    private val seenKeys = HashSet<String>()
    private val groupChannels = HashMap<String, List<LiveChannel>>()
    private var found: List<SportsLogic.NflListing> = emptyList()

    /** True when nothing has been shown yet: results then appear as they are found. */
    private val progressive get() = listings.isEmpty() && finishedAt == 0L

    /** Time for a new pass: none has finished, or the last one is older than [everyMillis]. */
    fun due(everyMillis: Long, nowMillis: Long = System.currentTimeMillis()) = finishedAt == 0L || nowMillis - finishedAt >= everyMillis

    /** The listings at [now]: ended programmes dropped, the ones on now marked live. */
    fun current(now: Long = nowSec()): List<SportsLogic.NflListing> = SportsLogic.order(
        listings.filter { (it.programme?.stopSec ?: Long.MAX_VALUE) > now }
            .map { l -> SportsLogic.NflListing(l.channel, l.programme, live = (l.programme?.startSec ?: 0L) <= now, game = l.game) },
    )

    /** Shows listings saved by an earlier run of the app (see [decode]); ignored once a pass has produced results. */
    fun restore(saved: List<SportsLogic.NflListing>, savedAt: Long) {
        if (listings.isEmpty() && finishedAt == 0L && saved.isNotEmpty()) { listings = saved; finishedAt = savedAt }
    }

    /** Runs (or resumes) a pass over the likely channels. Cancellable between channels; a cancelled pass resumes where it stopped. */
    suspend fun refresh(genres: List<LiveGenre>) = lock.withLock {
        // Two callers can queue on the lock (the background loop, and Sports opened before any result): the second one finds
        // a pass that has just finished and does nothing.
        if (!running && finishedAt != 0L && System.currentTimeMillis() - finishedAt < 60_000) return@withLock
        if (!running) { seenKeys.clear(); groupChannels.clear(); found = emptyList(); checked = 0; queued = 0; extended = false }
        running = true
        current = "channels named NFL"
        checkAll(searchByName("NFL"))
        for (g in genres.filter { SportsLogic.isNflChannel(it.title) }) scanGroup(g, networksOnly = false)
        for (g in genres.filter { SportsLogic.nflScan(it.title) == SportsLogic.NflScan.ALL_CHANNELS && isCarrierGroup(it.title) }) scanGroup(g, networksOnly = false)
        for (g in genres.filter { SportsLogic.nflScan(it.title) == SportsLogic.NflScan.NETWORKS_ONLY }) scanGroup(g, networksOnly = true)
        // The network channels (ESPN, FOX Sports, CBS Sports, NFL Network...) of the general sports groups, such as one plainly
        // called "SPORTS"; the rest of those groups waits for [extend].
        for (g in otherSportsGroups(genres)) scanGroup(g, networksOnly = true)
        finish()
    }

    /** Reads every other sports group too (slower; on request). Adds to the current listings. */
    suspend fun extend(genres: List<LiveGenre>) = lock.withLock {
        extended = true; running = true
        found = listings
        for (g in otherSportsGroups(genres)) scanGroup(g, networksOnly = false)
        finish()
    }

    private fun finish() {
        listings = SportsLogic.order(found)
        current = ""; running = false; finishedAt = System.currentTimeMillis()
        save(encode(listings, finishedAt))
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

    private suspend fun scanGroup(g: LiveGenre, networksOnly: Boolean) {
        current = g.title
        val channels = groupChannels[g.id] ?: run {
            val list = ArrayList<LiveChannel>()
            var page = 1
            while (page <= 40) {
                coroutineContext.ensureActive()
                when (val r = catalog.channels(g.id, page)) {
                    is CatalogResult.Ok -> { list += r.value.items; if (!r.value.hasMore(list.size) || r.value.items.isEmpty()) break }
                    is CatalogResult.Failed -> break
                }
                page++
            }
            list.also { groupChannels[g.id] = it }
        }
        checkAll(if (networksOnly) channels.filter { SportsLogic.isNetworkChannel(it.name) } else channels)
    }

    private suspend fun checkAll(channels: List<LiveChannel>) {
        // Keys are claimed only once a channel has been read, so a pass cancelled mid-group reads the rest when it resumes.
        val fresh = channels.filter { SportsLogic.channelKey(it.name) !in seenKeys }.distinctBy { SportsLogic.channelKey(it.name) }
        queued += fresh.size
        for (ch in fresh) {
            coroutineContext.ensureActive()
            val programmes = loader.load(ch)
            seenKeys += SportsLogic.channelKey(ch.name)
            checked++
            if (programmes != null) {
                val hits = SportsLogic.listings(ch, programmes, nowSec())
                if (hits.isNotEmpty()) {
                    found = SportsLogic.order(found + hits)
                    if (progressive || extended) listings = found
                }
            }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * The listings as JSON for the app's private storage: channel id, number, name and play command (needed to play it; the
         * same command the saved title index keeps), and the programme's title, times and whether it is a game.
         */
        fun encode(listings: List<SportsLogic.NflListing>, savedAt: Long): String = buildJsonObject {
            put("savedAt", savedAt)
            put("listings", buildJsonArray {
                listings.forEach { l ->
                    add(buildJsonObject {
                        put("id", l.channel.id); put("number", l.channel.number); put("name", l.channel.name); l.channel.cmd?.let { put("cmd", it) }
                        put("game", l.game)
                        l.programme?.let { p -> put("title", p.name); put("start", p.startSec); put("stop", p.stopSec) }
                    })
                }
            })
        }.toString()

        /** Reads [encode]'s output; an unreadable file gives nothing. */
        fun decode(text: String): Pair<List<SportsLogic.NflListing>, Long> = try {
            val root = json.parseToJsonElement(text) as JsonObject
            val savedAt = (root["savedAt"] as? JsonPrimitive)?.longOrNull ?: 0L
            val list = (root["listings"] as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
                fun n(k: String) = (o[k] as? JsonPrimitive)?.longOrNull
                val id = s("id") ?: return@mapNotNull null
                val ch = LiveChannel(id, s("number").orEmpty(), s("name").orEmpty(), s("cmd"))
                val title = s("title"); val start = n("start"); val stop = n("stop")
                val p = if (title != null && start != null && stop != null) EpgProgramme(title, null, null, start, stop) else null
                SportsLogic.NflListing(ch, p, live = false, game = (o["game"] as? JsonPrimitive)?.booleanOrNull == true)
            }
            list to savedAt
        } catch (_: Exception) { emptyList<SportsLogic.NflListing>() to 0L }
    }
}
