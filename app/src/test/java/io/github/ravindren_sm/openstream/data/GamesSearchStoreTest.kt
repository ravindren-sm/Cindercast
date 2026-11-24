package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.provider.LiveChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GamesSearchStoreTest {
    @Test fun savedListingsComeBackWithChannelProgrammeAndGameFlag() {
        val ch = LiveChannel("12", "2823", "FOX 5 HD", "ffrt local/ch/12")
        val l = SportsLogic.NflListing(ch, EpgProgramme("Bengals at Commanders", null, null, 1000, 5000), live = false, game = true)
        val show = SportsLogic.NflListing(LiveChannel("3", "503", "ESPN", null), EpgProgramme("NFL Live", null, null, 900, 2000), live = true)
        val (back, at) = GamesSearch.decode(GamesSearch.encode(listOf(l, show), 42L))
        assertEquals(42L, at)
        assertEquals(listOf("12", "3"), back.map { it.channel.id })
        assertEquals("ffrt local/ch/12", back[0].channel.cmd)
        assertEquals("Bengals at Commanders", back[0].programme?.name)
        assertEquals(5000L, back[0].programme?.stopSec)
        assertTrue(back[0].game)
        assertEquals(null, back[1].channel.cmd)
    }

    @Test fun anUnreadableFileGivesNothing() {
        assertEquals(0, GamesSearch.decode("not json").first.size)
        assertEquals(0, GamesSearch.decode("").first.size)
    }

    @Test fun theLeagueSurvivesTheRoundTrip() {
        val ch = LiveChannel("1", "10", "SKY F1", null)
        League.entries.forEach { lg ->
            val l = SportsLogic.NflListing(ch, EpgProgramme("Las Vegas Grand Prix", null, null, 1000, 5000), live = false, game = true, league = lg)
            assertEquals(lg, GamesSearch.decode(GamesSearch.encode(listOf(l), 1L)).first.single().league)
        }
    }

    @Test fun anOldFileWithoutLeagueIsNflAndAnUnknownLeagueIsDropped() {
        val old = """{"savedAt":5,"listings":[{"id":"1","number":"2","name":"FOX","game":true,"title":"Bills at Chiefs","start":1,"stop":2}]}"""
        assertEquals(League.NFL, GamesSearch.decode(old).first.single().league)
        val future = """{"savedAt":5,"listings":[{"id":"1","name":"A","league":"CURLING"},{"id":"2","name":"B","league":"CRICKET"}]}"""
        assertEquals(listOf("2"), GamesSearch.decode(future).first.map { it.channel.id })
    }

    @Test fun theOldNameStillWorks() {
        assertEquals(0, NflSearch.decode("x").first.size)
    }

    @Test fun forLeagueFilters() {
        val ch = LiveChannel("1", "1", "X", null)
        val a = SportsLogic.NflListing(ch, null, false, false, League.SOCCER)
        val b = SportsLogic.NflListing(ch, null, false, false, League.CRICKET)
        assertEquals(listOf(a), GamesSearch.forLeague(listOf(a, b), League.SOCCER))
        assertEquals(listOf(b), Leagues.forLeague(listOf(a, b), League.CRICKET))
    }
}
