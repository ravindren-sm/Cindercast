package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.provider.LiveChannel

/*
 * Plain-Kotlin helpers for the Live TV browser and the live player: quality badges, sorting, channel logo addresses, the
 * programme banner's text and the mini channel list's window. No Android types, so all of it is unit-tested on the JVM.
 */

/** A picture-quality tag found in a channel name. Declared lowest to highest; [label] is what the badge shows. */
enum class ChannelQuality(val label: String) { HD("HD"), FHD("FHD"), UHD("UHD"), K4("4K") }

/** A channel name split into the name to show and the quality tag it carried (null when it carried none). */
class ParsedChannelName(val display: String, val quality: ChannelQuality?)

object ChannelNames {
    // A tag is a whole word: not glued to letters or digits on either side (so "SHD" or "HD1x" are not tags). A resolution needs
    // its p or i, so a channel called "Studio 1080" keeps its number.
    private val TAG = Regex(
        "(?<![A-Za-z0-9])(4K|UHD|FULL\\s?HD|FHD|HD|2160[pi]|1080[pi]|720p)(?![A-Za-z0-9])",
        RegexOption.IGNORE_CASE,
    )
    private val EMPTY_BRACKETS = Regex("[(\\[{]\\s*[)\\]}]")
    private val EDGE_SEPARATORS = Regex("^[\\s\\-|:/,.·•]+|[\\s\\-|:/,·•]+$")
    private val SPACES = Regex("\\s{2,}")

    private fun qualityOf(tag: String): ChannelQuality = when (tag.uppercase().replace(" ", "")) {
        "4K" -> ChannelQuality.K4
        "UHD", "2160P", "2160I" -> ChannelQuality.UHD
        "FHD", "FULLHD", "1080P", "1080I" -> ChannelQuality.FHD
        else -> ChannelQuality.HD
    }

    /**
     * The highest quality tag in [name] and the name with every tag removed (and the brackets or separators they leave
     * behind). A name that is nothing but a tag is kept whole, so a row is never blank.
     */
    fun parse(name: String): ParsedChannelName {
        val tags = TAG.findAll(name).map { qualityOf(it.value) }.toList()
        if (tags.isEmpty()) return ParsedChannelName(name, null)
        val cleaned = TAG.replace(name, " ").replace(EMPTY_BRACKETS, " ").replace(SPACES, " ").replace(EDGE_SEPARATORS, "").trim()
        return ParsedChannelName(cleaned.ifEmpty { name }, tags.maxByOrNull { it.ordinal })
    }
}

/** How the channels of a group are ordered on screen. */
enum class ChannelSort(val label: String) {
    /** The portal's own order, which is by channel number. */
    NUMBER("Number"),
    /** By name, case-insensitive, with the quality tags ignored. Needs the whole group on the device. */
    NAME("A to Z"),
}

/** Where an A to Z sort stands, for the status line. */
sealed interface SortStatus {
    data object Idle : SortStatus
    class Loading(val loaded: Int, val total: Int) : SortStatus
    data object Done : SortStatus
    /** The group is larger than [ChannelSorting.CAP]: it stays in the portal's order. */
    class TooBig(val total: Int) : SortStatus
    class Failed(val message: String) : SortStatus
}

object ChannelSorting {
    /**
     * The largest group sorted on the device. The portal's list sort cannot be relied on (the app only ever asks for sortby
     * number and nothing in the protocol notes shows that the portal honours another), so A to Z loads every page of the
     * group: 14 channels a request, so this cap is about 43 requests (some ten seconds). A group of 5,000 is out of reach.
     */
    const val CAP = 600

    fun canSortFully(total: Int?): Boolean = total != null && total <= CAP

    /** [channels] by name (tags ignored, numbers inside names in numeric order), then channel number, then id. */
    fun byName(channels: List<LiveChannel>): List<LiveChannel> {
        val keyed = channels.map { it to ChannelNames.parse(io.github.ravindren_sm.openstream.provider.PortalText.decodeEntities(it.name)).display.lowercase() }
        return keyed.sortedWith(
            Comparator<Pair<LiveChannel, String>> { a, b -> naturalCompare(a.second, b.second) }
                .thenBy { it.first.number.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.first.id },
        ).map { it.first }
    }

    /** Compares text so that "channel 2" comes before "channel 10". */
    fun naturalCompare(a: String, b: String): Int {
        var i = 0; var j = 0
        while (i < a.length && j < b.length) {
            if (a[i].isDigit() && b[j].isDigit()) {
                var ei = i; while (ei < a.length && a[ei].isDigit()) ei++
                var ej = j; while (ej < b.length && b[ej].isDigit()) ej++
                val na = a.substring(i, ei).trimStart('0'); val nb = b.substring(j, ej).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
                i = ei; j = ej
            } else {
                if (a[i] != b[j]) return a[i].compareTo(b[j])
                i++; j++
            }
        }
        return (a.length - i) - (b.length - j)
    }

    /** One line under the list's header for the sort, or "" when there is nothing to say. */
    fun statusText(sort: ChannelSort, status: SortStatus): String = when {
        sort == ChannelSort.NUMBER -> ""
        status is SortStatus.Loading -> "Loading the group to sort it: ${status.loaded} of ${status.total} channels…"
        status is SortStatus.TooBig -> "This group has %,d channels, too many to sort here (up to %,d). Showing the portal's order.".format(status.total, CAP)
        status is SortStatus.Failed -> "Could not load the whole group to sort it. Showing the portal's order. Choose Sort again to retry."
        status is SortStatus.Done -> "Sorted A to Z on this device"
        else -> ""
    }
}

/** Resolving the "logo" field of a channel to an address. */
object ChannelLogoUrl {
    /**
     * An absolute address, or a path on the portal host, resolves like a poster. A bare file name (the Stalker habit) is looked
     * for under the portal's logo folder, `misc/logos/320/` (below `stalker_portal/` when the portal address has it): this
     * location is the usual one but is not confirmed against the real portal. Null when there is nothing usable.
     */
    fun resolve(raw: String?, portalUrl: String): String? {
        val r = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        PosterUrl.resolve(r, portalUrl)?.let { return it }
        if (r.contains("://") || r.startsWith("/") || r.contains(' ')) return null
        val scheme = portalUrl.substringBefore("://", "")
        val rest = portalUrl.substringAfter("://", "")
        val authority = rest.substringBefore('/').takeIf { it.isNotEmpty() } ?: return null
        if (scheme.isEmpty()) return null
        val base = if (rest.contains("/stalker_portal")) "$scheme://$authority/stalker_portal" else "$scheme://$authority"
        return "$base/misc/logos/320/$r"
    }
}

/** Text and helpers for the programme banner and the preview panel. */
object LiveInfo {
    /** "12 min left", "1 h 5 min left"; at least a minute while the programme is still on. */
    fun timeLeft(stopSec: Long, nowSec: Long): String {
        val left = ((stopSec - nowSec) / 60).coerceAtLeast(1)
        return if (left >= 60) "${left / 60} h ${left % 60} min left" else "$left min left"
    }

    /**
     * What is on now and next, with the portal's filler entries ("No details available") ignored, so a channel that only has
     * filler counts as having no guide.
     */
    fun nowNext(programmes: List<EpgProgramme>?, channelName: String, nowSec: Long): GuideLogic.NowNext? =
        programmes?.filterNot { SportsLogic.isPlaceholder(it.name, channelName) }?.let { GuideLogic.nowAndNext(it, nowSec) }
            ?.takeIf { it.now != null || it.next != null }
}

/** Which rows the mini channel list shows. */
object MiniListWindow {
    const val ROWS = 9

    /**
     * The rows to show for [size] channels with the cursor at [cursor]: [rows] of them (fewer if there are fewer channels), the
     * cursor near the middle, and the window held against the first and last channel rather than running past it.
     */
    fun range(size: Int, cursor: Int, rows: Int = ROWS): IntRange {
        if (size <= 0 || rows <= 0) return IntRange.EMPTY
        val count = minOf(rows, size)
        val at = cursor.coerceIn(0, size - 1)
        val start = (at - count / 2).coerceIn(0, size - count)
        return start until start + count
    }
}
