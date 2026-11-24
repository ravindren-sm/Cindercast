package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.ShownRating

/**
 * How a rating is labelled on a poster card, by where it came from (see `VodItem.shownRating`): a star for IMDb, "KP" for
 * Kinopoisk, "TMDB" for the online tags' score. Any other source is named in full, so a score is never passed off as another's.
 */
object RatingLabels {
    fun badge(rating: ShownRating): String = when (rating.source) {
        "IMDb" -> "★ %.1f"
        "Kinopoisk" -> "KP %.1f"
        else -> rating.source + " %.1f"
    }.format(rating.value)
}
