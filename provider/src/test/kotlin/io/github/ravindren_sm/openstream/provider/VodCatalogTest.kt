package io.github.ravindren_sm.openstream.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** VOD page and metadata parsing, and label cleanup. All data is fake. */
class VodCatalogTest {
    private fun js(body: String) = StalkerClient.parseJs("""{"js":$body}""".toByteArray())

    @Test fun `a page carries items, total and page size, with counts sent as text`() {
        val p = VodCatalogParser.page(js("""{"total_items":"2066","max_page_items":14,"data":[
            {"id":"1","name":"One","cmd":"/media/1.mpg","year":"2026","is_series":"0"},
            {"id":"2","name":"Two","is_series":"1"}]}"""), page = 5)
        assertNotNull(p)
        assertEquals(5, p.page); assertEquals(2066, p.totalItems); assertEquals(14, p.pageSize)
        assertEquals(listOf("One", "Two"), p.items.map { it.name })
        assertFalse(p.items[0].isSeries); assertTrue(p.items[1].isSeries)
        assertTrue(p.hasMore(loadedSoFar = 14)); assertFalse(p.hasMore(loadedSoFar = 2066))
    }

    @Test fun `metadata fields are read, zero and blank ratings are hidden`() {
        val item = VodItem.listFrom(js("""{"data":[{"id":"9","name":"Nine","cmd":"/m/9",
            "year":"2024","description":" A story. ","actors":"A, B","director":"D","genres_str":"Drama, Thriller",
            "rating_imdb":"7.5","rating_kinopoisk":"0","rating_mpaa":"PG-13","screenshot_uri":"http://img.invalid/9.jpg"}]}"""))[0]
        assertEquals("2024", item.year); assertEquals("A story.", item.description)
        assertEquals("A, B", item.actors); assertEquals("D", item.director); assertEquals("Drama, Thriller", item.genres)
        assertEquals(7.5, item.imdbRating); assertNull(item.kinopoiskRating)
        assertEquals("PG-13", item.age); assertEquals("http://img.invalid/9.jpg", item.posterUri)
        assertFalse(item.toString().contains("/m/9"), "cmd must stay out of toString")
    }

    @Test fun `the shown rating is IMDb when there is one, else Kinopoisk labelled as such, else none`() {
        fun item(body: String) = VodItem.listFrom(js("""{"data":[{"id":"1","name":"A",$body}]}"""))[0]
        assertEquals(ShownRating(7.5, "IMDb"), item(""""rating_imdb":"7.5","rating_kinopoisk":"6.1"""").shownRating)
        assertEquals(ShownRating(6.1, "Kinopoisk"), item(""""rating_imdb":"0","rating_kinopoisk":"6.1"""").shownRating)
        assertNull(item(""""rating_imdb":"0","rating_kinopoisk":"0"""").shownRating)
        assertNull(item(""""name2":"x"""").shownRating)
    }

    @Test fun `ratings outside 0 to 10 or not numbers are hidden`() {
        assertEquals(8.1, PortalText.rating("8.1")); assertEquals(10.0, PortalText.rating(" 10 "))
        listOf(null, "", "0", "0.0", "N/A", "11", "-1").forEach { assertNull(PortalText.rating(it), it.toString()) }
    }

    @Test fun `the poster falls back from screenshot_uri to pic, and the age to the age field`() {
        val item = VodItem.listFrom(js("""{"data":[{"id":"1","name":"A","pic":"/posters/1.jpg","age":"16+"}]}"""))[0]
        assertEquals("/posters/1.jpg", item.posterUri); assertEquals("16+", item.age)
    }

    @Test fun `entities in labels are decoded`() {
        assertEquals("HULU | ENGLISH SERIES", PortalText.decodeEntities("HULU &#124; ENGLISH SERIES"))
        assertEquals("Tom & Jerry's \"Best\"", PortalText.decodeEntities("Tom &amp; Jerry&#39;s &quot;Best&quot;"))
        assertEquals("Aé", PortalText.decodeEntities("A&#xE9;"))
        assertEquals("plain text", PortalText.decodeEntities("plain text"))
        assertEquals("&#99999999;", PortalText.decodeEntities("&#99999999;"))   // out of range: left as is
    }

    @Test fun `a payload that is not an object gives no page`() {
        assertNull(VodCatalogParser.page(null, 1)); assertNull(VodCatalogParser.page(js("[1]"), 1))
    }
}
