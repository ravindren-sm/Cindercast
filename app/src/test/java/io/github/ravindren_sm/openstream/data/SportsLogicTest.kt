package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.provider.LiveChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SportsLogicTest {
    @Test fun sportsGroupsAndCategoriesAreRecognisedByWholeWords() {
        listOf("SPORTS | SKY SPORTS+", "SPORTS | MLS", "SPORTS | NCAAB NCAAF", "NFL SUNDAY TICKET", "ESPN+", "CRICKET", "UFC PPV")
            .forEach { assertTrue(SportsLogic.isSports(it), it) }
        listOf("ENGLISH NEWS", "KIDS", "HINDI MOVIES | CAM", "NETFLIX | HINDI SERIES", "PASSPORT", "SNOW")
            .forEach { assertFalse(SportsLogic.isSports(it), it) }
    }

    @Test fun nflScanSkipsGroupsNamedForOtherSportsAndChecksOnlyNetworksInGeneralGroups() {
        assertEquals(SportsLogic.NflScan.ALL_CHANNELS, SportsLogic.nflScan("SPORTS | NFL"))
        assertEquals(SportsLogic.NflScan.ALL_CHANNELS, SportsLogic.nflScan("SPORTS | ESPN+"))
        assertEquals(SportsLogic.NflScan.ALL_CHANNELS, SportsLogic.nflScan("SPORTS | PARAMOUNT+"))
        assertEquals(SportsLogic.NflScan.SKIP, SportsLogic.nflScan("SPORTS | SOCCER"))
        assertEquals(SportsLogic.NflScan.SKIP, SportsLogic.nflScan("SPORTS | NCAAB NCAAF"))
        assertEquals(SportsLogic.NflScan.SKIP, SportsLogic.nflScan("SPORTS | EPL EFL"))
        assertEquals(SportsLogic.NflScan.NETWORKS_ONLY, SportsLogic.nflScan("ENGLISH USA LOCAL"))
        assertEquals(SportsLogic.NflScan.SKIP, SportsLogic.nflScan("HINDI NEWS"))
        assertEquals(SportsLogic.NflScan.NETWORKS_ONLY, SportsLogic.nflScan("ENGLISH"))
        assertEquals(SportsLogic.NflScan.SKIP, SportsLogic.nflScan("ENGLISH 24X7 MOVIES"))
        assertEquals(SportsLogic.NflScan.SKIP, SportsLogic.nflScan("ENGLISH NEWS"))
        assertEquals(SportsLogic.NflScan.ALL_CHANNELS, SportsLogic.nflScan("SPORTS"))
        assertEquals(SportsLogic.NflScan.SKIP, SportsLogic.nflScan("SPORTS | AHL OHL WHL QMJHL"))
        assertTrue(SportsLogic.isNetworkChannel("FOX 5 NEW YORK HD"))
        assertFalse(SportsLogic.isNetworkChannel("FOXTEL MOVIES"))
    }

    @Test fun nflGamesAreFoundAndOtherLeaguesAreNot() {
        assertTrue(SportsLogic.isNflText("NFL Football: Kansas City Chiefs at Buffalo Bills"))
        assertTrue(SportsLogic.isNflText("Chiefs at Bills"))
        assertTrue(SportsLogic.isNflText("Sunday Night Football: Packers vs. Bears"))
        assertTrue(SportsLogic.isNflText("NFL RedZone"))
        assertTrue(SportsLogic.isNflText("Football Night in America: Cowboys"))
        assertTrue(SportsLogic.isNflText("Monday Night Football"))
        assertTrue(SportsLogic.isGame(p("Monday Night Football", 0, 1)))
        assertFalse(SportsLogic.isGame(p("Monday Night Countdown", 0, 1)))
        assertTrue(SportsLogic.isGame(p("Manningcast | MNF with Peyton and Eli: Bengals @ Commanders", 0, 1)))
        assertFalse(SportsLogic.isGame(p("Bengals Weekly", 0, 1)))
        assertFalse(SportsLogic.isNflText("NHL Hockey: Winnipeg Jets at Florida Panthers"))
        assertFalse(SportsLogic.isNflText("Jets at Panthers"))
        assertFalse(SportsLogic.isNflText("MLB Baseball: Giants at Cardinals"))
        assertFalse(SportsLogic.isNflText("College Football: Georgia Bulldogs at Alabama Crimson Tide"))
        assertFalse(SportsLogic.isNflText("Premier League Football: Arsenal v Chelsea"))
        assertFalse(SportsLogic.isNflText("The Lion King"))
    }

    @Test fun qualityCopiesShareOneKey() {
        assertEquals(SportsLogic.channelKey("ESPN HD"), SportsLogic.channelKey("ESPN 4K"))
        assertEquals(SportsLogic.channelKey("ESPN (RAW)"), SportsLogic.channelKey("espn fhd"))
        assertTrue(SportsLogic.channelKey("ESPN") != SportsLogic.channelKey("ESPN2"))
    }

    private fun ch(id: String, name: String, number: String = id) = LiveChannel(id, number, name, null)
    private fun p(name: String, start: Long, stop: Long) = EpgProgramme(name, null, null, start, stop)

    @Test fun aRepeatedShowIsListedOnceButEveryGameIsKept() {
        val rz = ch("9", "NFL: REDZONE 4K")
        val filler = (0..2).map { k -> EpgProgramme("Every Sunday Afternoon", "NFL RedZone", null, 1_000L + k * 100, 1_100L + k * 100) }
        val shown = SportsLogic.listings(rz, filler, nowSec = 1_050)
        assertEquals(1, shown.size); assertEquals(1_000L, shown[0].programme?.startSec)
        val fox = ch("2", "FOX")
        val games = listOf(p("Bills at Chiefs", 1_000, 2_000), p("Bills at Chiefs", 5_000, 6_000))   // live, then a replay
        assertEquals(2, SportsLogic.listings(fox, games, nowSec = 1_500).size)
    }

    @Test fun listingsShowTheLiveGameAndLaterGamesInOrder() {
        val now = 10_000L
        val fox = ch("1", "FOX 5 HD")
        val cbs = ch("2", "CBS 2 HD")
        val nflNet = ch("3", "NFL NETWORK")
        val l1 = SportsLogic.listings(fox, listOf(p("NFL Football: Chiefs at Bills", now - 600, now + 9000), p("Local News", now + 9000, now + 10800)), now)
        val l2 = SportsLogic.listings(cbs, listOf(p("Local News", now - 100, now + 3000), p("Packers at Bears", now + 3000, now + 14000)), now)
        val l3 = SportsLogic.listings(nflNet, listOf(p("Good Morning Football", now - 100, now + 3000)), now)
        val team = SportsLogic.listings(ch("5", "NFL: BUFFALO BILLS 4K"), listOf(p("No details available", now - 100, now + 3000)), now)
        assertTrue(team.isEmpty())
        val none = SportsLogic.listings(ch("4", "ABC 7"), listOf(p("Jeopardy!", now - 10, now + 100)), now)
        assertEquals(1, l1.size); assertTrue(l1[0].live)
        assertEquals(1, l2.size); assertFalse(l2[0].live)
        assertEquals(1, l3.size); assertTrue(l3[0].live)
        assertTrue(none.isEmpty())
        // Games (the live one, then the later one) before the NFL Network show.
        val ordered = SportsLogic.order(l2 + l3 + l1)
        assertEquals(listOf("1", "2", "3"), ordered.map { it.channel.id })
        assertTrue(ordered[0].game && ordered[1].game && !ordered[2].game)
    }
}
