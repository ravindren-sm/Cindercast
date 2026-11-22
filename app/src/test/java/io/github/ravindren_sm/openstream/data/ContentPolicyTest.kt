package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.VodItem
import io.github.ravindren_sm.openstream.provider.VodPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentPolicyTest {
    private fun v(id: String, imdb: Double? = null, kp: Double? = null) =
        VodItem(id = id, name = id, isSeries = false, isSeason = false, isEpisode = false, cmd = "c", episodeNumbers = emptyList(), imdbRating = imdb, kinopoiskRating = kp)

    @Test fun noFloorShowsEverything() {
        val p = ContentPolicy()
        assertTrue(p.passes(v("a", imdb = 1.0))); assertTrue(p.passes(v("b"))); assertFalse(p.active)
    }

    @Test fun theFloorUsesImdbThenKinopoiskAndUnratedFollowsTheSetting() {
        val show = ContentPolicy(6.0, hideUnrated = false)
        assertTrue(show.passes(v("a", imdb = 6.0)))
        assertFalse(show.passes(v("b", imdb = 5.9)))
        assertTrue(show.passes(v("c", kp = 7.1)))
        assertFalse(show.passes(v("d", kp = 4.0)))
        assertTrue(show.passes(v("e")))                                   // unrated is kept
        assertFalse(ContentPolicy(6.0, hideUnrated = true).passes(v("e")))  // unless the profile hides it
    }

    @Test fun imdbWinsOverKinopoiskWhenBothExist() {
        assertFalse(ContentPolicy(6.0).passes(v("a", imdb = 5.0, kp = 9.0)))
    }

    @Test fun summariesSayWhatIsHidden() {
        assertEquals("Show everything", ContentPolicy().summary())
        assertEquals("Hide below 6", ContentPolicy(6.0).summary())
        assertEquals("Hide below 7 and unrated", ContentPolicy(7.0, true).summary())
    }

    @Test fun aPageThatTheFilterEmptiesDoesNotStallTheList() {
        val pages = mapOf(
            1 to listOf(v("a", imdb = 1.0), v("b", imdb = 2.0)),
            2 to listOf(v("c", imdb = 3.0)),
            3 to listOf(v("d", imdb = 8.0), v("e", imdb = 9.0)),
        )
        val policy = ContentPolicy(6.0)
        val paged = PagedTitles(keep = policy::passes) { p -> CatalogResult.Ok(VodPage(p, pages.getValue(p), 5, 2)) }
        paged.loadNext()
        assertEquals(listOf("d", "e"), paged.items.map { it.id })
        assertTrue(paged.exhausted)                                       // the portal's total (5) was reached
    }

    @Test fun sortsCarryTheCodesTheQueryExpects() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6), BrowseSort.entries.map { it.code })
        assertFalse(BrowseSort.NEWEST.needsIndex)
        assertTrue(BrowseSort.YEAR.needsIndex)
    }

    @Test fun indexRowsKeepRatingsAndThePortalOrder() {
        val item = VodItem(id = "9", name = "N", isSeries = false, isSeason = false, isEpisode = false, cmd = "c", episodeNumbers = emptyList(), year = "2020", imdbRating = 7.5, kinopoiskRating = 6.1)
        val row = TitleIndexer.entityOf(item, "10", ord = 33)
        assertEquals(7.5, row.imdbRating!!, 0.0); assertEquals(6.1, row.kinopoiskRating!!, 0.0); assertEquals(33, row.ord)
        val back = row.toVodItem()
        assertEquals(7.5, back.shownRating!!.value, 0.0); assertEquals("2020", back.year)
    }
}
