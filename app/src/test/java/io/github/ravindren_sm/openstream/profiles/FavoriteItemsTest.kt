package io.github.ravindren_sm.openstream.profiles

import io.github.ravindren_sm.openstream.data.PagedChannels
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.VodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteItemsTest {
    private fun vod(series: Boolean) = VodItem(
        id = "42", name = "Title", isSeries = series, isSeason = false, isEpisode = false, cmd = "cmd-42", episodeNumbers = emptyList(),
        year = "1999", description = "Plot", actors = "A, B", director = "D", genres = "Drama", imdbRating = 7.5, kinopoiskRating = 6.0,
        age = "PG", posterUri = "/p/42.png",
    )

    @Test fun aMovieSurvivesTheRoundTrip() {
        val back = FavoriteItems.toVod(FavoriteItems.ofVod(vod(false)))
        assertEquals("42", back.id); assertEquals("Title", back.name); assertEquals(false, back.isSeries)
        assertEquals("cmd-42", back.playableCmd); assertEquals("1999", back.year); assertEquals("Plot", back.description)
        assertEquals("A, B", back.actors); assertEquals("D", back.director); assertEquals("Drama", back.genres)
        assertEquals("PG", back.age); assertEquals("/p/42.png", back.posterUri)
        assertEquals(7.5, back.imdbRating!!, 0.0); assertEquals(6.0, back.kinopoiskRating!!, 0.0)
    }

    @Test fun aSeriesEntryIsAShowAndComesBackAsOne() {
        val f = FavoriteItems.ofVod(vod(true))
        assertEquals(Kinds.SHOW, f.kind)
        assertEquals(true, FavoriteItems.toVod(f).isSeries)
        assertEquals(Kinds.MOVIE, FavoriteItems.kindOf(vod(false)))
    }

    @Test fun aChannelKeepsItsNumberAndCommand() {
        val back = FavoriteItems.toChannel(FavoriteItems.ofChannel(LiveChannel("7", "105", "News", "ffrt://x")))
        assertEquals("7", back.id); assertEquals("105", back.number); assertEquals("News", back.name); assertEquals("ffrt://x", back.cmd)
    }

    @Test fun channelsComeInNumberOrderWithUnnumberedLast() {
        val rows = listOf(
            FavoriteItems.ofChannel(LiveChannel("1", "20", "B", null)),
            FavoriteItems.ofChannel(LiveChannel("2", "3", "A", null)),
            FavoriteItems.ofChannel(LiveChannel("3", "", "Z", null)),
            FavoriteItems.ofChannel(LiveChannel("4", "100", "C", null)),
            FavoriteItems.ofVod(vod(false)),
        )
        assertEquals(listOf("2", "1", "4", "3"), FavoriteItems.channelsInOrder(rows).map { it.refId })
    }

    @Test fun aFixedChannelListIsCompleteAtOnce() {
        val list = PagedChannels.fixed(listOf(LiveChannel("1", "1", "A", null), LiveChannel("2", "2", "B", null)))
        assertEquals(2, list.items.size)
        assertTrue(list.exhausted)
    }
}
