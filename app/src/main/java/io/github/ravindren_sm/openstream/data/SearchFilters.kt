package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.VodItem

/**
 * What the viewer narrows a search to. All parts apply together (language AND genre AND category AND rating); within one
 * part, several choices mean "any of them". They apply to movies and shows; channels have none of these properties, so a
 * search with any filter on shows no channels. [minRottenTomatoes] is kept for when a ratings source is configured.
 */
data class SearchFilters(
    val languages: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val categoryIds: Set<String> = emptySet(),
    /** Minimum of the rating the portal gives (its IMDb value, else its Kinopoisk value), or null for any. */
    val minRating: Double? = null,
    val minRottenTomatoes: Int? = null,
    /** Keep only titles whose name or category says they have English subtitles (the portal's lists carry no subtitle field). */
    val englishSubtitles: Boolean = false,
) {
    val isActive get() = englishSubtitles || languages.isNotEmpty() || genres.isNotEmpty() || categoryIds.isNotEmpty() || minRating != null || minRottenTomatoes != null

    /** A compact text for saving: `lang=English|Hindi;genre=Comedy;cat=12|14;rating=7.0;rt=75`. Empty parts are left out. */
    fun encode(): String = listOfNotNull(
        languages.takeIf { it.isNotEmpty() }?.let { "lang=" + it.sorted().joinToString("|") },
        genres.takeIf { it.isNotEmpty() }?.let { "genre=" + it.sorted().joinToString("|") },
        categoryIds.takeIf { it.isNotEmpty() }?.let { "cat=" + it.sorted().joinToString("|") },
        minRating?.let { "rating=$it" },
        minRottenTomatoes?.let { "rt=$it" },
        "subs=1".takeIf { englishSubtitles },
    ).joinToString(";")

    fun summary(categoryTitle: (String) -> String? = { it }): String = listOfNotNull(
        languages.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(", "),
        genres.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(", "),
        categoryIds.takeIf { it.isNotEmpty() }?.mapNotNull(categoryTitle)?.joinToString(", "),
        minRating?.let { "rating ${"%.0f".format(it)}+" },
        minRottenTomatoes?.let { "RT $it%+" },
        "English subtitles".takeIf { englishSubtitles },
    ).joinToString(" · ")

    companion object {
        val LANGUAGE_NAMES = listOf(
            "English", "Hindi", "Tamil", "Telugu", "Malayalam", "Kannada", "Bengali", "Punjabi", "Marathi", "Gujarati", "Urdu",
            "Arabic", "Spanish", "French", "German", "Italian", "Turkish", "Korean", "Japanese", "Chinese", "Russian", "Portuguese", "Persian", "Thai",
        )
        /** The genres offered in the picker, in alphabetical order. */
        val GENRES = listOf("Comedy", "Drama", "Action", "Animation", "Family", "Documentary", "Thriller", "Horror", "Romance", "Sci-Fi", "Crime", "Adventure", "Fantasy", "Mystery").sortedBy { it.lowercase() }
        val RATING_STEPS = listOf(5.0, 6.0, 7.0, 8.0)
        val ROTTEN_STEPS = listOf(60, 75, 90)

        fun decode(text: String?): SearchFilters {
            if (text.isNullOrBlank()) return SearchFilters()
            val parts = text.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            fun set(key: String) = parts[key]?.split('|')?.filter { it.isNotBlank() }?.toSet().orEmpty()
            return SearchFilters(set("lang"), set("genre"), set("cat"), parts["rating"]?.toDoubleOrNull(), parts["rt"]?.toIntOrNull(), parts["subs"] == "1")
        }
    }
}

/** Which categories to ask the portal for, given the filters. [ids] null means "all categories" (no category or language filter). */
class CategoryPlan(val ids: List<String>?, val capped: Boolean, val matching: Int) {
    /** The language or category choices left nothing to search. */
    val none get() = ids != null && ids.isEmpty()
}

/** The rules that connect filters to the catalog. Plain Kotlin, tested without Android. */
object FilterRules {
    private val LANGUAGE_REGEXES = SearchFilters.LANGUAGE_NAMES.associateWith { Regex("(?i)\\b${Regex.escape(it)}\\b") }

    /** The languages a category's name mentions ("HINDI WEB SERIES" gives Hindi). Categories carry no language field of their own. */
    fun languagesIn(categoryTitle: String): Set<String> = LANGUAGE_REGEXES.filter { (_, r) -> r.containsMatchIn(categoryTitle) }.keys

    /** Languages that at least one category mentions, in the usual order, so the picker offers only real choices. */
    fun languageOptions(categories: List<VodCategory>): List<String> {
        val present = categories.flatMapTo(HashSet()) { languagesIn(it.title) }
        return SearchFilters.LANGUAGE_NAMES.filter { it in present }.sorted()          // alphabetical for the picker
    }

    /**
     * The categories to search. A category choice narrows to those categories; a language choice narrows to categories
     * whose names mention the language; both together must hold. With neither, everything is searched at once. More than
     * [cap] matches are cut to the first [cap] (the portal answers one category per request).
     */
    fun plan(all: List<VodCategory>, f: SearchFilters, cap: Int = 6): CategoryPlan {
        if (f.categoryIds.isEmpty() && f.languages.isEmpty()) return CategoryPlan(null, false, all.size)
        var candidates = all
        if (f.categoryIds.isNotEmpty()) candidates = candidates.filter { it.id in f.categoryIds }
        if (f.languages.isNotEmpty()) candidates = candidates.filter { c -> languagesIn(c.title).any { it in f.languages } }
        return CategoryPlan(candidates.take(cap).map { it.id }, candidates.size > cap, candidates.size)
    }

    private val ENGLISH_SUBS = Regex("(?i)\\b(e-?subs?|eng(lish)?[\\s._-]*sub(s|bed|titles?)?|sub(s|bed|titles?)?[\\s._-]*eng(lish)?|english[\\s._-]+subtitles?|hardsubs?)\\b")

    /** Whether a title or category name says it has English subtitles ("[Eng Sub]", "English Subtitles", "ESub"). */
    fun mentionsEnglishSubtitles(text: String): Boolean = ENGLISH_SUBS.containsMatchIn(text)

    /**
     * Genre, rating and subtitle checks on one title (language and category were applied by the plan). [categoryHint] says
     * the title's category name already mentions English subtitles.
     */
    fun passes(item: VodItem, f: SearchFilters, categoryHint: Boolean = false, rottenScore: (VodItem) -> Int? = { null }): Boolean {
        if (f.englishSubtitles && !categoryHint && !mentionsEnglishSubtitles(item.name)) return false
        if (f.genres.isNotEmpty()) {
            val text = (item.genres ?: "").lowercase()
            if (f.genres.none { text.contains(it.lowercase()) }) return false
        }
        f.minRating?.let { min -> if ((item.shownRating?.value ?: return false) < min) return false }
        f.minRottenTomatoes?.let { min -> if ((rottenScore(item) ?: return false) < min) return false }
        return true
    }
}
