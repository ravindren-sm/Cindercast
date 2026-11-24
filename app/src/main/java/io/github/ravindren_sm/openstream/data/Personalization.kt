package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.VodItem

/** What a title says about itself that the stats can match: its genres, languages and its own name. Plain Kotlin, tested without Android. */
object WatchTags {
    /** Genres from the portal's text ("Comedy, Drama" or "Comedy / Drama"), trimmed and capitalised, no repeats. */
    fun genresOf(text: String?): List<String> =
        text.orEmpty().split(',', '/', '|', ';').map { it.trim().lowercase().replaceFirstChar { c -> c.uppercase() } }.filter { it.length >= 2 }.distinct()

    /** Languages a text mentions ("[Hindi]" in a title, "ENGLISH MOVIES" in a category). */
    fun languagesOf(text: String?): Set<String> = FilterRules.languagesIn(text.orEmpty())

    /** One key for every version of a title, so watching the 4K copy counts for the HD copy too. */
    fun titleKey(name: String): String = TitleMerge.baseKey(name)
}

/** What was watched, as counts: the genres, the languages and the titles (shows or movies, by [WatchTags.titleKey]). */
class Personalization(
    private val genres: Map<String, Int> = emptyMap(),
    private val languages: Map<String, Int> = emptyMap(),
    private val titles: Map<String, Int> = emptyMap(),
) {
    val isEmpty get() = genres.isEmpty() && languages.isEmpty() && titles.isEmpty()
    private val genreTotal = genres.values.sum().coerceAtLeast(1)
    private val languageTotal = languages.values.sum().coerceAtLeast(1)

    /**
     * How much a title fits what the viewer watches, 0 or more. A title the viewer already watches (any version) leads; then the
     * share of the viewer's viewing in this title's genres and languages. Never used to hide anything, only to order.
     */
    fun score(item: VodItem): Double {
        var s = 0.0
        titles[WatchTags.titleKey(item.name)]?.let { s += 2.0 + 0.3 * it.coerceAtMost(5) }
        for (g in WatchTags.genresOf(item.genres)) s += (genres[g] ?: 0).toDouble() / genreTotal
        for (l in WatchTags.languagesOf(item.name)) s += (languages[l] ?: 0).toDouble() / languageTotal
        return s
    }
}
