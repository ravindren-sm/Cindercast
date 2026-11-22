package io.github.ravindren_sm.openstream.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Age labels and the Kids and Adult rules. Labels here are the forms seen on the real portal and the usual ones. */
class ParentalPolicyTest {
    private val kid = ProfileInfo(1, "Maya", isKids = true)
    private val adult = ProfileInfo(2, "Dad", isKids = false)
    private val adultWithAdultCategories = adult.copy(showAdultCategories = true)

    @Test fun `common age labels map to a minimum age`() {
        mapOf("G" to 0, "U" to 0, "ALL" to 0, "TV-Y" to 0, "TV-Y7" to 7, "PG" to 8, "TV-PG" to 8, "PG-13" to 13, "TV-14" to 14,
            "R" to 17, "NC-17" to 18, "TV-MA" to 18, "A" to 18, "U/A 7+" to 7, "U/A 13+" to 13, "U/A 16+" to 16, "16+" to 16,
            "18+" to 18, "12A" to 12, "0+" to 0, " pg-13 " to 13, "u/a  13+" to 13)
            .forEach { (label, level) -> assertEquals(label, level, AgeLevels.levelOf(label)) }
    }

    @Test fun `blank or unrecognised labels have no level`() {
        listOf(null, "", "  ", "NR", "Unrated", "N/A", "-", "banana").forEach { assertNull(it.toString(), AgeLevels.levelOf(it)) }
    }

    @Test fun `a Kids profile allows only known labels at or under its ceiling`() {
        assertEquals(Verdict.ALLOWED, ParentalPolicy.titleVerdict(kid, "G"))
        assertEquals(Verdict.ALLOWED, ParentalPolicy.titleVerdict(kid, "PG"))
        assertEquals(Verdict.BLOCKED, ParentalPolicy.titleVerdict(kid, "PG-13"))
        assertEquals(Verdict.BLOCKED, ParentalPolicy.titleVerdict(kid, "U/A 13+"))
        assertEquals(Verdict.BLOCKED, ParentalPolicy.titleVerdict(kid, "R"))
        assertEquals(Verdict.BLOCKED, ParentalPolicy.titleVerdict(kid, null))          // unknown is blocked for Kids
        assertEquals(Verdict.BLOCKED, ParentalPolicy.titleVerdict(kid, "mystery"))
    }

    @Test fun `a Kids ceiling can be raised for an older child`() {
        val older = kid.copy(maxAgeLevel = 13)
        assertEquals(Verdict.ALLOWED, ParentalPolicy.titleVerdict(older, "PG-13"))
        assertEquals(Verdict.BLOCKED, ParentalPolicy.titleVerdict(older, "R"))
    }

    @Test fun `an adult profile plays everything except restricted labels, which ask for the PIN`() {
        assertEquals(Verdict.ALLOWED, ParentalPolicy.titleVerdict(adult, "G"))
        assertEquals(Verdict.ALLOWED, ParentalPolicy.titleVerdict(adult, "PG-13"))
        assertEquals(Verdict.ALLOWED, ParentalPolicy.titleVerdict(adult, "U/A 13+"))
        assertEquals(Verdict.ALLOWED, ParentalPolicy.titleVerdict(adult, null))         // unknown is not blocked for adults
        assertEquals(Verdict.LOCKED, ParentalPolicy.titleVerdict(adult, "R"))
        assertEquals(Verdict.LOCKED, ParentalPolicy.titleVerdict(adult, "TV-MA"))
        assertEquals(Verdict.LOCKED, ParentalPolicy.titleVerdict(adult, "U/A 16+"))
        assertEquals(Verdict.LOCKED, ParentalPolicy.titleVerdict(adult, "18+"))
    }

    @Test fun `adult categories are hidden from everyone unless an adult turned them on`() {
        val allowed = setOf("1")
        assertFalse(ParentalPolicy.categoryVisible(adult, "9", "ADULTS", allowed))
        assertTrue(ParentalPolicy.categoryVisible(adultWithAdultCategories, "9", "ADULTS", allowed))
        assertFalse(ParentalPolicy.categoryVisible(kid, "9", "ADULTS", setOf("9")))               // even if wrongly on the allow-list
        assertFalse(ParentalPolicy.categoryVisible(kid.copy(showAdultCategories = true), "9", "XXX Movies", setOf("9")))
    }

    @Test fun `a Kids profile sees only categories on its allow-list`() {
        val allowed = setOf("12", "13")
        assertTrue(ParentalPolicy.categoryVisible(kid, "12", "KIDS MOVIES", allowed))
        assertFalse(ParentalPolicy.categoryVisible(kid, "40", "ENGLISH MOVIES 4K", allowed))
        assertTrue(ParentalPolicy.categoryVisible(adult, "40", "ENGLISH MOVIES 4K", allowed))
    }

    @Test fun `Kids get no Live TV, and need the PIN for Settings and to switch profile`() {
        assertFalse(ParentalPolicy.liveAllowed(kid)); assertTrue(ParentalPolicy.liveAllowed(adult))
        assertTrue(ParentalPolicy.needsPinForSettings(kid)); assertFalse(ParentalPolicy.needsPinForSettings(adult))
        assertTrue(ParentalPolicy.needsPinToLeave(kid)); assertFalse(ParentalPolicy.needsPinToLeave(adult))
    }

    @Test fun `category names are recognised as adult or kid-friendly`() {
        listOf("ADULTS", "Adults 18+", "XXX", "Erotic Movies", "NSFW").forEach { assertTrue(it, CategoryRules.isAdult(it)) }
        listOf("KIDS MOVIES", "ENGLISH MOVIES 4K", "Dadult", "HOTEL").forEach { assertFalse(it, CategoryRules.isAdult(it)) }
        listOf("KIDS MOVIES", "KIDS MOVIES 4K", "Cartoons", "Family", "ANIMATION").forEach { assertTrue(it, CategoryRules.looksKidFriendly(it)) }
        listOf("ENGLISH MOVIES 4K", "ADULTS", "HINDI MOVIES | CAM", "Kids Adult Party").forEach { assertFalse(it, CategoryRules.looksKidFriendly(it)) }
    }

    @Test fun `a title counts as finished within 45 seconds of the end`() {
        val hour = 3_600_000L
        assertFalse(HistoryRules.isCompleted(600_000, hour)); assertFalse(HistoryRules.isCompleted(hour - 60_000, hour))
        assertTrue(HistoryRules.isCompleted(hour - 30_000, hour)); assertFalse(HistoryRules.isCompleted(5_000, 0))
    }
}
