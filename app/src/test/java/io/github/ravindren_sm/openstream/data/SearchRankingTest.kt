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
