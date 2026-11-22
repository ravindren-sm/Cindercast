package io.github.ravindren_sm.openstream

import io.github.ravindren_sm.openstream.data.CatalogResult
import io.github.ravindren_sm.openstream.data.LiveZapper
import io.github.ravindren_sm.openstream.data.PagedChannels
import io.github.ravindren_sm.openstream.data.ZapResult
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.LiveChannelPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zapping over a fake sorted catalog: channel numbers 40, 41, ... in pages of 3. No portal involved. */
class LiveZapperTest {
    private val total = 30
    private val pageSize = 3
    private var fetches = 0
    private var failFrom = Int.MAX_VALUE

    private fun channel(n: Int) = LiveChannel("id$n", "$n", "Channel $n", null)

    private fun paged() = PagedChannels { page ->
        fetches++
        if (page >= failFrom) return@PagedChannels CatalogResult.Failed("down")
        val numbers = (0 until total).map { 40 + it }.drop((page - 1) * pageSize).take(pageSize)
        CatalogResult.Ok(LiveChannelPage(page, numbers.map(::channel), total, pageSize))
    }

    private fun index(r: ZapResult) = (r as ZapResult.To).index

    @Test fun `next moves within what is loaded without a fetch`() {
        val p = paged().also { it.loadNext() }          // channels 40..42
        val z = LiveZapper(p)
        assertEquals(1, index(z.next(0)))
        assertEquals(1, fetches)
    }

    @Test fun `next at the last loaded channel fetches the next page`() {
        val p = paged().also { it.loadNext() }
        val z = LiveZapper(p)
        assertEquals(3, index(z.next(2)))
        assertEquals(6, p.items.size)
        assertEquals(2, fetches)
    }

    @Test fun `next at the very last channel says at end and previous at the first says at end`() {
        val p = paged()
        repeat(total / pageSize) { p.loadNext() }
        val z = LiveZapper(p)
        assertTrue(p.exhausted)
        assertEquals(ZapResult.AtEnd, z.next(total - 1))
        assertEquals(ZapResult.AtEnd, z.previous(0))
        assertEquals(4, index(z.previous(5)))
    }

    @Test fun `a failed page while going next leaves the position and says at end`() {
        val p = paged().also { it.loadNext() }
        failFrom = 2
        assertEquals(ZapResult.AtEnd, LiveZapper(p).next(2))
    }

    @Test fun `a typed number already loaded is found without a fetch`() {
        val p = paged().also { it.loadNext(); it.loadNext() }   // 40..45
        val z = LiveZapper(p)
        assertEquals(3, index(z.byNumber("43")))
        assertEquals(2, fetches)
    }

    @Test fun `a typed number beyond what is loaded fetches pages until it appears`() {
        val p = paged().also { it.loadNext() }
        assertEquals(10, index(LiveZapper(p).byNumber("50")))   // 40 + 10
        assertEquals(4, fetches)                                // pages 1..4 cover 40..51
    }

    @Test fun `a number that does not exist stops once the numbers pass it`() {
        val p = paged().also { it.loadNext() }
        assertEquals(ZapResult.NotFound, LiveZapper(p).byNumber("10"))   // below the first channel, no fetch
        assertEquals(1, fetches)
    }

    @Test fun `a number past the last channel stops at the end of the catalog`() {
        val p = paged().also { it.loadNext() }
        assertEquals(ZapResult.NotFound, LiveZapper(p).byNumber("999"))
        assertTrue(p.exhausted)
        assertEquals(total / pageSize, fetches)
    }

    @Test fun `a lookup is capped so a far-off number cannot flood the portal`() {
        val p = paged().also { it.loadNext() }
        assertEquals(ZapResult.NotFound, LiveZapper(p, maxPages = 2).byNumber("69"))
        assertEquals(3, fetches)                                // the first page plus two more
    }

    @Test fun `a failed page during a number lookup is not found, and leading zeros and junk are handled`() {
        val p = paged().also { it.loadNext() }
        failFrom = 2
        assertEquals(ZapResult.NotFound, LiveZapper(p).byNumber("55"))
        assertEquals(0, index(LiveZapper(paged().also { it.loadNext() }).byNumber("040")))
        assertEquals(ZapResult.NotFound, LiveZapper(p).byNumber(""))
    }
}
