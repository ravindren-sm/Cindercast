package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.provider.LiveChannel

/**
 * Rules for the Sports tab: which channel groups and on-demand categories are about sport, and which guide entries are NFL
 * games. Plain Kotlin on titles only (the portal has no sport flag), tested without Android.
 *
 * Words are matched whole and case-insensitively, so "SN+" matches "SPORTS | SN+" but "NBA" does not match "NBATV" unless
 * listed. Portal titles look like "SPORTS | SKY SPORTS+", "ENGLISH USA LOCAL", "NETFLIX | HINDI MOVIES".
 */
object SportsLogic {

    /** Group or category titles that are about sport. */
    private val SPORT_WORDS = listOf(
        "SPORT", "SPORTS", "NFL", "NBA", "NHL", "MLB", "MLS", "NCAA", "NCAAF", "NCAAB", "UFC", "WWE", "AEW", "PPV", "ESPN", "ESPN+",
        "DAZN", "TSN", "TSN+", "SN", "SN+", "SPORTSNET", "BEIN", "CRICKET", "GOLF", "TENNIS", "SOCCER", "FOOTBALL", "RUGBY", "F1",
        "FORMULA", "NASCAR", "MOTOGP", "RACING", "BOXING", "WRESTLING", "EPL", "EFL", "UEFA", "LALIGA", "BUNDESLIGA", "SERIE A",
        "OLYMPICS", "AHL", "OHL", "WHL", "CFL", "PGA", "ATP", "WTA", "IPL",
    )

    /** Sports groups whose title names a sport other than (American) football: never worth scanning for NFL games. */
    private val NOT_NFL_WORDS = listOf(
        "SOCCER", "TENNIS", "GOLF", "CRICKET", "RUGBY", "UEFA", "EPL", "EFL", "LALIGA", "BUNDESLIGA", "MLS", "NHL", "NBA", "MLB",
        "AHL", "OHL", "WHL", "NCAAB", "F1", "FORMULA", "NASCAR", "MOTOGP", "RACING", "BOXING", "UFC", "WWE", "AEW", "WRESTLING",
        "PGA", "ATP", "WTA", "IPL",
    )

    /**
     * General groups that carry the US and Canadian networks showing most NFL games (FOX, CBS, NBC, ABC affiliates). Only the
     * channels whose names look like those networks are checked, see [NETWORK_WORDS].
     */
    private val NETWORK_GROUP_WORDS = listOf("USA", "US", "LOCAL", "LOCALS", "AMERICAN", "CANADA", "CANADIAN", "ENGLISH")

    /** General groups that never carry live games (film, music and news channels, 24/7 loops). */
    private val NO_GAMES_GROUP_WORDS = listOf("MOVIES", "MOVIE", "MUSIC", "SERIES", "24X7", "24/7", "NEWS", "RELIGIOUS", "KIDS")

    /** Channel names worth checking inside [NETWORK_GROUP_WORDS] groups. */
    private val NETWORK_WORDS = listOf("FOX", "CBS", "NBC", "ABC", "ESPN", "ESPN2", "NFL", "CTV", "TSN", "SN", "SPORTSNET", "PRIME", "NETFLIX", "PEACOCK", "PARAMOUNT")

    private fun words(text: String): List<String> = text.uppercase().split(Regex("[^A-Z0-9+]+")).filter { it.isNotEmpty() }

    private fun hasAny(text: String, list: List<String>): Boolean {
        val w = words(text)
        val upper = " " + w.joinToString(" ") + " "
        return list.any { k -> if (' ' in k) upper.contains(" $k ") else k in w }
    }

    /** True for a channel group or on-demand category about sport. */
    fun isSports(title: String): Boolean = hasAny(title, SPORT_WORDS)

    /** How a group is used by the NFL search. */
    enum class NflScan { ALL_CHANNELS, NETWORKS_ONLY, SKIP }

    fun nflScan(groupTitle: String): NflScan = when {
        hasAny(groupTitle, listOf("NFL")) -> NflScan.ALL_CHANNELS
        isSports(groupTitle) && !hasAny(groupTitle, NOT_NFL_WORDS) -> NflScan.ALL_CHANNELS
        hasAny(groupTitle, NETWORK_GROUP_WORDS) && !hasAny(groupTitle, NO_GAMES_GROUP_WORDS) -> NflScan.NETWORKS_ONLY
        else -> NflScan.SKIP
    }

    /** In a NETWORKS_ONLY group: does the channel look like a network that shows NFL games? */
    fun isNetworkChannel(name: String): Boolean = hasAny(name, NETWORK_WORDS)

    /** Quality and copy markers that make several listings of one channel ("ESPN HD", "ESPN 4K", "ESPN (RAW)"). */
    private val QUALITY = setOf("4K", "UHD", "FHD", "HD", "SD", "HEVC", "H265", "RAW", "BACKUP", "60FPS", "50FPS")

    /** One key for every quality copy of a channel, so the NFL search asks for its guide once. */
    fun channelKey(name: String): String = words(name).filter { it !in QUALITY }.joinToString(" ")

    /** Channels whose own name says NFL (NFL Network, NFL RedZone, an "NFL Sunday Ticket" feed) are listed whatever is on. */
    fun isNflChannel(name: String): Boolean = hasAny(name, listOf("NFL", "REDZONE"))

    /** The 32 teams' nicknames. Several are shared with teams in other leagues, see [AMBIGUOUS]. */
    private val TEAMS = listOf(
        "Bills", "Dolphins", "Patriots", "Jets", "Ravens", "Bengals", "Browns", "Steelers", "Texans", "Colts", "Jaguars", "Titans",
        "Broncos", "Chiefs", "Raiders", "Chargers", "Cowboys", "Giants", "Eagles", "Commanders", "Bears", "Lions", "Packers",
        "VIK" + "INGS", "Falcons", "Panthers", "Saints", "Buccaneers", "Cardinals", "Rams", "49ers", "Seahawks",
    ).map { it.uppercase() }   // one name is written in two parts on purpose; keep it that way

    /** Nicknames also used in the NHL or MLB (Winnipeg Jets, Florida Panthers, San Francisco Giants, St. Louis Cardinals...). */
    private val AMBIGUOUS = setOf("JETS", "PANTHERS", "GIANTS", "CARDINALS", "RAMS", "TITANS", "EAGLES", "LIONS", "BEARS")

    /** Words that put a programme in another sport or in college football. */
    private val OTHER_SPORT = listOf("NHL", "HOCKEY", "MLB", "BASEBALL", "NBA", "BASKETBALL", "NCAA", "NCAAF", "COLLEGE", "CFL", "SOCCER", "MLS", "WNBA", "AHL", "OHL", "WHL")

    /**
     * True when a guide entry is an NFL game or NFL show. It counts when its text says NFL, or names two NFL teams, or names
     * one unambiguous team together with "football"; never when it names another league or college football.
     */
    fun isNfl(p: EpgProgramme): Boolean = isNflText(listOfNotNull(p.name, p.description, p.category).joinToString(" "))

    fun isNflText(text: String): Boolean {
        val w = words(text)
        if (w.isEmpty()) return false
        if (OTHER_SPORT.any { it in w }) return false
        if ("NFL" in w || "REDZONE" in w || primeTime(w)) return true
        val teams = TEAMS.filter { it in w }.toSet()
        if (teams.size >= 2 && teams.any { it !in AMBIGUOUS }) return true
        if (teams.size >= 2 && "FOOTBALL" in w) return true
        return teams.any { it !in AMBIGUOUS } && "FOOTBALL" in w
    }

    /** One line of the NFL results: [programme] on [channel], playing now or later; [game] when it is a match, not a show about the league. */
    class NflListing(val channel: LiveChannel, val programme: EpgProgramme?, val live: Boolean, val game: Boolean = false)

    /** A game, not a show about the NFL: it names two teams, or one team with "at" or "vs" ("Bills at Chiefs", "Packers vs. Bears"). */
    /** The prime-time game broadcasts, often listed without team names ("Monday Night Football", "TNF"). */
    private fun primeTime(w: List<String>): Boolean {
        val t = " " + w.joinToString(" ") + " "
        return listOf(" MONDAY NIGHT FOOTBALL ", " THURSDAY NIGHT FOOTBALL ", " SUNDAY NIGHT FOOTBALL ", " MNF ", " TNF ", " SNF ").any { it in t }
    }

    fun isGame(p: EpgProgramme): Boolean {
        val w = words(p.name)
        // A pre-game or post-game show names the broadcast too; those words make it a show, not the game.
        val showWords = listOf("PREGAME", "POSTGAME", "COUNTDOWN", "KICKOFF", "PREVIEW", "RECAP", "HIGHLIGHTS", "REVIEW", "WEEKLY", "LIVE", "TONIGHT")
        if (primeTime(w) && showWords.none { it in w }) return true
        // ESPN and others list the match itself as plain "NFL Football" when the guide has no team names; "NFL Football Live",
        // "NFL Football Countdown" and the like stay shows through [showWords].
        if (" NFL FOOTBALL " in " " + w.joinToString(" ") + " " && showWords.none { it in w }) return true
        val teams = TEAMS.count { it in w }
        return teams >= 2 || (teams >= 1 && (w.contains("AT") || w.contains("VS") || w.contains("V")))
    }

    /**
     * The NFL listings among [programmes] of [channel] at [nowSec]: the game on now, and games later in the guide. A channel
     * named for the NFL is listed with whatever is on now even when no game is.
     */
    fun listings(channel: LiveChannel, programmes: List<EpgProgramme>, nowSec: Long): List<NflListing> {
        val current = programmes.filter { it.stopSec > nowSec }.sortedBy { it.startSec }
        // A show repeated through the guide (RedZone's "Every Sunday Afternoon" filler, a magazine show twice a day) is listed
        // once, at its next showing; each game is kept.
        val games = current.filter(::isNfl).map { NflListing(channel, it, live = it.startSec <= nowSec, game = isGame(it)) }
            .distinctBy { if (it.game) "game ${it.programme?.startSec}" else "show " + words(it.programme?.name.orEmpty()).joinToString(" ") }
        if (games.isNotEmpty()) return games
        // A channel named for the NFL (NFL Network, RedZone, a team's channel) is listed with what is on now, but only when the
        // guide says something: team channels show "No details available" all week until a game is on.
        val onNow = current.firstOrNull { it.startSec <= nowSec }
        if (isNflChannel(channel.name) && onNow != null && !isPlaceholder(onNow.name, channel.name)) return listOf(NflListing(channel, onNow, live = true))
        return emptyList()
    }

    /** Guide filler that says nothing about the programme ("No details available", "TBA", the channel's own name). */
    fun isPlaceholder(title: String, channelName: String): Boolean {
        val t = words(title).joinToString(" ")
        return t.isEmpty() || t == channelKey(channelName) || listOf("NO DETAILS AVAILABLE", "NO INFORMATION", "NO INFO", "NO PROGRAMME INFORMATION",
            "NO PROGRAM INFORMATION", "TBA", "TBD", "TO BE ANNOUNCED", "N A", "OFF AIR", "OFFLINE", "NO EVENT", "NO EVENT SCHEDULED", "COMING SOON").any { t == it }
    }

    /** Games before shows; within each, live first, then by start time. One channel's programme listed once. */
    fun order(listings: List<NflListing>): List<NflListing> =
        listings.distinctBy { it.channel.id to (it.programme?.startSec ?: 0L) }
            .sortedWith(compareBy<NflListing>({ !it.game }, { !it.live }, { it.programme?.startSec ?: 0L }, { it.channel.number.toIntOrNull() ?: Int.MAX_VALUE }))
}
