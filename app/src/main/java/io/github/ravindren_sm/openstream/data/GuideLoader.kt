package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.provider.LiveChannel
import kotlinx.coroutines.sync.withLock

/** What is on a channel now and next, from a list of its programmes. Plain Kotlin, tested without Android. */
object GuideLogic {
    class NowNext(val now: EpgProgramme?, val next: EpgProgramme?)

    fun nowAndNext(programmes: List<EpgProgramme>, nowSec: Long): NowNext {
        val ordered = programmes.sortedBy { it.startSec }
        val now = ordered.lastOrNull { it.startSec <= nowSec && nowSec < it.stopSec }
        val next = ordered.firstOrNull { it.startSec >= (now?.stopSec ?: nowSec) && it.startSec > nowSec - 1 && it !== now }
        return NowNext(now, next)
    }

    /** 0 to 1: how far through the programme [nowSec] is. */
    fun progress(p: EpgProgramme, nowSec: Long): Float = ((nowSec - p.startSec).toFloat() / p.durationSec.coerceAtLeast(1)).coerceIn(0f, 1f)
}

/**
 * Reads a channel's short guide from the portal (one request each, about a quarter of a second) and keeps it for
 * [ttlMillis]. Blocking: call off the main thread. A channel with no guide is remembered too, so it is not asked again.
 */
class GuideLoader(private val session: PortalSession, private val ttlMillis: Long = 10 * 60_000, private val nowMillis: () -> Long = System::currentTimeMillis) {
    private class Entry(val channel: LiveChannel, val programmes: List<EpgProgramme>?, val at: Long)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Entry>()

    /** Keeps the screens' background loads to one request at a time (see [loadQueued]). */
    private val gate = kotlinx.coroutines.sync.Mutex()

    /** A channel and the programmes the loader holds for it (empty when it has no guide). */
    class CachedGuide(val channel: LiveChannel, val programmes: List<EpgProgramme>)

    /**
     * A read-only copy of every guide the loader holds (fresh or not; stale ones are harmless for a search, which ignores
     * programmes that have ended). Channels remembered as having no guide are left out. Safe to call from any thread.
     */
    fun cachedGuides(): List<CachedGuide> = cache.values.mapNotNull { e -> e.programmes?.takeIf { it.isNotEmpty() }?.let { CachedGuide(e.channel, it) } }

    /** The fresh cached programmes of a channel without asking the portal: null when not cached (or stale); empty when it has no guide. Safe on the main thread. */
    fun peek(channelId: String): List<EpgProgramme>? = cache[channelId]?.takeIf { nowMillis() - it.at < ttlMillis }?.programmes

    /**
     * Like [load] but from a coroutine, and one request at a time across every caller (the grid's rows, the programme search):
     * a caller waiting its turn that is cancelled (a row scrolled away) never sends its request. Cached answers return at once.
     */
    suspend fun loadQueued(channel: LiveChannel): List<EpgProgramme>? {
        peek(channel.id)?.let { return it }
        return gate.withLock { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { load(channel) } }
    }

    /** Cached programmes if fresh, else asked for. Null when the portal could not be asked (not connected or an error): worth trying later. */
    fun load(channel: LiveChannel): List<EpgProgramme>? {
        cache[channel.id]?.let { if (nowMillis() - it.at < ttlMillis) return it.programmes }
        var parsed: List<EpgProgramme>? = null
        val result = session.withSession { m -> m.call { c -> val (r, p) = c.epgProgrammes(channel.id, PROGRAMMES); parsed = p; r } } ?: return null
        if (!result.ok) return null
        val list = parsed.orEmpty()
        cache[channel.id] = Entry(channel, list, nowMillis())
        return list
    }
}

/**
 * Programmes asked for per channel: enough to reach the evening (the NFL search looks for tonight's game), at the same one
 * request per channel as a shorter list.
 */
private const val PROGRAMMES = 10
