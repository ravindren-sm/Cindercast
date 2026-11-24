package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.provider.LiveChannel

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
    private class Entry(val programmes: List<EpgProgramme>?, val at: Long)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Entry>()

    /** Cached programmes if fresh, else asked for. Null when the portal could not be asked (not connected or an error): worth trying later. */
    fun load(channel: LiveChannel): List<EpgProgramme>? {
        cache[channel.id]?.let { if (nowMillis() - it.at < ttlMillis) return it.programmes }
        var parsed: List<EpgProgramme>? = null
        val result = session.withSession { m -> m.call { c -> val (r, p) = c.epgProgrammes(channel.id, PROGRAMMES); parsed = p; r } } ?: return null
        if (!result.ok) return null
        val list = parsed.orEmpty()
        cache[channel.id] = Entry(list, nowMillis())
        return list
    }
}

/**
 * Programmes asked for per channel: enough to reach the evening (the NFL search looks for tonight's game), at the same one
 * request per channel as a shorter list.
 */
private const val PROGRAMMES = 10
