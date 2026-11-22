package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.profiles.db.IndexProgressEntity
import io.github.ravindren_sm.openstream.provider.VodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleIndexerTest {
    @Test fun pageCountRoundsUp() {
        assertEquals(0, TitleIndexer.pageCount(0, 14))
        assertEquals(1, TitleIndexer.pageCount(14, 14))
        assertEquals(2, TitleIndexer.pageCount(15, 14))
        assertEquals(7_143, TitleIndexer.pageCount(100_000, 14))
        assertEquals(0, TitleIndexer.pageCount(10, 0))
    }

    @Test fun progressCountsFinishedCategoriesFullyAndPartialOnesByPages() {
        val cats = listOf(VodCategory("1", "A"), VodCategory("2", "B"), VodCategory("3", "C"), VodCategory("4", "D"))
        val progress = listOf(
            IndexProgressEntity("1", 5, 56, done = true),        // finished: 1.0
            IndexProgressEntity("2", 3, 56, done = false),       // 2 of 4 pages: 0.5
        )                                                        // categories 3 and 4 not started: 0
        assertEquals(37, TitleIndexer.percentOf(progress, cats))   // (1 + 0.5) / 4
        assertEquals(0, TitleIndexer.percentOf(emptyList(), cats))
        assertEquals(0, TitleIndexer.percentOf(emptyList(), emptyList()))
    }

    @Test fun anIndexedRowKeepsKindNameKeyAndTheCommandButNotTheDescription() {
        val item = VodItem(id = "9", name = "Tom &amp; Jerry 7", isSeries = true, isSeason = false, isEpisode = false, cmd = null,
            episodeNumbers = emptyList(), fileCmds = listOf("/m/9"), year = "1999", description = "long text", posterUri = "/p/9.png", age = "PG")
        val row = TitleIndexer.entityOf(item, "20")
        assertEquals("SHOW", row.kind); assertEquals("Tom & Jerry 7", row.name); assertEquals("tom & jerry 7", row.nameKey)
        assertEquals("/m/9", row.cmd); assertEquals("20", row.categoryId); assertEquals("PG", row.age)
        assertNull(null)
    }
}
