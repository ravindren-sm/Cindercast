package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.provider.LiveChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeaguesTest {
    private val now = 1_000L
    private fun ch(name: String = "SPORTS 1") = LiveChannel("1", "1", name, null)
    private fun prog(name: String, desc: String? = null, cat: String? = null, start: Long = 1_100, stop: Long = 5_000) =
        EpgProgramme(name, desc, cat, start, stop)
    private fun list(name: String, desc: String? = null, cat: String? = null, channel: String = "SPORTS 1") =
        Leagues.listings(ch(channel), listOf(prog(name, desc, cat)), now)

    private fun assertGame(league: League, name: String, desc: String? = null) {
        val l = list(name, desc).singleOrNull() ?: error("not listed: $name")
        assertEquals(league, l.league, name); assertTrue(l.game, "game: $name")
    }
    private fun assertShow(league: League, name: String, desc: String? = null, cat: String? = null) {
        val l = list(name, desc, cat).singleOrNull() ?: error("not listed: $name")
        assertEquals(league, l.league, name); assertFalse(l.game, "show: $name")
    }
    private fun assertNothing(name: String, desc: String? = null, cat: String? = null) =
        assertEquals(0, list(name, desc, cat).size, "should be empty: $name")

    @Test fun soccerGames() {
        assertGame(League.SOCCER, "Premier League Football: Arsenal v Chelsea")
        assertGame(League.SOCCER, "UEFA Champions League: Real Madrid vs Bayern")
        assertGame(League.SOCCER, "Arsenal v Chelsea", desc = "English Premier League, live from the Emirates")
        assertGame(League.SOCCER, "La Liga: Barcelona vs. Sevilla")
        assertGame(League.SOCCER, "MLS: Inter Miami at LAFC")
        assertGame(League.SOCCER, "Football: Brazil v Argentina")
        assertGame(League.SOCCER, "FA Cup: Leeds @ Spurs")
        assertGame(League.SOCCER, "Serie A: Juventus vs Milan")
    }

    @Test fun soccerShowsAndNonMatches() {
        assertShow(League.SOCCER, "Soccer Saturday")
        assertShow(League.SOCCER, "Premier League Highlights")
        assertShow(League.SOCCER, "UEFA Champions League Preview")
        assertShow(League.SOCCER, "Champions League Magazine")
        // The blurb alone does not list a talk show.
        assertNothing("The Football Show", desc = "Arsenal v Chelsea previewed, Premier League talk")
        // No league word in title or category: "Match of the Day" is not listed (documented limit)...
        assertNothing("Match of the Day", desc = "Premier League highlights")
        // ...unless its category says so.
        assertShow(League.SOCCER, "Match of the Day", cat = "Soccer")
        assertNothing("Premier League Darts: Smith v Price")
        assertNothing("Rugby: Premiership Leicester v Bath", desc = "Premier League of rugby")
        assertNothing("Premier League Snooker")
    }

    @Test fun cricket() {
        assertGame(League.CRICKET, "India v Australia: 1st Test")
        assertGame(League.CRICKET, "IPL 2026: CSK vs MI")
        assertGame(League.CRICKET, "Big Bash League: Sixers v Stars")
        assertGame(League.CRICKET, "England vs New Zealand - 2nd T20")
        assertGame(League.CRICKET, "Pakistan v Sri Lanka", desc = "ODI series, live cricket")
        assertShow(League.CRICKET, "Cricket Highlights")
        assertShow(League.CRICKET, "The Ashes Review")
        assertShow(League.CRICKET, "Cricket")
        assertNothing("Driving Test")
    }

    @Test fun formulaOne() {
        assertGame(League.FORMULA_1, "Formula 1: Las Vegas Grand Prix - Qualifying")
        assertGame(League.FORMULA_1, "F1: Monaco Grand Prix - Race")
        assertGame(League.FORMULA_1, "Practice 2", desc = "Formula 1 free practice")
        assertGame(League.FORMULA_1, "Sprint Qualifying", desc = "F1 weekend")
        assertShow(League.FORMULA_1, "F1 Pre-Race Show")
        assertShow(League.FORMULA_1, "Paddock Pass", cat = "Formula 1")
        assertShow(League.FORMULA_1, "Formula 1 Highlights")
        assertNothing("MotoGP Grand Prix")
        assertNothing("IndyCar Grand Prix of Long Beach")
        assertNothing("Superbike World Championship Grand Prix")
        assertNothing("Paddock Pass")
    }

    @Test fun collegeFootball() {
        val p = prog("College Football: Georgia Bulldogs at Alabama Crimson Tide")
        assertEquals(League.COLLEGE_FOOTBALL, Leagues.leagueOf(p))
        assertFalse(SportsLogic.isNfl(p))
        assertGame(League.COLLEGE_FOOTBALL, "College Football: Georgia Bulldogs at Alabama Crimson Tide")
        assertGame(League.COLLEGE_FOOTBALL, "NCAAF: Ohio State vs Michigan")
        assertGame(League.COLLEGE_FOOTBALL, "Texas v Oklahoma", desc = "College football rivalry")
        assertGame(League.COLLEGE_FOOTBALL, "College Football")
        assertShow(League.COLLEGE_FOOTBALL, "College GameDay")
        assertShow(League.COLLEGE_FOOTBALL, "College Football Highlights")
        assertNothing("College Basketball: Duke at UNC")
        assertNothing("NCAAB: Kansas vs Baylor")
    }

    @Test fun crossChecks() {
        val nfl = list("NFL Football: Chiefs at Bills").single()
        assertEquals(League.NFL, nfl.league); assertTrue(nfl.game)
        assertEquals(League.NFL, list("Monday Night Football").single().league)
        assertNothing("NHL Hockey: Rangers at Bruins")
        assertNothing("MLB Baseball: Yankees at Red Sox")
        assertNothing("The Lion King")
    }

    @Test fun aProgrammeBelongsToOneLeagueOnly() {
        val names = listOf(
            "NFL Football: Chiefs at Bills", "College Football: Georgia at Alabama", "Premier League: Arsenal v Chelsea",
            "India v Australia: 1st Test", "Formula 1: Monaco Grand Prix", "Football: Brazil v Argentina", "College GameDay",
            "Cricket Highlights", "NFL Live", "MLS: Seattle vs Portland",
        )
        names.forEach { n ->
            val leagues = Leagues.listings(ch(), listOf(prog(n)), now).map { it.league }
            assertTrue(leagues.size <= 1, "$n -> $leagues")
        }
        assertNull(Leagues.leagueOf(prog("The Lion King")))
    }

    @Test fun nflShowsStillNeedTheLeagueInTitle() {
        assertShow(League.NFL, "NFL Live")
        assertNothing("Sports Talk", desc = "We discuss the NFL and the Chiefs")
    }

    @Test fun channelNamedForTheLeagueFallback() {
        val p = prog("Studio Hour", start = 900)
        assertEquals(League.FORMULA_1, Leagues.listings(ch("SKY SPORTS F1 HD"), listOf(p), now).single().league)
        assertEquals(League.CRICKET, Leagues.listings(ch("Star Cricket"), listOf(p), now).single().league)
        assertEquals(League.NFL, Leagues.listings(ch("NFL Network"), listOf(p), now).single().league)
        assertEquals(0, Leagues.listings(ch("Premier Soccer HD"), listOf(p), now).size)
        assertEquals(0, Leagues.listings(ch("SKY SPORTS F1 HD"), listOf(prog("No Details Available", start = 900)), now).size)
        assertEquals(0, Leagues.listings(ch("SKY SPORTS F1 HD"), listOf(prog("Studio Hour", start = 2_000)), now).size)
    }

    @Test fun repeatedShowIsListedOnceAndGamesAreKept() {
        val ps = listOf(
            prog("Cricket Highlights", start = 1_100, stop = 2_000), prog("Cricket Highlights", start = 3_000, stop = 4_000),
            prog("India v Australia", desc = "Test cricket", start = 5_000, stop = 9_000), prog("India v Australia", desc = "Test cricket", start = 9_000, stop = 12_000),
        )
        val l = Leagues.listings(ch(), ps, now)
        assertEquals(3, l.size)
        assertEquals(2, l.count { it.game })
    }

    @Test fun leagueGroups() {
        listOf("SPORTS | SOCCER", "SPORTS | EPL EFL", "UEFA", "LALIGA", "LA LIGA TV", "SPORTS | MLS", "BUNDESLIGA", "SERIE A", "CRICKET", "IPL",
            "F1", "FORMULA 1", "SPORTS | NCAAB NCAAF", "NCAA", "SPORTS | NFL").forEach { assertTrue(Leagues.isLeagueGroup(it), it) }
        listOf("SPORTS | NHL", "SPORTS | NBA", "SPORTS | NCAAB", "ENGLISH USA LOCAL", "KIDS", "SPORTS | TENNIS", "SPORTS").forEach { assertFalse(Leagues.isLeagueGroup(it), it) }
    }

    @Test fun scanOpensLeagueGroupsAndOtherwiseDefers() {
        val all = SportsLogic.NflScan.ALL_CHANNELS
        assertEquals(all, Leagues.scan("SPORTS | SOCCER"))
        assertEquals(all, Leagues.scan("SPORTS | NCAAB NCAAF"))
        assertEquals(all, Leagues.scan("SPORTS | EPL EFL"))
        assertEquals(all, Leagues.scan("SPORTS | NFL"))
        assertEquals(all, Leagues.scan("SPORTS | ESPN+"))
        assertEquals(all, Leagues.scan("SPORTS"))
        listOf("SPORTS | NHL", "SPORTS | NBA", "SPORTS | MLB", "SPORTS | AHL OHL WHL QMJHL", "SPORTS | TENNIS", "SPORTS | GOLF", "SPORTS | RUGBY",
            "SPORTS | BOXING", "SPORTS | UFC", "SPORTS | WWE", "SPORTS | NASCAR").forEach { assertEquals(SportsLogic.NflScan.SKIP, Leagues.scan(it), it) }
        assertEquals(SportsLogic.NflScan.NETWORKS_ONLY, Leagues.scan("ENGLISH USA LOCAL"))
        assertEquals(SportsLogic.NflScan.SKIP, Leagues.scan("HINDI NEWS"))
    }

    @Test fun deadGroupGuardStopsAfterEightEmptyGuides() {
        val g = DeadGroupGuard()
        repeat(7) { assertFalse(g.feed(emptyList(), "LIVE SOCCER $it")) }
        assertTrue(g.feed(listOf(prog("No Details Available")), "LIVE SOCCER 8"))
    }

    @Test fun deadGroupGuardIgnoresUnreadAndStaysOffAfterRealGuide() {
        val g = DeadGroupGuard()
        repeat(20) { assertFalse(g.feed(null, "X")) }
        repeat(3) { assertFalse(g.feed(emptyList(), "X")) }
        assertFalse(g.feed(listOf(prog("Arsenal v Chelsea")), "X"))
        repeat(20) { assertFalse(g.feed(emptyList(), "X")) }
    }

    @Test fun deadGroupGuardTreatsChannelNameAsPlaceholder() {
        val g = DeadGroupGuard(limit = 2)
        assertFalse(g.feed(listOf(prog("LIVE SOCCER 01")), "LIVE SOCCER 01"))
        assertTrue(g.feed(listOf(prog("TBA")), "LIVE SOCCER 02"))
    }
}
