package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.EpgProgramme
import io.github.ravindren_sm.openstream.provider.LiveChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NflSearchStoreTest {
    @Test fun savedListingsComeBackWithChannelProgrammeAndGameFlag() {
        val ch = LiveChannel("12", "2823", "FOX 5 HD", "ffrt local/ch/12")
        val l = SportsLogic.NflListing(ch, EpgProgramme("Bengals at Commanders", null, null, 1000, 5000), live = false, game = true)
        val show = SportsLogic.NflListing(LiveChannel("3", "503", "ESPN", null), EpgProgramme("NFL Live", null, null, 900, 2000), live = true)
        val (back, at) = NflSearch.decode(NflSearch.encode(listOf(l, show), 42L))
        assertEquals(42L, at)
        assertEquals(listOf("12", "3"), back.map { it.channel.id })
        assertEquals("ffrt local/ch/12", back[0].channel.cmd)
        assertEquals("Bengals at Commanders", back[0].programme?.name)
        assertEquals(5000L, back[0].programme?.stopSec)
        assertTrue(back[0].game)
        assertEquals(null, back[1].channel.cmd)
    }

    @Test fun anUnreadableFileGivesNothing() {
        assertEquals(0, NflSearch.decode("not json").first.size)
        assertEquals(0, NflSearch.decode("").first.size)
    }
}
