package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.PortalText
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Geometry and navigation helpers of the TV Guide time grid. Plain Kotlin, tested without Android: the screen only draws
 * what these functions return. Lengths are in abstract units (the screen passes dp), times are seconds since the epoch.
 */
object GuideGrid {
    /** One header mark: the grid is divided in half-hour slots. */
    const val SLOT_SEC = 1800L

    /** How much time one screen of the grid shows (two and a half hours). */
    const val WINDOW_SEC = 5 * SLOT_SEC

    /** The start of the half hour containing [sec]. */
    fun floorToSlot(sec: Long): Long = sec - Math.floorMod(sec, SLOT_SEC)

    /**
     * One programme drawn in a row: [x] from the left edge of the time area, [width] wide. [clippedStart] and [clippedEnd] say
     * the programme continues beyond the window on that side.
     */
    class Cell(val programme: EpgProgramme, val x: Float, val width: Float, val clippedStart: Boolean, val clippedEnd: Boolean)

    /**
     * The cells of one channel for the window [windowStartSec] until [windowStartSec] + [windowSec], at [unitsPerSec] units per second.
     * Programmes outside the window are left out; those crossing an edge are cut to it. Each cell is made [gap] narrower than its
     * time span so neighbours do not touch, but never narrower than [minWidth] (so a very short programme stays visible and
     * focusable), and never reaches past the right edge of the window. Overlapping or unsorted input is tolerated: cells come
     * back in start order and a cell is cut where the next one begins.
     */
    fun layout(
        programmes: List<EpgProgramme>, windowStartSec: Long, windowSec: Long, unitsPerSec: Float,
        gap: Float = 4f, minWidth: Float = 24f,
    ): List<Cell> {
        val windowEnd = windowStartSec + windowSec
        val total = windowSec * unitsPerSec
        val visible = programmes.filter { it.stopSec > windowStartSec && it.startSec < windowEnd }.sortedBy { it.startSec }
        return visible.mapIndexed { i, p ->
            val s = maxOf(p.startSec, windowStartSec)
            // A programme running into the next one's start (bad data) ends there.
            val e = minOf(p.stopSec, windowEnd, visible.getOrNull(i + 1)?.startSec?.takeIf { it > s } ?: Long.MAX_VALUE)
            val x = (s - windowStartSec) * unitsPerSec
            val width = minOf(maxOf((e - s) * unitsPerSec - gap, minWidth), maxOf(total - x, 0f))
            Cell(p, x, width, clippedStart = p.startSec < windowStartSec, clippedEnd = p.stopSec > windowEnd)
        }
    }

    /** The programme on air at [timeSec] (the start belongs to the new programme), or null in a gap. */
    fun programmeAt(programmes: List<EpgProgramme>, timeSec: Long): EpgProgramme? =
        programmes.sortedBy { it.startSec }.lastOrNull { it.startSec <= timeSec && timeSec < it.stopSec }

    /**
     * The programme to land on when moving to a row at [timeSec]: the one on air then, else the one nearest in time (a gap in the
     * guide); on a tie the later one. Null for an empty list.
     */
    fun programmeNear(programmes: List<EpgProgramme>, timeSec: Long): EpgProgramme? {
        programmeAt(programmes, timeSec)?.let { return it }
        fun distance(p: EpgProgramme) = if (timeSec < p.startSec) p.startSec - timeSec else timeSec - p.stopSec
        return programmes.sortedByDescending { it.startSec }.minByOrNull { distance(it) }
    }

    /** The programme following [current] in the row (the first starting at or after its end), or null. */
    fun after(programmes: List<EpgProgramme>, current: EpgProgramme): EpgProgramme? =
        programmes.filter { it !== current && it.startSec >= current.stopSec }.minByOrNull { it.startSec }

    /** The programme preceding [current] in the row (the last ending at or before its start), or null. */
    fun before(programmes: List<EpgProgramme>, current: EpgProgramme): EpgProgramme? =
        programmes.filter { it !== current && it.stopSec <= current.startSec }.maxByOrNull { it.startSec }

    /**
     * Where the window should start so that [target], reached by a step in [direction] (+1 right, -1 left) from a window
     * starting at [windowStartSec], is on screen; the same value when no scrolling is needed. Never earlier than [earliestSec].
     * Going right the new window leaves an hour of context to the left of the target; going left it starts at the target's half hour.
     */
    fun windowStartFor(target: EpgProgramme, direction: Int, windowStartSec: Long, windowSec: Long, earliestSec: Long): Long {
        val windowEnd = windowStartSec + windowSec
        return if (direction > 0) {
            if (target.startSec < windowEnd - SLOT_SEC) windowStartSec else maxOf(earliestSec, floorToSlot(target.startSec) - 2 * SLOT_SEC)
        } else {
            if (target.startSec >= windowStartSec) windowStartSec else maxOf(earliestSec, floorToSlot(target.startSec))
        }
    }
}

/** A programme found by the search, with the channel it is on. [live] is true when it is on air at the time of the search. */
class ProgrammeHit(val channel: LiveChannel, val programme: EpgProgramme, val live: Boolean)

/** Searching programme titles and descriptions, and the short time labels of the results. Pure; tested without Android. */
object ProgrammeSearch {
    /** The typed words: split on whitespace, lower case. */
    fun words(query: String): List<String> = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /**
     * Programmes (from [guides], one entry per channel) whose title or description contains every typed word, anywhere and
     * ignoring case. Programmes that have ended are left out, as is filler such as "No details available". Order: those on air
     * now first, then by start time; ties by channel number and name. One channel's programme appears once. An empty query
     * finds nothing. At most [limit] results.
     */
    fun search(guides: List<Pair<LiveChannel, List<EpgProgramme>>>, query: String, nowSec: Long, limit: Int = 200): List<ProgrammeHit> {
        val needles = words(query)
        if (needles.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        val hits = ArrayList<ProgrammeHit>()
        for ((channel, programmes) in guides) for (p in programmes) {
            if (p.stopSec <= nowSec) continue
            if (SportsLogic.isPlaceholder(p.name, channel.name)) continue
            val text = (PortalText.decodeEntities(p.name) + " " + PortalText.decodeEntities(p.description.orEmpty())).lowercase()
            if (needles.any { it !in text }) continue
            if (seen.add("${channel.id}:${p.startSec}")) hits.add(ProgrammeHit(channel, p, live = p.startSec <= nowSec))
        }
        return hits.sortedWith(
            compareByDescending<ProgrammeHit> { it.live }.thenBy { it.programme.startSec }
                .thenBy { it.channel.number.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.channel.name },
        ).take(limit)
    }

    /**
     * "Live now" while on air, else the start as "Today 8:00 PM", "Tomorrow 8:00 PM", the weekday and time within the next
     * six days ("Fri 8:00 PM"), or a date. [clock] formats the time of day (the device's 12 or 24 hour setting).
     */
    fun whenLabel(startSec: Long, nowSec: Long, zone: TimeZone, locale: Locale, clock: (Long) -> String): String {
        if (startSec <= nowSec) return "Live now"
        // Whole days from today to the start day in the given zone (java.time is avoided: the app supports API 25 without desugaring).
        fun dayNumber(sec: Long) = Math.floorDiv(sec + zone.getOffset(sec * 1000) / 1000, 86_400L)
        val ahead = dayNumber(startSec) - dayNumber(nowSec)
        val time = clock(startSec)
        val cal = Calendar.getInstance(zone, locale).apply { timeInMillis = startSec * 1000 }
        return when {
            ahead == 0L -> "Today $time"
            ahead == 1L -> "Tomorrow $time"
            ahead in 2..6 -> "${cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, locale)} $time"
            else -> "${cal.get(Calendar.DAY_OF_MONTH)} ${cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, locale)} $time"
        }
    }
}
