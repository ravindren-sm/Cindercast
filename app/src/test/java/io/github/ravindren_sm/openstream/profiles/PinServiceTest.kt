package io.github.ravindren_sm.openstream.profiles

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The household PIN: hashed storage, wrong guesses, lockout. A fake store and clock; 1,000 iterations for speed. */
class PinServiceTest {
    private class FakeStore : PinStore {
        var record: PinRecord? = null
        override suspend fun load() = record
        override suspend fun save(record: PinRecord) { this.record = record }
    }

    private var now = 1_000_000L
    private val store = FakeStore()
    private val pins = PinService(store, nowMillis = { now }, iterations = 1_000)

    @Test fun `no PIN is set at first, and verifying says so`() = runBlocking {
        assertFalse(pins.isSet())
        assertTrue(pins.verify("1234") is PinResult.NotSet)
    }

    @Test fun `a PIN must be exactly four digits`() = runBlocking {
        listOf("", "123", "12345", "12a4", " 123", "١٢٣٤").forEach { bad ->
            assertTrue(bad, runCatching { pins.set(bad) }.isFailure)
        }
        assertFalse(pins.isSet())
    }

    @Test fun `the right PIN passes and the PIN is not stored`() = runBlocking {
        pins.set("2468")
        assertTrue(pins.isSet())
        assertTrue(pins.verify("2468") is PinResult.Ok)
        val r = store.record!!
        assertFalse(String(r.hash, Charsets.ISO_8859_1).contains("2468"))
        assertFalse(r.salt.isEmpty()); assertEquals(1_000, r.iterations)
    }

    @Test fun `the same PIN gets a different salt and hash each time it is set`() = runBlocking {
        pins.set("2468"); val first = store.record!!
        pins.set("2468"); val second = store.record!!
        assertNotEquals(first.salt.toList(), second.salt.toList())
        assertNotEquals(first.hash.toList(), second.hash.toList())
        assertTrue(pins.verify("2468") is PinResult.Ok)
    }

    @Test fun `wrong guesses count down, then the fifth locks`() = runBlocking {
        pins.set("2468")
        for (left in 4 downTo 1) { val r = pins.verify("0000") as PinResult.Wrong; assertEquals(left, r.attemptsLeft) }
        val locked = pins.verify("0000") as PinResult.Locked
        assertEquals(now + 30_000, locked.untilMillis)
    }

    @Test fun `while locked even the right PIN is refused, and it works after the lock ends`() = runBlocking {
        pins.set("2468")
        repeat(5) { pins.verify("0000") }
        assertTrue(pins.verify("2468") is PinResult.Locked)
        now += 29_000; assertTrue(pins.verify("2468") is PinResult.Locked)
        now += 2_000;  assertTrue(pins.verify("2468") is PinResult.Ok)
    }

    @Test fun `a success clears the failures, so guesses start again from five`() = runBlocking {
        pins.set("2468")
        repeat(3) { pins.verify("0000") }
        assertTrue(pins.verify("2468") is PinResult.Ok)
        assertEquals(4, (pins.verify("0000") as PinResult.Wrong).attemptsLeft)
    }

    @Test fun `each further wrong guess after the lock doubles it, up to 15 minutes`() {
        assertEquals(30_000, PinService.lockMillis(5)); assertEquals(60_000, PinService.lockMillis(6))
        assertEquals(120_000, PinService.lockMillis(7)); assertEquals(15 * 60_000, PinService.lockMillis(40))
    }

    @Test fun `repeated wrong guesses after a lock make the next lock longer`() = runBlocking {
        pins.set("2468")
        repeat(5) { pins.verify("0000") }
        now += 31_000
        val second = pins.verify("0000") as PinResult.Locked
        assertEquals(now + 60_000, second.untilMillis)
    }

    @Test fun `changing the PIN needs the old one`() = runBlocking {
        pins.set("2468")
        assertTrue(pins.change("1111", "1357") is PinResult.Wrong)
        assertTrue(pins.verify("2468") is PinResult.Ok)
        assertTrue(pins.change("2468", "1357") is PinResult.Ok)
        assertTrue(pins.verify("1357") is PinResult.Ok)
        assertTrue(pins.verify("2468") is PinResult.Wrong)
    }
}
