package io.github.ravindren_sm.openstream.data.imdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImdbRatingsTest {
    private val file = sequenceOf("tconst\taverageRating\tnumVotes", "tt0000001\t5.7\t2100", "tt0000002\t5.8\t270", "tt0000003\t6.5\t2000", "tt0000004\t0.0\t5", "bad", "")

    @Test fun `only the wanted titles are kept`() {
        val r = ImdbRatings.parse(file, setOf("tt0000001", "tt0000003"))
        assertEquals(mapOf("tt0000001" to 5.7, "tt0000003" to 6.5), r)
    }

    @Test fun `the header, blank, broken and zero lines are skipped`() {
        val r = ImdbRatings.parse(file, setOf("tconst", "bad", "tt0000004"))
        assertTrue(r.isEmpty())
    }

    @Test fun `a line with no vote column still parses`() {
        assertEquals(7.1, ImdbRatings.parse(sequenceOf("tt0000009\t7.1"), setOf("tt0000009"))["tt0000009"]!!, 0.001)
    }

    @Test fun `the credit line is IMDb's required wording`() {
        assertEquals("Information courtesy of IMDb (https://www.imdb.com). Used with permission.", ImdbRatings.CREDIT)
        assertFalse(ImdbRatings.URL.contains("imdb.com/title"))
    }
}
