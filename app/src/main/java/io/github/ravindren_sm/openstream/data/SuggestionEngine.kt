package io.github.ravindren_sm.openstream.data

/** Where a suggestion came from. Shown as a small label and used to order ties. */
enum class SuggestionSource(val label: String) { RECENT("Recent"), FAVORITE("Favorite"), WATCHED("Watched"), CHANNEL("Channel"), TITLE("Title") }

class Suggestion(val text: String, val source: SuggestionSource)

/**
 * Type-ahead suggestions from text the device already has: recent searches, favorites, watch history, channel names and
 * the local title index. No portal request. A suggestion matches when its words start with the typed text or the whole text
 * starts with it; whole-text prefixes rank above word prefixes, and within a rank the more personal source wins.
 * With nothing typed, recent searches are offered. Plain Kotlin, tested without Android.
 */
object SuggestionEngine {
    const val MAX = 8

    /** [sources] are candidate texts per source, in the source's own order (newest first for recents). */
    fun suggest(query: String, sources: Map<SuggestionSource, List<String>>, max: Int = MAX): List<Suggestion> {
        val q = SearchRanking.normalize(query)
        if (q.isEmpty()) return sources[SuggestionSource.RECENT].orEmpty().take(max).map { Suggestion(it, SuggestionSource.RECENT) }
        data class Hit(val text: String, val source: SuggestionSource, val rank: Int, val order: Int)
        val hits = mutableListOf<Hit>()
        val seen = HashSet<String>()
        for (source in SuggestionSource.entries) {
            sources[source].orEmpty().forEachIndexed { i, text ->
                val n = SearchRanking.normalize(text)
                if (n == q) return@forEachIndexed                          // already typed in full: nothing to suggest
                val rank = when {
                    n.startsWith(q) -> 0
                    n.split(' ').any { it.startsWith(q) } || (q.contains(' ') && n.contains(q)) -> 1
                    else -> return@forEachIndexed
                }
                // A title and its quality variants count once: compare on the name without tags.
                if (seen.add(TitleMerge.baseKey(text).ifEmpty { n })) hits += Hit(text.trim(), source, rank, i)
            }
        }
        return hits.sortedWith(compareBy({ it.rank }, { it.source.ordinal }, { it.order })).take(max).map { Suggestion(it.text, it.source) }
    }
}
