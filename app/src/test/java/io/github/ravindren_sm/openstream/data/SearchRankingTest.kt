package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.VodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankingTest {
    private fun v(id: String, name: String) =
        VodItem(id = id, name = name, isSeries = false, isSeason = false, isEpisode = false, cmd = "c", episodeNumbers = emptyList())
    private fun ch(id: String, number: String, name: String) = LiveChannel(id, number, name, null)

    @Test fun titlesStartingWithTheQueryComeFirstThenThoseContainingIt() {
        val ranked = SearchRanking.rank(listOf(v("1", "The Jerry Show"), v("2", "Jerry Maguire"), v("3", "Unrelated"), v("4", "Tom &amp; Jerry")), "jerry")
        assertEquals(listOf("2", "1", "4", "3"), ranked.map { it.id })
    }

    @Test fun matchingIgnoresCaseEntitiesAndExtraSpaces() {
        assertEquals("tom & jerry", SearchRanking.normalize("  Tom &amp;   JERRY "))
        assertEquals(listOf("4"), SearchRanking.rank(listOf(v("4", "Tom &amp; Jerry")), "tom & jerry").map { it.id })
    }

    @Test fun channelsMatchByNameOrExactNumber() {
        val all = listOf(ch("1", "5", "BBC News"), ch("2", "105", "News 24"), ch("3", "7", "Sports"), ch("4", "12", "Newsroom"))
        assertEquals(listOf("2", "4", "1"), SearchRanking.matchChannels(all, "news").map { it.id })   // starts-with first, portal order kept
        assertEquals(listOf("3"), SearchRanking.matchChannels(all, "7").map { it.id })                 // a number
        assertEquals(listOf("1"), SearchRanking.matchChannels(all, "5").map { it.id })
    }

    @Test fun serverChannelsKeepTheirOrderWithNameStartsFirstAndNoRepeats() {
        val found = listOf(ch("1", "3", "The Cartoon Hour"), ch("2", "9", "Cartoon Net"), ch("1", "3", "The Cartoon Hour"), ch("3", "20", "Cartoon Plus"))
        assertEquals(listOf("2", "3", "1"), SearchRanking.matchChannelsFromServer(found, "cartoon").map { it.id })
    }

    @Test fun theExactTitleComesBeforeLongerNamesStartingTheSame() {
        val ranked = SearchRanking.rank(listOf(v("1", "Frozen Kiss"), v("2", "Frozen Sasquatch"), v("3", "Frozen (Hindi)"), v("4", "Frozen")), "frozen")
        assertEquals(listOf("3", "4", "1", "2"), ranked.map { it.id })
    }

    @Test fun onlyTitlesWithAVisibleReasonCountAsMatches() {
        val cast = VodItem(id = "3", name = "Big", isSeries = false, isSeason = false, isEpisode = false, cmd = "c", episodeNumbers = emptyList(), actors = "Tom Hanks")
        assertTrue(SearchRanking.explains(v("1", "Ramayana: Rise of a Legend"), "ramay"))
        assertTrue(SearchRanking.explains(v("1", "Ramayana: Rise of a Legend"), "rise  RAMAYANA"))   // words in any order
        assertTrue(SearchRanking.explains(cast, "hanks"))                                          // found in the cast
        assertTrue(!SearchRanking.explains(v("2", "Behind Her Eyes"), "ramay"))                   // matched on a field the portal did not send
        // A query must begin a word in a person's name or the description, not sit in the middle of one.
        val director = VodItem(id = "4", name = "Apollo 18", isSeries = false, isSeason = false, isEpisode = false, cmd = "c", episodeNumbers = emptyList(), director = "Gonzalo Lopez-Gallego")
        assertTrue(!SearchRanking.explains(director, "lego"))
        assertTrue(SearchRanking.explains(director, "gallego"))
        assertTrue(SearchRanking.explains(director, "gonz"))
        assertEquals(null, SearchRanking.matchReason(director, "lego"))
    }

    @Test fun aSingleLetterFindsNoChannels() {
        assertTrue(SearchRanking.matchChannels(listOf(ch("1", "5", "A News")), "a").isEmpty())
    }

    @Test fun channelResultsAreCapped() {
        val many = List(100) { ch("$it", "${it + 1000}", "News $it") }
        assertEquals(SearchRanking.MAX_CHANNELS, SearchRanking.matchChannels(many, "news").size)
    }

    @Test fun anEmptyResultIsReportedAsEmpty() {
        assertTrue(SearchResults(emptyList(), emptyList(), emptyList()).isEmpty)
    }
}
