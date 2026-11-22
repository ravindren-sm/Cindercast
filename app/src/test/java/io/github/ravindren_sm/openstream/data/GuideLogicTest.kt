package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.EpgProgramme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideLogicTest {
    private fun p(name: String, start: Long, stop: Long) = EpgProgramme(name, null, null, start, stop)
    private val list = listOf(p("A", 1000, 2000), p("B", 2000, 3000), p("C", 3000, 4000))

    @Test fun nowIsTheProgrammeCoveringTheTimeAndNextFollowsIt() {
        val r = GuideLogic.nowAndNext(list, 2500)
        assertEquals("B", r.now?.name); assertEquals("C", r.next?.name)
    }

    @Test fun theBoundaryBelongsToTheNewProgramme() {
        assertEquals("B", GuideLogic.nowAndNext(list, 2000).now?.name)
    }

    @Test fun betweenProgrammesThereIsNoNowButStillANext() {
        val gap = listOf(p("A", 1000, 2000), p("B", 3000, 4000))
        val r = GuideLogic.nowAndNext(gap, 2500)
        assertNull(r.now); assertEquals("B", r.next?.name)
    }

    @Test fun afterTheLastProgrammeNothingIsOn() {
        val r = GuideLogic.nowAndNext(list, 5000)
        assertNull(r.now); assertNull(r.next)
    }

    @Test fun beforeTheFirstOnlyNextIsKnown() {
        val r = GuideLogic.nowAndNext(list, 500)
        assertNull(r.now); assertEquals("A", r.next?.name)
    }

    @Test fun progressRunsFromZeroToOne() {
        assertEquals(0.5f, GuideLogic.progress(list[0], 1500), 0.001f)
        assertEquals(0f, GuideLogic.progress(list[0], 500), 0f)
        assertEquals(1f, GuideLogic.progress(list[0], 9000), 0f)
    }
}
