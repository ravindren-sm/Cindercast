package io.github.ravindren_sm.openstream.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VodItemTagsTest {
    private fun item(imdb: Double? = null, kp: Double? = null, genres: String? = null, age: String? = null) =
        VodItem(id = "1", name = "T", isSeries = false, isSeason = false, isEpisode = false, cmd = null, episodeNumbers = emptyList(),
            genres = genres, imdbRating = imdb, kinopoiskRating = kp, age = age)

    @Test fun `tags fill gaps in score genres and age`() {
        val t = item().withTags(7.4, "Animation, Family", "PG")
        assertEquals(ShownRating(7.4, "TMDB"), t.shownRating); assertEquals("Animation, Family", t.genres); assertEquals("PG", t.age)
    }

    @Test fun `the portal's own values always win`() {
        val t = item(imdb = 6.0, genres = "Drama", age = "R").withTags(9.0, "Comedy", "G")
        assertEquals(ShownRating(6.0, "IMDb"), t.shownRating); assertEquals("Drama", t.genres); assertEquals("R", t.age)
        assertEquals(ShownRating(5.5, "Kinopoisk"), item(kp = 5.5).withTags(9.0, null, null).shownRating)
    }

    @Test fun `a title with no score anywhere shows none`() {
        assertNull(item().withTags(null, null, null).shownRating)
    }
}
