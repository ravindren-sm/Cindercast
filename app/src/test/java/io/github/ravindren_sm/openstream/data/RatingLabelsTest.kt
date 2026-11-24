package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.ShownRating
import kotlin.test.Test
import kotlin.test.assertEquals

class RatingLabelsTest {
    @Test fun eachSourceKeepsItsOwnLabel() {
        assertEquals("★ 7.4", RatingLabels.badge(ShownRating(7.4, "IMDb")))
        assertEquals("KP 6.9", RatingLabels.badge(ShownRating(6.9, "Kinopoisk")))
        assertEquals("TMDB 7.1", RatingLabels.badge(ShownRating(7.1, "TMDB")))   // was shown as "KP 7.1"
    }
}
