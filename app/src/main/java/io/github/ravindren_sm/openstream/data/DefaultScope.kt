package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.LiveGenre
import io.github.ravindren_sm.openstream.provider.VodItem

/**
 * The viewer's saved search defaults, applied beyond search when the profile asks for it (Settings, "Use my search defaults everywhere"):
 * to Movies and Shows lists, Home's "New in" rows, and the Live TV and guide groups. Plain Kotlin, tested without Android.
 *
 * Categories: a category choice or a language choice narrows the list of categories, as it does in search. Titles: genre, rating and
 * English-subtitle choices are checked on each title. Live groups: channels carry no genre or rating, so only the language applies,
 * to groups whose names mention a language ("ENGLISH | NEWS"); groups naming no language, and All channels, stay.
 */
class DefaultScope(private val filters: SearchFilters) {
    val active get() = filters.isActive

    fun categoryAllowed(c: VodCategory): Boolean {
        if (filters.categoryIds.isNotEmpty() && c.id !in filters.categoryIds) return false
        if (filters.languages.isNotEmpty() && FilterRules.languagesIn(c.title).none { it in filters.languages }) return false
        return true
    }

    /** Genre, rating and subtitle choices (language and category are handled on the category list). */
    fun titlePasses(item: VodItem): Boolean = FilterRules.passes(item, filters.copy(languages = emptySet(), categoryIds = emptySet()))

    fun groupAllowed(g: LiveGenre): Boolean {
        if (g.id == "*" || filters.languages.isEmpty()) return true
        val mentioned = FilterRules.languagesIn(g.title)
        return mentioned.isEmpty() || mentioned.any { it in filters.languages }
    }

    /** Up to three genres the device-copy query can take, or null when something it cannot express is on (the subtitle wording check, more genres). */
    val indexGenres: List<String>? get() = if (filters.englishSubtitles || filters.genres.size > 3) null else filters.genres.sorted()

    /** The rating floor from the defaults (this floor also drops unrated titles, as in search). */
    val minRating: Double? get() = filters.minRating

    fun summary(): String = filters.summary()
}
