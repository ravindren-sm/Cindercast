package io.github.ravindren_sm.openstream.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parsing of live genres and channel pages. All data is fake. */
class LiveCatalogTest {
    private fun js(body: String) = StalkerClient.parseJs("""{"js":$body}""".toByteArray())

    @Test fun `genres keep id and title and drop entries without an id`() {
        val genres = LiveCatalogParser.genres(js("""[{"id":"*","title":"All"},{"id":"7","title":" Sports "},{"title":"No id"},{"id":"","title":"Blank id"}]"""))
        assertEquals(listOf(LiveGenre("*", "All"), LiveGenre("7", "Sports")), genres)
    }

    @Test fun `a missing or wrong-shaped genre payload is an empty list`() {
        assertTrue(LiveCatalogParser.genres(null).isEmpty())
        assertTrue(LiveCatalogParser.genres(js("""{"not":"an array"}""")).isEmpty())
    }

    @Test fun `a channel page reads numbers sent as text and keeps the cmd out of toString`() {
        val page = LiveCatalogParser.channelPage(js("""{"total_items":"4900","max_page_items":14,"data":[
            {"id":"11","number":"1","name":"One","cmd":"ffmpeg http://secret.example/live/1"},
            {"id":"12","number":"2","name":"Two","cmd":""},
            {"name":"No id"}]}"""), page = 3)
        assertNotNull(page)
        assertEquals(3, page.page)
        assertEquals(4900, page.totalItems)
        assertEquals(14, page.pageSize)
        assertEquals(listOf("One", "Two"), page.items.map { it.name })
        assertEquals("ffmpeg http://secret.example/live/1", page.items[0].cmd)
        assertNull(page.items[1].cmd)
        assertFalse(page.items[0].toString().contains("secret.example"))
    }

    @Test fun `missing totals fall back to what the page holds`() {
        val page = LiveCatalogParser.channelPage(js("""{"data":[{"id":"1","name":"A"},{"id":"2","name":"B"}]}"""), 1)
        assertNotNull(page)
        assertEquals(2, page.totalItems)
        assertEquals(2, page.pageSize)
    }

    @Test fun `hasMore follows the total, and an empty page ends paging`() {
        val full = LiveChannelPage(1, listOf(LiveChannel("1", "1", "A", null)), totalItems = 3, pageSize = 1)
        assertTrue(full.hasMore(loadedSoFar = 1))
        assertFalse(full.hasMore(loadedSoFar = 3))
        assertFalse(LiveChannelPage(2, emptyList(), totalItems = 99, pageSize = 14).hasMore(loadedSoFar = 14))
    }

    @Test fun `a payload that is not an object gives no page`() {
        assertNull(LiveCatalogParser.channelPage(null, 1))
        assertNull(LiveCatalogParser.channelPage(js("""[1,2]"""), 1))
    }

    @Test fun `a channel logo is read when present and left null when blank or absent`() {
        val page = LiveCatalogParser.channelPage(js("""{"total_items":3,"data":[
            {"id":"1","name":"A","logo":"1.png"},
            {"id":"2","name":"B","logo":"  "},
            {"id":"3","name":"C"}]}"""), 1)
        assertNotNull(page)
        assertEquals(listOf("1.png", null, null), page.items.map { it.logo })
        val all = LiveCatalogParser.allChannels(js("""[{"id":"1","name":"A","logo":"http://img.example/a.png"}]"""))
        assertEquals("http://img.example/a.png", all?.single()?.logo)
        assertFalse(page.items[0].toString().contains("1.png"))
    }
}
