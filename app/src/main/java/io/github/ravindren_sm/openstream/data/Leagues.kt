package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.provider.LiveChannel

/** The leagues the Sports tab searches for in one pass. [label] is the name shown to the viewer. */
enum class League(val label: String) {
    NFL("NFL"),
    COLLEGE_FOOTBALL("College football"),
    SOCCER("Soccer"),
    CRICKET("Cricket"),
    FORMULA_1("Formula 1"),
}

/**
 * Per-league rules on guide entries, the generalisation of [SportsLogic]'s NFL rules. A programme belongs to at most one
 * league: [leagueOf] checks the NFL first (so college football, which [SportsLogic.isNflText] rejects, never lands there), then
 * college football, Formula 1, cricket and soccer, and returns the first that matches.
 *
 * Each league has: a text test over name, description and category ("is this about the league"); a game test on the NAME only
 * (a match "X v Y", "X vs Y", "X at Y", "X @ Y", or for Formula 1 a session); and show words that turn a programme into a show.
 * A non-game programme is listed only when its TITLE or CATEGORY says the league, so a talk show whose blurb mentions the league
 * is not listed, while a game may be recognised from its description ("Arsenal v Chelsea" with "Premier League" in the blurb).
 *
 * Known limits: "Match of the Day" says no league in its title, so it is not listed unless its category does. "LIVE" is not a
 * show word (many games are titled "LIVE: A v B"); "Soccer Saturday" is a show, listed because its title says soccer.
 */
object Leagues {

    // ---- Keyword lists (matched as whole words or word sequences, case-insensitive) ----

    /** Show words common to all leagues: studio, preview and replay programmes are never the game itself. */
    private val SHOW_WORDS = listOf(
        "PREVIEW", "REVIEW", "HIGHLIGHTS", "RECAP", "MAGAZINE", "ROUNDUP", "REPLAY", "PREGAME", "POSTGAME", "COUNTDOWN",
        "SHOW", "TALK", "WEEKLY", "GAMEDAY", "PADDOCK", "ANALYSIS", "REWIND", "DOCUMENTARY",
    )

    /** Multi-word show markers ("PRE-RACE" arrives as PRE RACE once punctuation is split). */
    private val SHOW_PHRASES = listOf("PRE RACE", "POST RACE", "PRE MATCH", "POST MATCH", "MATCH OF THE DAY", "PRESS CONFERENCE")

    /** Soccer: league and competition names, plus words that only soccer uses. */
    private val SOCCER_WORDS = listOf(
        "SOCCER", "PREMIER LEAGUE", "EPL", "CHAMPIONS LEAGUE", "EUROPA LEAGUE", "LA LIGA", "LALIGA", "BUNDESLIGA", "SERIE A",
        "LIGUE 1", "MLS", "FA CUP", "EFL", "CARABAO", "UEFA", "FIFA", "LIGA MX", "EREDIVISIE", "COPA",
        "WORLD CUP QUALIFIER", "WORLD CUP QUALIFIERS",
    )

    /** Words that rule soccer out: other sports sharing a name ("Premier League Darts") and American football. */
    private val NOT_SOCCER_WORDS = listOf(
        "NFL", "NCAA", "NCAAF", "COLLEGE", "AMERICAN", "CFL", "AFL", "DARTS", "RUGBY", "SNOOKER", "CRICKET", "HOCKEY", "BASKETBALL",
        "BASEBALL", "NHL", "NBA", "MLB", "GOLF", "TENNIS", "FORMULA", "F1", "MOTOGP",
    )

    private val CRICKET_WORDS = listOf(
        "CRICKET", "IPL", "BIG BASH", "BBL", "T20", "T20I", "ODI", "TEST MATCH", "ASHES", "THE HUNDRED", "PSL", "CPL", "SA20",
    )

    /** A cricket fixture format; with a match pattern in the name it is cricket even with no other cricket word ("1st Test"). */
    private val CRICKET_FORMATS = listOf("TEST", "T20", "T20I", "ODI")
    private val NOT_CRICKET_WORDS = listOf("NFL", "NCAA", "COLLEGE", "SOCCER", "RUGBY", "HOCKEY", "BASEBALL")

    private val F1_WORDS = listOf("FORMULA 1", "FORMULA ONE", "F1", "GRAND PRIX")

    /** Other motor series that also say "Grand Prix". */
    private val NOT_F1_WORDS = listOf("MOTOGP", "MOTO2", "MOTO3", "INDYCAR", "NASCAR", "SUPERBIKE", "SUPERBIKES", "WSBK", "SUPERCROSS", "KARTING")

    /** What a Formula 1 session is called. */
    private val F1_SESSION_WORDS = listOf("GRAND PRIX", "PRACTICE", "QUALIFYING", "SPRINT", "RACE")

    private val COLLEGE_WORDS = listOf("COLLEGE FOOTBALL", "NCAAF", "NCAA FOOTBALL", "CFB", "COLLEGE GAMEDAY")
    private val NOT_COLLEGE_WORDS = listOf("BASKETBALL", "NCAAB", "BASEBALL", "HOCKEY", "SOCCER", "LACROSSE", "VOLLEYBALL", "SOFTBALL")

    /** A college football title made only of these words is the game itself (as "NFL Football" is for the NFL). */
    private val COLLEGE_PLAIN = setOf("COLLEGE", "FOOTBALL", "NCAA", "NCAAF", "CFB", "THE")

    // ---- Helpers ----

    /** "X v Y", "X vs Y", "X vs. Y", "X versus Y", "X at Y", "X @ Y": something on both sides. */
    private val MATCH_PATTERN = Regex("""(?i)\S.*\s(v\.?|vs\.?|versus|at)\s.*\S|\S.*\s?@\s?.*\S""")

    private fun fullText(p: EpgProgramme) = listOfNotNull(p.name, p.description, p.category).joinToString(" ")
    private fun titleText(p: EpgProgramme) = listOfNotNull(p.name, p.category).joinToString(" ")
    private fun isMatchName(name: String) = MATCH_PATTERN.containsMatchIn(name)

    private fun isShowName(name: String): Boolean {
        val w = SportsLogic.words(name)
        val joined = " " + w.joinToString(" ") + " "
        return SHOW_WORDS.any { it in w } || SHOW_PHRASES.any { " $it " in joined }
    }

    private fun any(text: String, list: List<String>) = SportsLogic.hasAny(text, list)

    // ---- League text tests ----

    private fun isCollegeText(t: String): Boolean {
        if (any(t, NOT_COLLEGE_WORDS)) return false
        if (any(t, COLLEGE_WORDS)) return true
        val w = SportsLogic.words(t)
        return "FOOTBALL" in w && ("COLLEGE" in w || "NCAA" in w)
    }

    private fun isF1Text(t: String) = any(t, F1_WORDS) && !any(t, NOT_F1_WORDS)

    private fun isCricketText(t: String, name: String): Boolean {
        if (any(t, NOT_CRICKET_WORDS)) return false
        if (any(t, CRICKET_WORDS)) return true
        // "India v Australia: 1st Test": the format word with a match pattern in the title.
        return isMatchName(name) && any(name, CRICKET_FORMATS)
    }

    private fun isSoccerText(t: String, name: String): Boolean {
        if (any(t, NOT_SOCCER_WORDS)) return false
        if (any(t, SOCCER_WORDS)) return true
        // Plain "Football: Brazil v Argentina" is soccer only when nothing says American or college football (checked above).
        return any(t, listOf("FOOTBALL")) && isMatchName(name)
    }

    private fun textMatches(league: League, p: EpgProgramme, t: String): Boolean = when (league) {
        League.NFL -> SportsLogic.isNfl(p)
        League.COLLEGE_FOOTBALL -> isCollegeText(t)
        League.FORMULA_1 -> isF1Text(t)
        League.CRICKET -> isCricketText(t, p.name)
        League.SOCCER -> isSoccerText(t, p.name)
    }

    /** True when [p] is a match or session (not a show) of [league]; the name decides. */
    private fun isGameOf(league: League, p: EpgProgramme): Boolean {
        if (league == League.NFL) return SportsLogic.isGame(p)
        if (isShowName(p.name)) return false
        val w = SportsLogic.words(p.name)
        return when (league) {
            League.FORMULA_1 -> {
                val joined = " " + w.joinToString(" ") + " "
                F1_SESSION_WORDS.any { " $it " in joined }
            }
            League.COLLEGE_FOOTBALL -> isMatchName(p.name) || (w.isNotEmpty() && w.all { it in COLLEGE_PLAIN })
            else -> isMatchName(p.name)
        }
    }

    /** The league [p] belongs to, or null. One answer at most, so no programme is in two leagues. */
    fun leagueOf(p: EpgProgramme): League? {
        if (SportsLogic.isNfl(p)) return League.NFL
        val t = fullText(p)
        // Order matters only for text two leagues could claim: the rarer sport goes first.
        return listOf(League.COLLEGE_FOOTBALL, League.FORMULA_1, League.CRICKET, League.SOCCER).firstOrNull { textMatches(it, p, t) }
    }

    /** A programme of [league] worth listing: a game (recognised from any text) or a show whose title or category names the league. */
    private fun listable(league: League, p: EpgProgramme): Boolean =
        isGameOf(league, p) || textMatches(league, p, titleText(p))

    private fun channelFallbackLeague(name: String): League? = when {
        SportsLogic.isNflChannel(name) -> League.NFL
        any(name, listOf("F1", "FORMULA")) -> League.FORMULA_1
        any(name, listOf("CRICKET")) -> League.CRICKET
        else -> null
    }

    /**
     * The listings of every league among [programmes] of [channel] at [nowSec]. NFL is delegated to [SportsLogic.listings]
     * (its dedupe, filler and placeholder rules); the others use the rules above. A channel named for the league (NFL, F1,
     * cricket) is listed with whatever is on now when nothing matched, unless that is placeholder filler.
     */
    fun listings(channel: LiveChannel, programmes: List<EpgProgramme>, nowSec: Long): List<SportsLogic.NflListing> {
        val current = programmes.filter { it.stopSec > nowSec }.sortedBy { it.startSec }
        val nfl = SportsLogic.listings(channel, programmes, nowSec)
        // SportsLogic lists an NFL-named channel's current programme when no game matched; that fallback is dropped here when
        // another league did find something on the channel.
        val nflReal = nfl.filter { l -> l.programme?.let { SportsLogic.isNfl(it) } == true }
        val others = ArrayList<SportsLogic.NflListing>()
        for (league in listOf(League.COLLEGE_FOOTBALL, League.FORMULA_1, League.CRICKET, League.SOCCER)) {
            current.filter { leagueOf(it) == league && listable(league, it) }
                .map { SportsLogic.NflListing(channel, it, live = it.startSec <= nowSec, game = isGameOf(league, it), league = league) }
                .distinctBy { if (it.game) "game ${it.programme?.startSec}" else "show " + SportsLogic.words(it.programme?.name.orEmpty()).joinToString(" ") }
                .let { others += it }
        }
        if (nflReal.isNotEmpty() || others.isNotEmpty()) return nflReal + others
        if (nfl.isNotEmpty()) return nfl
        val fallback = channelFallbackLeague(channel.name) ?: return emptyList()
        val onNow = current.firstOrNull { it.startSec <= nowSec } ?: return emptyList()
        if (SportsLogic.isPlaceholder(onNow.name, channel.name)) return emptyList()
        return listOf(SportsLogic.NflListing(channel, onNow, live = true, league = fallback))
    }

    /** Filters [list] to one league. */
    fun forLeague(list: List<SportsLogic.NflListing>, league: League): List<SportsLogic.NflListing> = list.filter { it.league == league }

    // ---- Groups ----

    private val GROUP_WORDS = listOf(
        "SOCCER", "EPL", "EFL", "UEFA", "LALIGA", "LA LIGA", "MLS", "BUNDESLIGA", "SERIE A", "CRICKET", "IPL", "F1", "FORMULA",
        "NCAA", "NCAAF", "NFL",
    )

    /** A live group whose title names one of the leagues' own groups. "NCAAB" (basketball) alone does not count. */
    fun isLeagueGroup(title: String): Boolean {
        if (any(title, listOf("NCAAB")) && !any(title, listOf("NCAAF", "FOOTBALL"))) return false
        return any(title, GROUP_WORDS)
    }

    /** How a group is used by the all-leagues search: league groups are read in full, others as [SportsLogic.nflScan] says. */
    fun scan(title: String): SportsLogic.NflScan =
        if (isLeagueGroup(title)) SportsLogic.NflScan.ALL_CHANNELS else SportsLogic.nflScan(title)
}

/**
 * Stops reading a group whose channels have no guide at all (numbered event slots such as "LIVE SOCCER 01..34"). Feed it each
 * channel's result in order; it answers true once the first [limit] channels asked all had empty or placeholder-only guides.
 * A null result (could not ask) is not counted. One real programme within the first [limit] switches the guard off.
 */
class DeadGroupGuard(private val limit: Int = 8) {
    private var read = 0
    private var alive = false

    /** True when reading this group should stop now. */
    fun feed(programmes: List<EpgProgramme>?, channelName: String): Boolean {
        if (alive || programmes == null || read >= limit) return false
        read++
        if (programmes.any { !SportsLogic.isPlaceholder(it.name, channelName) }) { alive = true; return false }
        return read >= limit
    }
}
