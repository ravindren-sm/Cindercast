package io.github.ravindren_sm.openstream.profiles

import io.github.ravindren_sm.openstream.data.ResumeStore
import io.github.ravindren_sm.openstream.profiles.db.HistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinueItemsTest {
    private fun row(key: String, kind: String = "MOVIE", titleId: String = key, cmd: String? = "cmd-$key", pos: Long = 600_000, dur: Long = 6_000_000) =
        HistoryEntity(1, key, kind, titleId, "Title $key", null, null, pos, dur, false, 0, cmd)

    @Test fun keepsOrderAndDropsRowsThatCannotBePlayedAgain() {
        val tiles = ContinueItems.from(listOf(row("a"), row("b", cmd = null), row("c")))
        assertEquals(listOf("a", "c"), tiles.map { it.resumeKey })
    }

    @Test fun oneTilePerShowTheNewestWins() {
        val rows = listOf(row("s1/e3", "EPISODE", "s1"), row("s1/e2", "EPISODE", "s1"), row("s2/e1", "EPISODE", "s2"))
        assertEquals(listOf("s1/e3", "s2/e1"), ContinueItems.from(rows).map { it.resumeKey })
    }

    @Test fun aMovieAndAShowWithTheSameIdAreDifferentTitles() {
        val rows = listOf(row("7", "MOVIE", "7"), row("7/1/1", "EPISODE", "7"))
        assertEquals(2, ContinueItems.from(rows).size)
    }

    @Test fun finishedTitlesAreMarkedWatchedAndShowAFullBar() {
        val t = ContinueItems.from(listOf(row("a").copy(completed = true, positionMs = 5_990_000))).single()
        assertEquals("Watched", t.timeLeft)
        assertEquals(1f, t.progress, 0f)
        assertEquals(true, t.completed)
    }

    @Test fun aShowAlreadyInTheFirstRowIsLeftOutOfTheSecond() {
        val inContinue = ContinueItems.from(listOf(row("s1/e1", "EPISODE", "s1")))
        val finished = listOf(row("s1/e0", "EPISODE", "s1"), row("m9").copy(completed = true))
        val recent = ContinueItems.from(finished, exclude = inContinue.mapTo(HashSet()) { it.titleKey })
        assertEquals(listOf("m9"), recent.map { it.resumeKey })
    }

    @Test fun capsTheRow() {
        assertEquals(3, ContinueItems.from(List(10) { row("m$it") }, max = 3).size)
    }

    @Test fun progressAndTimeLeft() {
        assertEquals(0.1f, ContinueItems.progress(600_000, 6_000_000), 0.0001f)
        assertEquals(0f, ContinueItems.progress(5, 0), 0f)
        assertEquals(1f, ContinueItems.progress(9_000_000, 6_000_000), 0f)
    }

    @Test fun timeLeftWording() {
        assertEquals("1 h 30 min left", ContinueItems.timeLeft(600_000, 6_000_000))
        assertEquals("2 h left", ContinueItems.timeLeft(0, 7_200_000))
        assertEquals("45 min left", ContinueItems.timeLeft(0, 2_700_000))
        assertEquals("1 min left", ContinueItems.timeLeft(0, 30_000))          // rounded up
        assertEquals("Almost done", ContinueItems.timeLeft(100_000, 100_000))
        assertNull(ContinueItems.timeLeft(10_000, 0))
    }

    @Test fun resumePositionsAreKeptPerProfile() {
        val base = object : ResumeStore {
            val m = HashMap<String, Long>()
            override fun get(id: String) = m[id]
            override fun put(id: String, positionMillis: Long) { m[id] = positionMillis }
            override fun clear(id: String) { m.remove(id) }
        }
        var profile = 1L
        val store = ProfileResumeStore(base) { profile }
        store.put("movie", 100_000)
        profile = 2
        assertNull(store.get("movie"))
        store.put("movie", 5_000_000)
        profile = 1
        assertEquals(100_000L, store.get("movie"))
        store.clear("movie")
        assertNull(store.get("movie"))
        profile = 2
        assertEquals(5_000_000L, store.get("movie"))
    }
}
