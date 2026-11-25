package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.provider.LiveChannel
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideGridTest {
    private fun p(name: String, start: Long, stop: Long, descr: String? = null) = EpgProgramme(name, descr, null, start, stop)
    private val W = GuideGrid.WINDOW_SEC
    private val H = 3600L

    @Test fun floorsToTheHalfHour() {
        assertEquals(1800L, GuideGrid.floorToSlot(2500))
        assertEquals(1800L, GuideGrid.floorToSlot(1800))
        assertEquals(0L, GuideGrid.floorToSlot(1799))
    }

    @Test fun widthFollowsDurationMinusTheGap() {
        // 0.1 unit per second: an hour is 360 units, less a 4 unit gap.
        val c = GuideGrid.layout(listOf(p("A", 0, H)), 0, W, 0.1f, gap = 4f).single()
        assertEquals(0f, c.x, 0.001f); assertEquals(356f, c.width, 0.001f)
        assertFalse(c.clippedStart); assertFalse(c.clippedEnd)
    }

    @Test fun cellsAreCutAtTheWindowEdges() {
        val list = listOf(p("Early", 0, 2 * H), p("Late", 2 * H, 6 * H))
        val cells = GuideGrid.layout(list, H, W, 0.1f, gap = 0f)
        // Early: only the part from the window start (1 h) to 2 h remains; Late runs to the window end (1 h + 2.5 h).
        assertEquals(0f, cells[0].x, 0.001f); assertEquals(360f, cells[0].width, 0.001f); assertTrue(cells[0].clippedStart)
        assertEquals(360f, cells[1].x, 0.001f); assertEquals(W * 0.1f - 360f, cells[1].width, 0.001f); assertTrue(cells[1].clippedEnd)
    }

    @Test fun programmesOutsideTheWindowAreLeftOut() {
        val list = listOf(p("Gone", 0, H), p("Later", 10 * H, 11 * H), p("Here", 2 * H, 3 * H))
        assertEquals(listOf("Here"), GuideGrid.layout(list, 2 * H, W, 0.1f).map { it.programme.name })
        // One that ends exactly at the window start is outside, one that starts exactly at the end too.
        assertTrue(GuideGrid.layout(listOf(p("E", 0, H), p("S", H + W, H + W + 100)), H, W, 0.1f).isEmpty())
    }

    @Test fun veryShortProgrammesKeepAMinimumWidthButStayInsideTheWindow() {
        val c = GuideGrid.layout(listOf(p("Blip", 100, 160)), 0, W, 0.1f, gap = 4f, minWidth = 24f).single()
        assertEquals(24f, c.width, 0.001f)
        val atEdge = GuideGrid.layout(listOf(p("Edge", W - 30, W + 100)), 0, W, 0.1f, gap = 4f, minWidth = 24f).single()
        assertEquals(W * 0.1f, atEdge.x + atEdge.width, 0.001f)
    }

    @Test fun gapsInTheGuideLeaveEmptySpaceAndOverlapsAreCut() {
        val gap = GuideGrid.layout(listOf(p("A", 0, 600), p("B", 1800, 2400)), 0, W, 0.1f, gap = 0f)
        assertEquals(60f, gap[0].width, 0.001f); assertEquals(180f, gap[1].x, 0.001f)
        val overlap = GuideGrid.layout(listOf(p("A", 0, 1000), p("B", 600, 1200)), 0, W, 0.1f, gap = 0f)
        assertEquals(60f, overlap[0].width, 0.001f)
    }

    @Test fun programmeAtPicksTheOneOnAirAndTheBoundaryBelongsToTheNewOne() {
        val list = listOf(p("A", 0, 100), p("B", 100, 200), p("C", 300, 400))
        assertEquals("A", GuideGrid.programmeAt(list, 50)?.name)
        assertEquals("B", GuideGrid.programmeAt(list, 100)?.name)
        assertNull(GuideGrid.programmeAt(list, 250))
        assertNull(GuideGrid.programmeAt(list, 400))
    }

    @Test fun programmeNearFallsBackToTheNearestInTime() {
        val list = listOf(p("A", 0, 100), p("C", 300, 400))
        assertEquals("A", GuideGrid.programmeNear(list, 120)?.name)
        assertEquals("C", GuideGrid.programmeNear(list, 260)?.name)
        assertEquals("C", GuideGrid.programmeNear(list, 200)?.name)   // tie: the later one
        assertEquals("C", GuideGrid.programmeNear(list, 5000)?.name)
        assertNull(GuideGrid.programmeNear(emptyList(), 5))
    }

    @Test fun afterAndBeforeStepAlongTheRow() {
        val a = p("A", 0, 100); val b = p("B", 100, 200); val c = p("C", 300, 400)
        val list = listOf(c, a, b)
        assertEquals("B", GuideGrid.after(list, a)?.name); assertEquals("C", GuideGrid.after(list, b)?.name); assertNull(GuideGrid.after(list, c))
        assertEquals("B", GuideGrid.before(list, c)?.name); assertEquals("A", GuideGrid.before(list, b)?.name); assertNull(GuideGrid.before(list, a))
    }

    @Test fun theWindowScrollsOnlyWhenTheTargetIsOffScreen() {
        val start = 10 * H
        // Visible: no change either way.
        assertEquals(start, GuideGrid.windowStartFor(p("v", start + H, start + 2 * H), 1, start, W, start))
        // Right, beyond the window: the new start leaves an hour of context before the target's half hour.
        val far = p("f", start + W + 600, start + W + 4000)
        assertEquals(GuideGrid.floorToSlot(far.startSec) - H, GuideGrid.windowStartFor(far, 1, start, W, start))
        // Left, before the window: starts at the target's half hour, never before the earliest allowed.
        val before = p("b", start - 2 * H - 600, start - H)
        assertEquals(GuideGrid.floorToSlot(before.startSec), GuideGrid.windowStartFor(before, -1, start, W, start - 10 * H))
        assertEquals(start - H, GuideGrid.windowStartFor(before, -1, start, W, start - H))
    }

    // ---- programme search ----

    private fun ch(id: String, name: String, number: String = id) = LiveChannel(id, number, name, null)
    private val now = 10_000L

    @Test fun searchMatchesWordsAnywhereInTitleOrDescriptionIgnoringCase() {
        val c = ch("1", "News One")
        val guides = listOf(c to listOf(
            p("Evening NEWS", now + 100, now + 200, "Headlines and weather"),
            p("Cooking", now + 300, now + 400, "Chef shows how to make a News-worthy stew"),
            p("Quiz", now + 500, now + 600, "Questions"),
        ))
        assertEquals(listOf("Evening NEWS", "Cooking"), ProgrammeSearch.search(guides, "news", now).map { it.programme.name })
        assertEquals(listOf("Evening NEWS"), ProgrammeSearch.search(guides, "WEATHER evening", now).map { it.programme.name })
        assertTrue(ProgrammeSearch.search(guides, "news quiz", now).isEmpty())
        assertTrue(ProgrammeSearch.search(guides, "   ", now).isEmpty())
    }

    @Test fun searchListsLiveNowFirstThenByStartAndDropsEndedAndFiller() {
        val a = ch("1", "A"); val b = ch("2", "B")
        val guides = listOf(
            a to listOf(p("Match late", now + 900, now + 1000), p("Match ended", now - 500, now - 1), p("Match live A", now - 100, now + 50)),
            b to listOf(p("Match soon", now + 100, now + 200), p("Match live B", now - 300, now + 10), p("Match", now - 5, now + 5)),
        )
        val r = ProgrammeSearch.search(guides, "match", now)
        // Live: by start (B's started earliest), then the filler-like "Match" is a real title here so it is kept; upcoming by start.
        assertEquals(listOf("Match live B", "Match live A", "Match", "Match soon", "Match late"), r.map { it.programme.name })
        assertTrue(r[0].live); assertFalse(r[3].live)
    }

    @Test fun searchSkipsPlaceholdersListsAProgrammeOnceAndHonoursTheLimit() {
        val c = ch("1", "A")
        val dup = p("Show", now + 10, now + 20)
        val guides = listOf(c to listOf(dup, dup, p("No details available", now + 5, now + 9, "show")))
        assertEquals(1, ProgrammeSearch.search(guides, "show", now).size)
        val many = listOf(c to (1..30).map { p("Show $it", now + it, now + it + 1) })
        assertEquals(10, ProgrammeSearch.search(many, "show", now, limit = 10).size)
    }

    @Test fun searchTiesBreakOnChannelNumber() {
        val guides = listOf(ch("9", "Z", "9") to listOf(p("Same", now + 5, now + 9)), ch("2", "Y", "2") to listOf(p("Same", now + 5, now + 9)))
        assertEquals(listOf("2", "9"), ProgrammeSearch.search(guides, "same", now).map { it.channel.id })
    }

    @Test fun whenLabelsAreLiveNowTodayTomorrowWeekdayOrDate() {
        val utc = TimeZone.getTimeZone("UTC")
        val clock = { _: Long -> "8:00 PM" }
        val noon = 1_800_000_000L - Math.floorMod(1_800_000_000L, 86_400L) + 12 * 3600   // some day, 12:00 UTC
        fun label(start: Long) = ProgrammeSearch.whenLabel(start, noon, utc, Locale.US, clock)
        assertEquals("Live now", label(noon - 5))
        assertEquals("Today 8:00 PM", label(noon + 8 * 3600))
        assertEquals("Tomorrow 8:00 PM", label(noon + 20 * 3600))
        assertTrue(label(noon + 3 * 86_400).endsWith(" 8:00 PM") && !label(noon + 3 * 86_400).startsWith("Today"))
        assertEquals(3, label(noon + 3 * 86_400).substringBefore(' ').length)   // "Fri"-style
        assertTrue(label(noon + 9 * 86_400).first().isDigit())                  // "17 Jan 8:00 PM"
    }
}
