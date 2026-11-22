package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.VodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEpisodeTest {
    private fun item(id: String) = VodItem(id = id, name = id, isSeries = false, isSeason = false, isEpisode = true, cmd = null, episodeNumbers = emptyList())
    private val seasons = listOf(SeasonRef(item("s1")), SeasonRef(item("s2")), SeasonRef(item("s3")))
    private val episodes = mapOf(
        "s1" to listOf("e1", "e2"), "s2" to listOf(), "s3" to listOf("e1", "e2"),
    )
    private fun episodesOf(s: SeasonRef) = episodes[s.item.id]?.map { EpisodeRef(item(it)) }

    @Test fun theNextEpisodeInTheSeasonFollows() {
        val n = NextEpisodeLogic.next(seasons, "s1", "e1", ::episodesOf)!!
        assertEquals("s1", n.season.item.id); assertEquals("e2", n.episode.item.id)
    }

    @Test fun theEndOfASeasonLeadsToTheNextSeasonThatHasEpisodes() {
        val n = NextEpisodeLogic.next(seasons, "s1", "e2", ::episodesOf)!!
        assertEquals("s3", n.season.item.id); assertEquals("e1", n.episode.item.id)      // s2 is empty and skipped
    }

    @Test fun theLastEpisodeOfTheShowHasNoNext() {
        assertNull(NextEpisodeLogic.next(seasons, "s3", "e2", ::episodesOf))
    }

    @Test fun unknownIdsOrUnreadableSeasonsGiveNothing() {
        assertNull(NextEpisodeLogic.next(seasons, "zz", "e1", ::episodesOf))
        assertNull(NextEpisodeLogic.next(seasons, "s1", "zz", ::episodesOf))
        assertNull(NextEpisodeLogic.next(seasons, "s1", "e1") { null })
    }

    @Test fun resumeKeysAreSplitIntoShowSeasonEpisode() {
        assertEquals(Triple("a", "a-s1", "a-s1-e2"), NextEpisodeLogic.parseKey(ResumeKeys.episode("a", "a-s1", "a-s1-e2")))
        assertNull(NextEpisodeLogic.parseKey("12345"))
        assertNull(NextEpisodeLogic.parseKey("a//c"))
    }

    @Test fun seekStepsGrowWithTheHoldAndStartAtTenSeconds() {
        assertEquals(10_000L, SeekRamp.stepMillis(0))
        assertEquals(30_000L, SeekRamp.stepMillis(5))
        assertEquals(60_000L, SeekRamp.stepMillis(10))
        assertEquals(180_000L, SeekRamp.stepMillis(20))
        assertEquals(300_000L, SeekRamp.stepMillis(50))
        val steps = (0..60).map(SeekRamp::stepMillis)
        assertEquals(steps.sorted(), steps)
    }
}

class LiveShiftTest {
    @org.junit.Test fun labelsSayLiveOrHowFarBehind() {
        org.junit.Assert.assertEquals("", LiveShift.label(null))
        org.junit.Assert.assertEquals("Live", LiveShift.label(1_500))
        org.junit.Assert.assertEquals("-2:05 behind live", LiveShift.label(125_000))
        org.junit.Assert.assertEquals("-1:00:00 behind live", LiveShift.label(3_600_000))
    }

    @org.junit.Test fun aSkipThatPassesTheEdgeRejoinsLive() {
        org.junit.Assert.assertEquals(true, LiveShift.reachesLive(20_000, 30_000))
        org.junit.Assert.assertEquals(false, LiveShift.reachesLive(120_000, 30_000))
        org.junit.Assert.assertEquals(false, LiveShift.reachesLive(null, 30_000))
    }
}
