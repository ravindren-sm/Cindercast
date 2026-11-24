package io.github.ravindren_sm.openstream.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RequestBudgetTest {

    /** Fake clock: sleeping advances time instantly and records each wait. */
    private class FakeTime {
        var now = 1_000_000L
        val sleeps = mutableListOf<Long>()
        fun sleep(ms: Long) { sleeps += ms; now += ms }
    }

    @Test fun `allows the cap and refuses one more`() {
        val t = FakeTime()
        val b = RequestBudget(nowMillis = { t.now }, sleepMillis = t::sleep)
        repeat(10) { b.acquire() }
        assertEquals(0, b.remaining)
        assertFailsWith<BudgetExhaustedException> { b.acquire() }
    }

    @Test fun `paces requests at least two seconds apart`() {
        val t = FakeTime()
        val b = RequestBudget(nowMillis = { t.now }, sleepMillis = t::sleep)
        b.acquire()                 // first request: no wait
        t.now += 500                // caller spends 0.5 s
        b.acquire()                 // must wait the remaining 1.5 s
        t.now += 5_000              // caller spends 5 s
        b.acquire()                 // already past the interval: no wait
        assertEquals(listOf(1_500L), t.sleeps)
    }

    @Test fun `cap cannot be raised and interval cannot be shortened`() {
        assertFailsWith<IllegalArgumentException> { RequestBudget(maxRequests = 11) }
        assertFailsWith<IllegalArgumentException> { RequestBudget(minIntervalMillis = 500) }
        RequestBudget(maxRequests = 3) // lowering is allowed
    }
}
