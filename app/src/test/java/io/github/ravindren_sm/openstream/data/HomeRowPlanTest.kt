package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.profiles.db.PinnedCategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeRowPlanTest {
    private fun pin(kind: String, id: String, pos: Int) = PinnedCategoryEntity(1, kind, id, "T$id", pos)

    @Test fun titleRowsFollowTheSavedOrderAndAreCapped() {
        val pins = listOf(pin("MOVIES", "a", 3), pin("SHOWS", "b", 1), pin("MOVIES", "c", 2), pin("MOVIES", "d", 0), pin("LIVE", "g", 9))
        assertEquals(listOf("d", "b", "c"), HomeRowPlan.titlePins(pins).map { it.categoryId })
    }

    @Test fun theLiveGroupIsKeptApartFromTheTitleRows() {
        val pins = listOf(pin("LIVE", "g", 0), pin("MOVIES", "a", 1))
        assertEquals("g", HomeRowPlan.livePin(pins)?.categoryId)
        assertEquals(listOf("a"), HomeRowPlan.titlePins(pins).map { it.categoryId })
        assertNull(HomeRowPlan.livePin(listOf(pin("MOVIES", "a", 0))))
    }

    @Test fun aNewPinGoesAfterTheExistingOnes() {
        assertEquals(0, HomeRowPlan.nextPosition(emptyList()))
        assertEquals(6, HomeRowPlan.nextPosition(listOf(pin("MOVIES", "a", 5), pin("SHOWS", "b", 2))))
    }
}
