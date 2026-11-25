package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.provider.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quality badges, sorting, logo addresses, banner text and the mini list window. All names are made up. */
class LiveChannelLogicTest {
    private fun ch(id: String, number: String, name: String) = LiveChannel(id, number, name, null)

    // ---- quality badges ----
    @Test fun aTagAtTheEndIsFoundAndRemoved() {
        val p = ChannelNames.parse("Sky Sports HD")
        assertEquals("Sky Sports", p.display); assertEquals(ChannelQuality.HD, p.quality)
    }

    @Test fun bracketsAndSeparatorsLeftByATagAreCleanedUp() {
        assertEquals("News One", ChannelNames.parse("News One (FHD)").display)
        assertEquals(ChannelQuality.FHD, ChannelNames.parse("News One [FHD]").quality)
        assertEquals("Movies", ChannelNames.parse("4K | Movies").display)
        assertEquals("Movies - US", ChannelNames.parse("Movies HD - US").display)
    }

    @Test fun theHighestTagWinsAndAllAreRemoved() {
        val p = ChannelNames.parse("Arena HD UHD")
        assertEquals("Arena", p.display); assertEquals(ChannelQuality.UHD, p.quality)
        assertEquals(ChannelQuality.K4, ChannelNames.parse("Arena 4K UHD").quality)
    }

    @Test fun resolutionsAndFullHdMapToTheirBadge() {
        assertEquals(ChannelQuality.FHD, ChannelNames.parse("Channel 1080p").quality)
        assertEquals(ChannelQuality.FHD, ChannelNames.parse("Channel Full HD").quality)
        assertEquals(ChannelQuality.HD, ChannelNames.parse("Channel 720p").quality)
        assertEquals(ChannelQuality.UHD, ChannelNames.parse("Channel 2160p").quality)
        assertEquals("Channel", ChannelNames.parse("Channel Full HD").display)
    }

    @Test fun lettersOrDigitsAroundATagMeanItIsNotOne() {
        assertNull(ChannelNames.parse("SHD Movies").quality)
        assertNull(ChannelNames.parse("Studio 1080").quality)
        assertEquals("Studio 1080", ChannelNames.parse("Studio 1080").display)
        assertNull(ChannelNames.parse("Dubai").quality)
    }

    @Test fun aNameThatIsOnlyATagIsKeptWhole() {
        val p = ChannelNames.parse("HD")
        assertEquals("HD", p.display); assertEquals(ChannelQuality.HD, p.quality)
    }

    @Test fun tagsAreFoundInAnyCase() {
        assertEquals(ChannelQuality.HD, ChannelNames.parse("Kids hd").quality)
    }

    // ---- sorting ----
    @Test fun aToZIgnoresCaseAndQualityTagsAndOrdersNumbersNaturally() {
        val list = listOf(ch("1", "1", "zeta HD"), ch("2", "2", "Alpha 10"), ch("3", "3", "alpha 9"), ch("4", "4", "Beta"))
        assertEquals(listOf("alpha 9", "Alpha 10", "Beta", "zeta HD"), ChannelSorting.byName(list).map { it.name })
    }

    @Test fun namesThatMatchAreOrderedByChannelNumber() {
        val list = listOf(ch("a", "30", "News"), ch("b", "4", "News HD"), ch("c", "x", "News"))
        assertEquals(listOf("b", "a", "c"), ChannelSorting.byName(list).map { it.id })
    }

    @Test fun naturalCompareHandlesLeadingZerosAndPrefixes() {
        assertTrue(ChannelSorting.naturalCompare("ch 002", "ch 10") < 0)
        assertTrue(ChannelSorting.naturalCompare("ch", "ch 1") < 0)
        assertEquals(0, ChannelSorting.naturalCompare("same", "same"))
    }

    @Test fun onlyGroupsUpToTheCapAreSorted() {
        assertTrue(ChannelSorting.canSortFully(ChannelSorting.CAP))
        assertTrue(!ChannelSorting.canSortFully(ChannelSorting.CAP + 1))
        assertTrue(!ChannelSorting.canSortFully(null))
    }

    @Test fun theStatusLineIsEmptyForNumberOrderAndSaysWhyForTooBigGroups() {
        assertEquals("", ChannelSorting.statusText(ChannelSort.NUMBER, SortStatus.TooBig(4900)))
        assertTrue(ChannelSorting.statusText(ChannelSort.NAME, SortStatus.TooBig(4900)).contains("too many"))
        assertTrue(ChannelSorting.statusText(ChannelSort.NAME, SortStatus.Loading(14, 200)).contains("14 of 200"))
        assertEquals("", ChannelSorting.statusText(ChannelSort.NAME, SortStatus.Idle))
    }

    // ---- logo addresses ----
    @Test fun logoAddressesResolveAgainstThePortal() {
        val portal = "http://portal.example:8080/stalker_portal/c/"
        assertEquals("http://img.example/a.png", ChannelLogoUrl.resolve("http://img.example/a.png", portal))
        assertEquals("http://portal.example:8080/misc/x.png", ChannelLogoUrl.resolve("/misc/x.png", portal))
        assertEquals("http://portal.example:8080/stalker_portal/misc/logos/320/7.png", ChannelLogoUrl.resolve("7.png", portal))
        assertEquals("http://portal.example/misc/logos/320/7.png", ChannelLogoUrl.resolve("7.png", "http://portal.example/c/"))
        assertNull(ChannelLogoUrl.resolve("", portal)); assertNull(ChannelLogoUrl.resolve(null, portal)); assertNull(ChannelLogoUrl.resolve("7.png", ""))
        assertNull(ChannelLogoUrl.resolve("has space.png", portal))
    }

    // ---- banner text ----
    private fun p(name: String, start: Long, stop: Long) = EpgProgramme(name, null, null, start, stop)

    @Test fun timeLeftIsMinutesOrHoursAndNeverBelowOneMinute() {
        assertEquals("12 min left", LiveInfo.timeLeft(1720, 1000))
        assertEquals("1 h 5 min left", LiveInfo.timeLeft(1000 + 65 * 60, 1000))
        assertEquals("1 min left", LiveInfo.timeLeft(1010, 1000))
    }

    @Test fun nowNextSkipsFillerAndIsNullWithoutAGuide() {
        val list = listOf(p("No details available", 0, 2000), p("Evening News", 2000, 3000), p("Weather", 3000, 3600))
        val nn = LiveInfo.nowNext(list, "News One", 2500)!!
        assertEquals("Evening News", nn.now?.name); assertEquals("Weather", nn.next?.name)
        assertNull(LiveInfo.nowNext(emptyList(), "News One", 2500))
        assertNull(LiveInfo.nowNext(null, "News One", 2500))
        assertNull(LiveInfo.nowNext(listOf(p("No details available", 0, 9000)), "News One", 2500))
    }

    @Test fun progressRunsFromZeroToOne() {
        val prog = p("A", 1000, 2000)
        assertEquals(0.5f, GuideLogic.progress(prog, 1500), 0.001f)
        assertEquals(1f, GuideLogic.progress(prog, 5000), 0f)
    }

    // ---- mini list window ----
    @Test fun theWindowCentresOnTheCursor() {
        assertEquals(6..14, MiniListWindow.range(50, 10))
    }

    @Test fun theWindowIsHeldAtTheEdges() {
        assertEquals(0..8, MiniListWindow.range(50, 0))
        assertEquals(0..8, MiniListWindow.range(50, 2))
        assertEquals(41..49, MiniListWindow.range(50, 49))
        assertEquals(41..49, MiniListWindow.range(50, 47))
    }

    @Test fun aShortListShowsEverythingAndAnEmptyOneNothing() {
        assertEquals(0..2, MiniListWindow.range(3, 1))
        assertTrue(MiniListWindow.range(0, 0).isEmpty())
        assertEquals(0..8, MiniListWindow.range(50, -5))
        assertEquals(41..49, MiniListWindow.range(50, 500))
    }
}
