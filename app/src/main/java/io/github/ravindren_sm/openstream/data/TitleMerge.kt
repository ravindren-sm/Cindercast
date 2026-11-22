package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.PortalText
import io.github.ravindren_sm.openstream.provider.VodItem

/** Several catalog entries that are the same title in different versions (quality, language). [best] is shown; [versions] are all of them, best first. */
class TitleGroup(val best: VodItem, val versions: List<VodItem>) {
    val count get() = versions.size
}

/**
 * Folds the portal's repeated entries (the same film listed several times for different quality or language, each with its
 * own id) into one. Two entries are the same title when their names agree once quality and language tags are removed and
 * their years do not conflict. Plain Kotlin, tested without Android.
 */
object TitleMerge {
    // Quality words and release tags, removed from the end of a name or from brackets.
    private val QUALITY = Regex("(?i)\\b(4k|uhd|2160p?|1080p?|720p?|480p?|fhd|hd|sd|hdr10?|hevc|x26[45]|hdcam|cam|webrip|web-dl|webdl|bluray|brrip|dvdrip|dvd)\\b")
    private val LANGUAGE = Regex("(?i)\\b(english|eng|en|hindi|hin|hi|tamil|tam|telugu|tel|bengali|ben|malayalam|mal|kannada|kan|punjabi|urdu|dubbed|dual audio|multi)\\b")
    private val BRACKETED = Regex("[\\[(][^\\])]{0,40}[\\])]")
    private val SEPARATORS = Regex("\\s*[|:–—-]\\s*$")

    /** The name with entities decoded, lower-cased, tags and brackets removed, punctuation and spaces collapsed. */
    fun baseKey(name: String): String {
        var n = PortalText.decodeEntities(name).lowercase()
        n = BRACKETED.replace(n) { m -> if (QUALITY.containsMatchIn(m.value) || LANGUAGE.containsMatchIn(m.value)) " " else m.value }
        n = QUALITY.replace(n, " ")
        n = LANGUAGE.replace(n) { m -> if (n.trim().equals(m.value.trim(), ignoreCase = true)) m.value else " " }
        n = n.replace(Regex("[^\\p{L}\\p{N}&' ]+"), " ").replace(Regex("\\s+"), " ").trim()
        return SEPARATORS.replace(n, "").trim()
    }

    /** Higher is better: 4K, then 1080p, HD, nothing stated, SD, and cam recordings last. */
    fun qualityScore(name: String): Int {
        val n = name.lowercase()
        return when {
            Regex("\\b(cam|hdcam)\\b").containsMatchIn(n) -> 0
            Regex("\\b(4k|uhd|2160p?)\\b").containsMatchIn(n) -> 5
            Regex("\\b(1080p?|fhd)\\b").containsMatchIn(n) -> 4
            Regex("\\b(hd|720p?)\\b").containsMatchIn(n) -> 3
            Regex("\\b(sd|480p?|dvd\\w*)\\b").containsMatchIn(n) -> 1
            else -> 2
        }
    }

    /** A short label for choosing between versions: the quality and language words in the raw name, else "Version n". */
    fun versionLabel(item: VodItem, index: Int): String {
        val name = PortalText.decodeEntities(item.name)
        val q = QUALITY.findAll(name).map { it.value.uppercase() }.distinct().toList()
        val l = LANGUAGE.findAll(name).map { it.value.replaceFirstChar { c -> c.uppercase() } }.distinct().toList()
        val tags = (q + l).joinToString(" · ")
        return if (tags.isEmpty()) "Version ${index + 1}" else tags
    }

    /**
     * Groups [items] in first-seen order. Entries with different years (when both state one) stay apart; an entry with no
     * year joins the first group of its name. Movies and shows are never mixed.
     */
    fun group(items: List<VodItem>): List<TitleGroup> {
        class Bucket(val key: String, val series: Boolean, var year: String?, val items: MutableList<VodItem> = mutableListOf())
        val buckets = mutableListOf<Bucket>()
        for (item in items) {
            val key = baseKey(item.name)
            val year = item.year?.trim()?.takeIf { it.isNotEmpty() }
            val match = buckets.firstOrNull { b ->
                b.key == key && b.series == item.isSeries && (year == null || b.year == null || b.year == year)
            }
            if (match == null) buckets += Bucket(key, item.isSeries, year).also { it.items += item }
            else { match.items += item; if (match.year == null) match.year = year }
        }
        return buckets.map { b ->
            val ordered = b.items.withIndex().sortedWith(compareBy({ -qualityScore(it.value.name) }, { it.index })).map { it.value }
            TitleGroup(ordered.first(), ordered)
        }
    }
}
