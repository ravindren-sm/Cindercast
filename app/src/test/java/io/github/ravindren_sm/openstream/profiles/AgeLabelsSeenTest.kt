package io.github.ravindren_sm.openstream.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The age labels the real catalogue carries (surveyed 2026-11-22 from the on-device title index; counts only were read). */
class AgeLabelsSeenTest {
    private fun level(label: String?) = AgeLevels.levelOf(label)

    @Test fun `common labels`() {
        assertEquals(0, level("G")); assertEquals(8, level("PG")); assertEquals(13, level("PG-13")); assertEquals(17, level("R"))
        assertEquals(18, level("NC-17")); assertEquals(18, level("TV-MA")); assertEquals(14, level("TV-14")); assertEquals(8, level("TV-PG"))
        assertEquals(0, level("TV-Y")); assertEquals(7, level("TV-Y7")); assertEquals(0, level("TV-G"))
    }

    @Test fun `numbered labels`() {
        assertEquals(16, level("16+")); assertEquals(12, level("12+")); assertEquals(18, level("18+")); assertEquals(6, level("6+"))
        assertEquals(0, level("0+")); assertEquals(15, level("15")); assertEquals(12, level("12A")); assertEquals(15, level("MA 15+"))
        assertEquals(18, level("M18")); assertEquals(18, level("R-18")); assertEquals(13, level("PG13")); assertEquals(15, level("PG15"))
    }

    @Test fun `indian labels in their several spellings`() {
        assertEquals(16, level("U/A 16+")); assertEquals(16, level("U/A16+")); assertEquals(16, level("UA16+")); assertEquals(16, level("UA 16+"))
        assertEquals(13, level("U/A 13+")); assertEquals(13, level("UA13+")); assertEquals(7, level("U/A 7+")); assertEquals(7, level("UA7+"))
        assertEquals(12, level("UA")); assertEquals(12, level("U/A")); assertEquals(0, level("U")); assertEquals(18, level("A"))
    }

    @Test fun `other countries and unnumbered letters`() {
        assertEquals(8, level("PG (Australia)")); assertEquals(7, level("TE+7 (Chile)")); assertEquals(0, level("TP")); assertEquals(15, level("M"))
    }

    @Test fun `labels that say nothing are unknown`() {
        for (l in listOf("NR", "Not Rated", "Unrated", "N/A", "Approved", "CLUB", "", null)) assertNull(l, level(l))
    }
}
