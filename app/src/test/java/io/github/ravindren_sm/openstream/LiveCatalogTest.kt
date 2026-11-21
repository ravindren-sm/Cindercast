package io.github.ravindren_sm.openstream

import io.github.ravindren_sm.openstream.data.CatalogResult
import io.github.ravindren_sm.openstream.data.LiveCatalog
import io.github.ravindren_sm.openstream.data.PagedChannels
import io.github.ravindren_sm.openstream.data.PortalSession
import io.github.ravindren_sm.openstream.data.SessionStatus
import io.github.ravindren_sm.openstream.provider.HttpProbe
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.LiveChannelPage
import io.github.ravindren_sm.openstream.provider.PortalSettings
import io.github.ravindren_sm.openstream.provider.RequestPacer
import io.github.ravindren_sm.openstream.provider.SessionManager
import io.github.ravindren_sm.openstream.provider.StalkerClient
import io.github.ravindren_sm.openstream.provider.StalkerIdentity
import io.github.ravindren_sm.openstream.ui.screens.LiveBrowseState
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

/** Live catalog and paging against a local fake portal (40 channels, 14 per page). Nothing reaches the provider. */
class LiveCatalogTest {
    private lateinit var server: MockWebServer
    private lateinit var session: PortalSession
    private lateinit var catalog: LiveCatalog
    private val channelRequests = CopyOnWriteArrayList<String>()
    @Volatile private var failPage = -1

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private fun channelsBody(page: Int): String {
        val from = (page - 1) * 14 + 1
        val to = minOf(page * 14, 40)
        val data = (from..to).joinToString(",") { """{"id":"$it","number":"$it","name":"Channel $it","cmd":"ffmpeg http://x/$it"}""" }
        return """{"js":{"total_items":"40","max_page_items":14,"data":[$data]}}"""
    }

    @Before fun setUp() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val q = request.path.orEmpty()
                    return when {
                        "handshake" in q -> ok("""{"js":{"token":"t0k3n"}}""")
                        "get_profile" in q -> ok("""{"js":{"status":0,"expire_billing_date":"2027-11-04 19:14:32"}}""")
                        "get_events" in q -> ok("""{"js":{"data":{"msgs":0}}}""")
                        "get_genres" in q -> ok("""{"js":[{"id":"1","title":"News"},{"id":"2","title":"Sports"}]}""")
                        "get_ordered_list" in q -> {
                            channelRequests += q
                            val page = Regex("[?&]p=(\\d+)").find(q)?.groupValues?.get(1)?.toInt() ?: 1
                            if (page == failPage) MockResponse().setResponseCode(500) else ok(channelsBody(page))
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
        catalog = LiveCatalog(session)
    }

    @After fun tearDown() { session.shutdown(); server.shutdown() }

    private fun connect() {
        val settings = (PortalSettings.validate(server.url("/stalker_portal/c/").toString(), "00:1A:79:00:00:01") as PortalSettings.Result.Valid).settings
        session.configure(settings); session.onForeground()
        val end = System.nanoTime() + 5_000_000_000L
        while (session.status !is SessionStatus.Ready) { check(System.nanoTime() < end) { "timed out connecting" }; Thread.sleep(10) }
    }

    @Test fun `genres start with All channels and keep the portal's own`() {
        connect()
        val r = catalog.genres() as CatalogResult.Ok
        assertEquals(listOf("*", "1", "2"), r.value.map { it.id })
        assertEquals("All channels", r.value[0].title)
    }

    @Test fun `without a connected session the catalog fails with fixed text`() {
        val r = catalog.genres() as CatalogResult.Failed
        assertEquals(LiveCatalog.NOT_CONNECTED, r.message)
        assertTrue(catalog.channels("*", 1) is CatalogResult.Failed)
    }

    @Test fun `paging loads one server page at a time and stops at the total`() {
        connect()
        val paged = PagedChannels { page -> catalog.channels("2", page) }
        paged.loadNext()
        assertEquals(14, paged.items.size); assertEquals(40, paged.total); assertFalse(paged.exhausted)
        paged.loadNext(); paged.loadNext()
        assertEquals(40, paged.items.size)
        assertTrue(paged.exhausted)
        assertEquals((1..40).map { "Channel $it" }, paged.items.map { it.name })

        paged.loadNext()                                  // finished: no further request
        assertEquals(3, channelRequests.size)
        assertTrue(channelRequests.all { "genre=2" in it })
        assertEquals(listOf("p=1", "p=2", "p=3"), channelRequests.map { Regex("p=\\d+").find(it)!!.value })
    }

    @Test fun `a failed page keeps what was loaded and a retry loads the same page`() {
        connect()
        val paged = PagedChannels { page -> catalog.channels("*", page) }
        paged.loadNext()
        failPage = 2
        paged.loadNext()
        assertEquals(LiveCatalog.NO_CHANNELS, paged.error)
        assertEquals(14, paged.items.size)
        assertFalse(paged.loading)

        failPage = -1
        paged.loadNext()
        assertNull(paged.error)
        assertEquals(28, paged.items.size)
        assertEquals("Channel 15", paged.items[14].name)  // page 2 starts where page 1 ended
    }

    @Test fun `browse state selects All channels first and switching genre restarts at page 1`() {
        connect()
        val browse = LiveBrowseState(catalog)
        browse.loadGenres()
        assertTrue(browse.genresLoaded)
        assertEquals(0, browse.selected)
        browse.channels.loadNext()
        assertEquals(14, browse.channels.items.size)

        browse.select(2, resetScroll = false)             // index 2 is the portal's genre "2"
        assertEquals(2, browse.selected)
        assertEquals(0, browse.channels.items.size)
        browse.channels.loadNext()
        assertTrue(channelRequests.last().let { "genre=2" in it && "p=1" in it })
    }

    // ---- PagedChannels on its own (no portal) ----

    private fun ch(id: Int) = LiveChannel("$id", "$id", "C$id", null)

    @Test fun `a channel repeated across pages is kept once`() {
        val pages = mapOf(
            1 to LiveChannelPage(1, listOf(ch(1), ch(2)), totalItems = 4, pageSize = 2),
            2 to LiveChannelPage(2, listOf(ch(2), ch(3)), totalItems = 4, pageSize = 2),
            3 to LiveChannelPage(3, listOf(ch(4)), totalItems = 4, pageSize = 2),
        )
        val paged = PagedChannels { CatalogResult.Ok(pages.getValue(it)) }
        paged.loadNext(); paged.loadNext(); paged.loadNext()
        assertEquals(listOf("1", "2", "3", "4"), paged.items.map { it.id })
        assertTrue(paged.exhausted)
    }

    @Test fun `an empty page ends paging even if the total says more`() {
        val paged = PagedChannels { page ->
            CatalogResult.Ok(if (page == 1) LiveChannelPage(1, listOf(ch(1)), 100, 1) else LiveChannelPage(2, emptyList(), 100, 1))
        }
        paged.loadNext(); assertFalse(paged.exhausted)
        paged.loadNext(); assertTrue(paged.exhausted)
        assertEquals(1, paged.items.size)
    }
}
