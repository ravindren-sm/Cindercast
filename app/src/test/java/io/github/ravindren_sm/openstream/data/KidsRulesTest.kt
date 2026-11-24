package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.VodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsRulesTest {
    private fun item(name: String, age: String? = "G", description: String? = null, genres: String? = null, season: Boolean = false, episode: Boolean = false) =
        VodItem(id = "1", name = name, isSeries = false, isSeason = season, isEpisode = episode, cmd = null, episodeNumbers = emptyList(), description = description, genres = genres, age = age)

    private val kids = KidsRules(maxAge = 8)

    @Test fun `a word matches only as a whole word`() {
        val f = TextFilter()
        assertTrue(f.blocks("Sex and the City"))
        assertTrue(f.blocks("hot SEXY night"))
        assertFalse(f.blocks("Essex Boys"))
        assertFalse(f.blocks("Scunthorpe United"))
        assertFalse(f.blocks("Sussex"))
    }

    @Test fun `punctuation and case do not hide a word`() {
        val f = TextFilter()
        assertTrue(f.blocks("S.E.X"  ).not())       // spelled out letters are not matched: the age label is the main gate
        assertTrue(f.blocks("PORN!"))
        assertTrue(f.blocks("a-nude-beach"))
    }

    @Test fun `a phrase matches when its words are together`() {
        val f = TextFilter(listOf("strip club"))
        assertTrue(f.blocks("Night at the Strip Club"))
        assertFalse(f.blocks("Club of the strip mall"))
    }

    @Test fun `blank or missing text never blocks`() {
        val f = TextFilter()
        assertFalse(f.blocks(null)); assertFalse(f.blocks("")); assertFalse(f.blocks("   "))
    }

    @Test fun `kids see a known age label at or under the ceiling`() {
        assertTrue(kids.allows(item("Tom and Jerry", "G")))
        assertTrue(kids.allows(item("Frozen", "PG")))
        assertTrue(kids.allows(item("Bluey", "TV-Y")))
        assertFalse(kids.allows(item("A Teen Film", "PG-13")))
        assertFalse(kids.allows(item("An Adult Film", "R")))
        assertFalse(kids.allows(item("Grown Up", "16+")))
    }

    @Test fun `a title with no age label is hidden from kids`() {
        assertFalse(kids.allows(item("Mystery Title", null)))
        assertFalse(kids.allows(item("Unrated Title", "NR")))
        assertFalse(kids.allows(item("Blank", "")))
    }

    @Test fun `a higher ceiling lets more through`() {
        assertTrue(KidsRules(13).allows(item("A Teen Film", "PG-13")))
        assertFalse(KidsRules(13).allows(item("An Adult Film", "R")))
        assertFalse(KidsRules(0).allows(item("Frozen", "PG")))
    }

    @Test fun `a listed word in the description or genres hides the title even with a good label`() {
        assertFalse(kids.allows(item("Nice Name", "G", description = "A story about a stripper")))
        assertFalse(kids.allows(item("Nice Name", "G", genres = "Comedy, Erotic")))
        assertTrue(kids.allows(item("Nice Name", "G", description = "A story about a dog", genres = "Family")))
    }

    @Test fun `seasons and episodes need only pass the word list`() {
        assertTrue(kids.allows(item("Season 1", age = null, season = true)))
        assertTrue(kids.allows(item("Episode 4: The Picnic", age = null, episode = true)))
        assertFalse(kids.allows(item("Episode 5: Naked Truth", age = null, episode = true)))
    }

    @Test fun `the index form applies the same age and word rules`() {
        assertTrue(kids.allows("Frozen", "PG", "Animation"))
        assertFalse(kids.allows("Frozen", null, "Animation"))
        assertFalse(kids.allows("Porn Star", "G", null))
        assertFalse(kids.allows("Rated R Film", "R", null))
    }

    @Test fun `the policy applies the audience rules and the rating floor together`() {
        val policy = ContentPolicy(minRating = 6.0, kids = kids)
        val good = item("Frozen", "PG").let { VodItem(it.id, it.name, false, false, false, null, emptyList(), age = "PG", imdbRating = 7.5) }
        val low = VodItem("2", "Low Rated", false, false, false, null, emptyList(), age = "G", imdbRating = 3.0)
        val adult = VodItem("3", "Grown Up", false, false, false, null, emptyList(), age = "R", imdbRating = 9.0)
        assertTrue(policy.passes(good))
        assertFalse(policy.passes(low))
        assertFalse(policy.passes(adult))
        assertTrue(policy.isKids)
        assertFalse(ContentPolicy().isKids)
        assertEquals(true, ContentPolicy().passes(adult))
    }
}
