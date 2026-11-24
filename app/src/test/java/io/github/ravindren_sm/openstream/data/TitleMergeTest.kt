package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.VodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMergeTest {
    private fun v(id: String, name: String, year: String? = null, series: Boolean = false) =
        VodItem(id = id, name = name, isSeries = series, isSeason = false, isEpisode = false, cmd = "c$id", episodeNumbers = emptyList(), year = year)

    @Test fun qualityAndLanguageTagsAreIgnoredWhenComparingNames() {
        assertEquals("toy story 5", TitleMerge.baseKey("Toy Story 5 - 4K"))
        assertEquals("toy story 5", TitleMerge.baseKey("Toy Story 5 [Hindi] (1080p)"))
        assertEquals("toy story 5", TitleMerge.baseKey("TOY STORY 5 | HD"))
        assertEquals("tom & jerry 7", TitleMerge.baseKey("Tom &amp; Jerry 7"))
    }

    @Test fun aTitleThatIsOnlyALanguageWordIsKept() {
        assertEquals("hindi", TitleMerge.baseKey("Hindi"))
        assertEquals("the it crowd", TitleMerge.baseKey("The IT Crowd"))
    }

    @Test fun versionsOfOneTitleMergeAndTheBestQualityLeads() {
        val groups = TitleMerge.group(listOf(v("1", "Toy Story 5"), v("2", "Toy Story 5 - 4K"), v("3", "Toy Story 5 CAM"), v("4", "Other")))
        assertEquals(2, groups.size)
        assertEquals("2", groups[0].best.id)
        assertEquals(listOf("2", "1", "3"), groups[0].versions.map { it.id })
        assertEquals(3, groups[0].count)
    }

    @Test fun differentYearsStayApartAndAMissingYearJoinsTheFirst() {
        val groups = TitleMerge.group(listOf(v("1", "Dune", "1984"), v("2", "Dune", "2021"), v("3", "Dune HD")))
        assertEquals(2, groups.size)
        assertEquals(listOf("3", "1"), groups[0].versions.map { it.id })   // the HD version leads the plain one
        assertEquals(listOf("2"), groups[1].versions.map { it.id })
    }

    @Test fun moviesAndShowsWithTheSameNameAreNotMixed() {
        assertEquals(2, TitleMerge.group(listOf(v("1", "Fargo"), v("2", "Fargo", series = true))).size)
    }

    @Test fun versionLabelsNameTheQualityAndLanguageOrFallBack() {
        assertEquals("4K", TitleMerge.versionLabel(v("1", "Toy Story 5 - 4K"), 0))
        assertEquals("HD · Hindi", TitleMerge.versionLabel(v("1", "Toy Story 5 HD Hindi"), 0))
        assertEquals("Version 3", TitleMerge.versionLabel(v("1", "Toy Story 5"), 2))
    }

    @Test fun firstSeenOrderOfGroupsIsKept() {
        val groups = TitleMerge.group(listOf(v("1", "B"), v("2", "A"), v("3", "B 4K")))
        assertEquals(listOf("3", "2"), groups.map { it.best.id })
    }

    @Test fun cammedCopiesRankLast() {
        assertTrue(TitleMerge.qualityScore("X CAM") < TitleMerge.qualityScore("X"))
        assertTrue(TitleMerge.qualityScore("X 4K") > TitleMerge.qualityScore("X 1080p"))
    }
}
