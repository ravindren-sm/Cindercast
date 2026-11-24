package io.github.ravindren_sm.openstream.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbTest {
    private fun cand(id: Long, title: String, year: String?, votes: Int = 100, original: String? = null) = TmdbCandidate(id, title, original, year, 10.0, votes)

    @Test fun `search results are read for movies and shows`() {
        val movie = """{"results":[{"id":11,"title":"Frozen","original_title":"Frozen","release_date":"2013-11-20","popularity":50.5,"vote_count":9000},{"id":12,"title":"Odd"}]}"""
        val m = TmdbParser.candidates(movie, series = false)
        assertEquals(2, m.size); assertEquals("2013", m[0].year); assertNull(m[1].year); assertEquals(9000, m[0].voteCount)
        val tv = """{"results":[{"id":5,"name":"Bluey","original_name":"Bluey","first_air_date":"2018-10-01","vote_count":300}]}"""
        assertEquals("Bluey", TmdbParser.candidates(tv, series = true).single().title)
        assertTrue(TmdbParser.candidates("not json", false).isEmpty())
    }

    @Test fun `movie details give certifications keywords genres and rating`() {
        val json = """{"id":11,"genres":[{"name":"Animation"},{"name":"Family"}],"original_language":"en","vote_average":7.2,
          "release_dates":{"results":[{"iso_3166_1":"GB","release_dates":[{"certification":"U"}]},{"iso_3166_1":"US","release_dates":[{"certification":""},{"certification":"PG"}]}]},
          "keywords":{"keywords":[{"name":"sister"},{"name":"snow"}]}}"""
        val d = TmdbParser.details(json, series = false)!!
        assertEquals("PG", d.certifications["US"]); assertEquals("U", d.certifications["GB"])
        assertEquals(listOf("Animation", "Family"), d.genres); assertEquals(listOf("sister", "snow"), d.keywords); assertEquals(7.2, d.rating!!, 0.01)
    }

    @Test fun `show details read content ratings and keyword results`() {
        val json = """{"id":5,"genres":[{"name":"Kids"}],"original_language":"en","vote_average":0,
          "content_ratings":{"results":[{"iso_3166_1":"US","rating":"TV-Y"}]},"keywords":{"results":[{"name":"dog"}]}}"""
        val d = TmdbParser.details(json, series = true)!!
        assertEquals("TV-Y", d.certifications["US"]); assertEquals(listOf("dog"), d.keywords); assertNull(d.rating)
    }

    @Test fun `the US label wins, otherwise the strictest known`() {
        assertEquals(8, Certification.pick(mapOf("US" to "PG", "GB" to "15"))!!.level)
        assertEquals(15, Certification.pick(mapOf("GB" to "15", "DE" to "12"))!!.level)
        assertEquals(12, Certification.pick(mapOf("US" to "NR", "DE" to "12"))!!.level)
        assertNull(Certification.pick(mapOf("US" to "NR")))
        assertNull(Certification.pick(emptyMap()))
    }

    @Test fun `an exact name and year is accepted`() {
        val pick = TmdbMatcher.pick("Frozen [Eng Sub]", "2013", listOf(cand(1, "Frozen II", "2019"), cand(2, "Frozen", "2013")))
        assertEquals(2L, pick!!.id)
    }

    @Test fun `a different year is a different film`() {
        assertNull(TmdbMatcher.pick("Frozen", "2005", listOf(cand(2, "Frozen", "2013"))))
        assertEquals(3L, TmdbMatcher.pick("Frozen", "2013", listOf(cand(2, "Frozen", "2010"), cand(3, "Frozen", "2013")))!!.id)
    }

    @Test fun `a year one out is allowed but loses to an exact year`() {
        assertEquals(4L, TmdbMatcher.pick("Up", "2009", listOf(cand(4, "Up", "2010")))!!.id)
        assertEquals(5L, TmdbMatcher.pick("Up", "2009", listOf(cand(4, "Up", "2010"), cand(5, "Up", "2009")))!!.id)
    }

    @Test fun `a title with no year needs standing or to be the only match`() {
        assertEquals(6L, TmdbMatcher.pick("Bluey", null, listOf(cand(6, "Bluey", "2018", votes = 300)))!!.id)
        assertNull(TmdbMatcher.pick("Bluey", null, listOf(cand(6, "Bluey", "2018", votes = 2), cand(7, "Bluey", "1999", votes = 1))))
    }

    @Test fun `a close but not equal name is not accepted`() {
        assertNull(TmdbMatcher.pick("Frozen", "2013", listOf(cand(8, "Frozen Planet", "2013"))))
    }

    @Test fun `the year at the end of a portal name is used`() {
        assertEquals("frozen" to "2013", TmdbMatcher.clean("Frozen (2013)"))
        assertEquals(9L, TmdbMatcher.pick("Frozen (2013)", null, listOf(cand(9, "Frozen", "2013")))!!.id)
    }

    @Test fun `the original title can match`() {
        assertEquals(10L, TmdbMatcher.pick("Lion King", "1994", listOf(cand(10, "Le Roi Lion", "1994", original = "The Lion King")).map { it.copy(originalTitle = "Lion King") })!!.id)
    }

    @Test fun `keywords with a listed word flag the title`() {
        assertTrue(TagFlags.flagged(listOf("Drama"), listOf("nudity", "love")))
        assertFalse(TagFlags.flagged(listOf("Family"), listOf("dog", "picnic")))
    }
}
