package io.github.ravindren_sm.openstream

import io.github.ravindren_sm.openstream.data.CatalogResult
import io.github.ravindren_sm.openstream.data.LiveCatalog
import io.github.ravindren_sm.openstream.data.LivePlayback
import io.github.ravindren_sm.openstream.data.PagedTitles
import io.github.ravindren_sm.openstream.data.PortalSession
import io.github.ravindren_sm.openstream.data.PosterUrl
import io.github.ravindren_sm.openstream.data.ResumePolicy
import io.github.ravindren_sm.openstream.data.SessionStatus
import io.github.ravindren_sm.openstream.data.StreamLink
import io.github.ravindren_sm.openstream.data.TimeText
import io.github.ravindren_sm.openstream.data.VodCatalog
import io.github.ravindren_sm.openstream.data.VodCategory
import io.github.ravindren_sm.openstream.data.VodKind
import io.github.ravindren_sm.openstream.data.VodPlayback
import io.github.ravindren_sm.openstream.provider.HttpProbe
import io.github.ravindren_sm.openstream.provider.PortalSettings
import io.github.ravindren_sm.openstream.provider.RequestPacer
import io.github.ravindren_sm.openstream.provider.SessionManager
import io.github.ravindren_sm.openstream.provider.StalkerClient
import io.github.ravindren_sm.openstream.provider.StalkerIdentity
import io.github.ravindren_sm.openstream.provider.VodItem
import io.github.ravindren_sm.openstream.provider.VodPage
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** VOD catalog and playback against a local fake portal (35 movies, 14 per page), plus the pure rules. */
class VodCatalogTest {
    private lateinit var server: MockWebServer
    private lateinit var session: PortalSession
    private lateinit var catalog: VodCatalog
    private val listRequests = CopyOnWriteArrayList<String>()
    private val linkRequests = CopyOnWriteArrayList<String>()

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private fun pageBody(category: String, page: Int): String {
        val from = (page - 1) * 14 + 1
        val to = minOf(page * 14, 35)
        val data = (from..to).joinToString(",") { """{"id":"$category-$it","name":"Movie $it","cmd":"/media/$it.mpg","is_series":"0","rating_imdb":"7.$it","year":"2020"}""" }
        return """{"js":{"total_items":"35","max_page_items":14,"data":[$data]}}"""
    }

    @Before fun setUp() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val q = request.path.orEmpty()
                    return when {
                        "handshake" in q -> ok("""{"js":{"token":"t0k3n"}}""")
                        "get_profile" in q -> ok("""{"js":{"status":0}}""")
                        "get_categories" in q -> ok("""{"js":[{"id":"*","title":"All"},{"id":"10","title":"ENGLISH MOVIES 4K"},{"id":"20","title":"HULU &#124; ENGLISH SERIES"},{"id":"30","title":"HINDI MOVIES"},{"id":"40","title":"HINDI WEB SERIES"}]}""")
                        "action=create_link" in q -> { linkRequests += q; ok("""{"js":{"cmd":"http://stream.invalid/vod/1.m3u8?token=abc","id":"1"}}""") }
                        "get_ordered_list" in q -> {
                            listRequests += q
                            val page = Regex("[?&]p=(\\d+)").find(q)?.groupValues?.get(1)?.toInt() ?: 1
                            val cat = Regex("[?&]category=([^&]+)").find(q)?.groupValues?.get(1) ?: "*"
                            ok(pageBody(cat, page))
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start()
        }
        session = PortalSession(newManager = { s ->
            SessionManager(StalkerClient(s.portalUrl, s.mac, HttpProbe(RequestPacer(0)), StalkerIdentity.HONEST))
        }, intervalMillis = 60_000)
        catalog = VodCatalog(session)
    }

    @After fun tearDown() { session.shutdown(); server.shutdown() }

    private fun connect() {
        val s = (PortalSettings.validate(server.url("/stalker_portal/c/").toString(), "00:1A:79:00:00:01") as PortalSettings.Result.Valid).settings
        session.configure(s); session.onForeground()
        val end = System.nanoTime() + 5_000_000_000L
        while (session.status !is SessionStatus.Ready) { check(System.nanoTime() < end) { "timed out connecting" }; Thread.sleep(10) }
    }

    @Test fun `categories split into movies and shows, drop All, and decode entities`() {
        connect()
        val movies = (catalog.categories(VodKind.MOVIES) as CatalogResult.Ok).value
        val shows = (catalog.categories(VodKind.SHOWS) as CatalogResult.Ok).value
        assertEquals(listOf("ENGLISH MOVIES 4K", "HINDI MOVIES"), movies.map { it.title })
        assertEquals(listOf("HULU | ENGLISH SERIES", "HINDI WEB SERIES"), shows.map { it.title })
        assertTrue((movies + shows).none { it.id == "*" })
    }

    @Test fun `category kind follows the title`() {
        assertEquals(VodKind.SHOWS, VodCategory.kindOf("ENGLISH SERIES"))
        assertEquals(VodKind.SHOWS, VodCategory.kindOf("Hindi TV Shows"))
        assertEquals(VodKind.SHOWS, VodCategory.kindOf("Season Pass"))
        assertEquals(VodKind.MOVIES, VodCategory.kindOf("ENGLISH MOVIES 4K"))
        assertEquals(VodKind.SHOWS, VodCategory.kindOf("HINDI DUB | K-DRAMAS"))   // found on the real portal
        assertEquals(VodKind.SHOWS, VodCategory.kindOf("ENGLISH SERIALS"))
        assertEquals(VodKind.MOVIES, VodCategory.kindOf("ANIME | MOVIES"))
        assertEquals(VodKind.MOVIES, VodCategory.kindOf("ENGLISH DOCU | MOVIES"))
        assertEquals(VodKind.MOVIES, VodCategory.kindOf("DOCUDRAMA MOVIES"))      // "docudrama" is not "drama"
        assertEquals(VodKind.MOVIES, VodCategory.kindOf("KIDS"))
        assertEquals(VodKind.MOVIES, VodCategory.kindOf("SHOWTIME MOVIES"))     // "showtime" is not "show"
    }

    @Test fun `titles page one server page at a time, with ratings, and stop at the total`() {
        connect()
        val paged = PagedTitles { page -> catalog.titles("10", page) }
        paged.loadNext(); assertEquals(14, paged.items.size); assertEquals(35, paged.total)
        paged.loadNext(); paged.loadNext()
        assertEquals(35, paged.items.size); assertTrue(paged.exhausted)
        paged.loadNext()
        assertEquals(3, listRequests.size)
        assertTrue(listRequests.all { "category=10" in it })
        assertEquals(7.1, paged.items[0].imdbRating)
    }

    @Test fun `a failed titles call keeps what was loaded and retries the same page`() {
        connect()
        var fail = false
        val paged = PagedTitles { page -> if (fail && page == 2) CatalogResult.Failed(VodCatalog.NO_TITLES) else catalog.titles("10", page) }
        paged.loadNext(); fail = true; paged.loadNext()
        assertEquals(VodCatalog.NO_TITLES, paged.error); assertEquals(14, paged.items.size)
        fail = false; paged.loadNext()
        assertNull(paged.error); assertEquals(28, paged.items.size)
    }

    @Test fun `a title repeated across pages is kept once`() {
        fun item(id: String) = VodItem(id, "T$id", false, false, false, "/m/$id", emptyList())
        val pages = mapOf(1 to VodPage(1, listOf(item("a"), item("b")), 4, 2), 2 to VodPage(2, listOf(item("b"), item("c")), 4, 2), 3 to VodPage(3, listOf(item("d")), 4, 2))
        val paged = PagedTitles { CatalogResult.Ok(pages.getValue(it)) }
        paged.loadNext(); paged.loadNext(); paged.loadNext()
        assertEquals(listOf("a", "b", "c", "d"), paged.items.map { it.id }); assertTrue(paged.exhausted)
    }

    @Test fun `a movie resolves to its stream address and keeps it out of toString`() {
        connect()
        val movie = VodItem("1", "One", false, false, false, "/media/1.mpg", emptyList())
        val link = VodPlayback(session).resolve(movie) as StreamLink.Ok
        assertEquals("http://stream.invalid/vod/1.m3u8?token=abc", link.url)
        assertTrue(linkRequests.single().let { "type=vod" in it && "action=create_link" in it })
        assertFalse(link.toString().contains("stream.invalid"))
    }

    @Test fun `a title without a cmd, and a disconnected session, fail with fixed text`() {
        val noCmd = VodItem("1", "One", false, false, false, null, emptyList())
        assertEquals(LiveCatalog.NOT_CONNECTED, (catalog.titles("10", 1) as CatalogResult.Failed).message)
        assertEquals(LivePlayback.NO_ADDRESS, (VodPlayback(session).resolve(noCmd) as StreamLink.Failed).message)
        assertEquals(LiveCatalog.NOT_CONNECTED, (VodPlayback(session).resolve(VodItem("1", "One", false, false, false, "/m", emptyList())) as StreamLink.Failed).message)
    }

    // ---- pure rules ----

    @Test fun `resume starts from a meaningful saved position only`() {
        val hour = 3_600_000L
        assertEquals(0L, ResumePolicy.startPosition(null, hour))
        assertEquals(0L, ResumePolicy.startPosition(10_000, hour))            // barely started
        assertEquals(600_000L, ResumePolicy.startPosition(600_000, hour))
        assertEquals(0L, ResumePolicy.startPosition(hour - 30_000, hour))     // near the end: finished
        assertEquals(hour - 60_000, ResumePolicy.startPosition(hour - 60_000, hour))
        assertEquals(600_000L, ResumePolicy.startPosition(600_000, 0))        // unknown duration
    }

    @Test fun `positions are saved only in the middle of a title`() {
        val hour = 3_600_000L
        assertFalse(ResumePolicy.shouldSave(5_000, hour)); assertTrue(ResumePolicy.shouldSave(900_000, hour))
        assertFalse(ResumePolicy.shouldSave(hour - 10_000, hour)); assertTrue(ResumePolicy.shouldSave(900_000, 0))
    }

    @Test fun `poster addresses are resolved against the portal host`() {
        val portal = "http://portal.invalid:8080/stalker_portal/c/"
        assertEquals("http://img.invalid/a.jpg", PosterUrl.resolve("http://img.invalid/a.jpg", portal))
        assertEquals("http://portal.invalid:8080/misc/a.jpg", PosterUrl.resolve("/misc/a.jpg", portal))
        assertEquals("http://img.invalid/a.jpg", PosterUrl.resolve("//img.invalid/a.jpg", portal))
        assertNull(PosterUrl.resolve("a.jpg", portal)); assertNull(PosterUrl.resolve("  ", portal)); assertNull(PosterUrl.resolve(null, portal))
    }

    @Test fun `times show as minutes and seconds, or hours when long`() {
        assertEquals("0:00", TimeText.format(0)); assertEquals("0:09", TimeText.format(9_999))
        assertEquals("12:34", TimeText.format(754_000)); assertEquals("1:06:40", TimeText.format(4_000_000))
        assertEquals("0:00", TimeText.format(-5_000))
    }
}
