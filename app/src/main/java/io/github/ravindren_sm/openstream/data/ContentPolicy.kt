package io.github.ravindren_sm.openstream.data

import android.content.SharedPreferences
import io.github.ravindren_sm.openstream.provider.VodItem

/**
 * What a profile never wants to see: titles rated below [minRating] (the portal's IMDb value, else its Kinopoisk value).
 * Titles with no rating are shown unless [hideUnrated] is set. Applies to movies and shows in lists, search and Home rows.
 */
data class ContentPolicy(val minRating: Double? = null, val hideUnrated: Boolean = false) {
    val active get() = minRating != null

    fun passes(rating: Double?): Boolean {
        val min = minRating ?: return true
        return if (rating == null) !hideUnrated else rating >= min
    }

    fun passes(item: VodItem): Boolean = passes(item.shownRating?.value)

    fun summary(): String = if (minRating == null) "Show everything"
    else "Hide below ${"%.0f".format(minRating)}" + if (hideUnrated) " and unrated" else ""

    companion object { val STEPS = listOf(5.0, 6.0, 7.0, 8.0) }
}

/** Keeps each profile's policy on the device, under keys that name the profile so profiles stay separate. */
class ContentPolicyStore(private val prefs: SharedPreferences) {
    private fun key(profileId: Long, name: String) = "p$profileId.$name"

    fun load(profileId: Long): ContentPolicy {
        val min = prefs.getFloat(key(profileId, "min_rating"), -1f).takeIf { it > 0 }?.toDouble()
        return ContentPolicy(min, prefs.getBoolean(key(profileId, "hide_unrated"), false))
    }

    fun save(profileId: Long, policy: ContentPolicy) {
        prefs.edit()
            .putFloat(key(profileId, "min_rating"), policy.minRating?.toFloat() ?: -1f)
            .putBoolean(key(profileId, "hide_unrated"), policy.hideUnrated)
            .apply()
    }
}

/** The orders a category can be shown in. [code] is what the index query takes. */
enum class BrowseSort(val label: String, val code: Int, val needsIndex: Boolean) {
    NEWEST("Newest added", 1, false),
    YEAR("Release year", 2, true),
    BEST("Best rated", 3, true),
    IMDB("IMDb", 4, true),
    KINOPOISK("Kinopoisk", 5, true),
    NAME("Name A to Z", 6, true),
}
