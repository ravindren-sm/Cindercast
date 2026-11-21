package io.github.ravindren_sm.openstream.provider

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stalker client against a fake portal on 127.0.0.1 (MockWebServer). All data is fake;
 * nothing here reaches the provider.
 */
class StalkerTest {

    private lateinit var server: MockWebServer
    private var now = 0L
    private val mac = "00:1A:79:00:00:01"

    @BeforeTest fun start() { server = MockWebServer().apply { start() } }
    @AfterTest fun stop() { server.shutdown() }

    private fun portalUrl() = server.url("/stalker_portal/c/").toString()
    private fun client(identity: StalkerIdentity = StalkerIdentity.HONEST, max: Int = 10) = StalkerClient(
        portalUrl(), mac,
        HttpProbe(RequestBudget(max, nowMillis = { now }, sleepMillis = { now += it }), 1_000_000),
        identity,
    )
    private fun json(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    @Test fun `api URL derivation`() {
        assertEquals("http://h.test/stalker_portal/server/load.php", StalkerRequests.apiUrl("http://h.test/stalker_portal/c/"))
        assertEquals("http://h.test:8080/portal.php", StalkerRequests.apiUrl("http://h.test:8080/c/"))
    }

    @Test fun `handshake sends MAC cookie and referer, then token is used as bearer`() {
        server.enqueue(json("""{"js":{"token":"ABCDEF0123456789ABCDEF0123456789","random":"x","not_valid":0}}"""))
        server.enqueue(json("""{"js":{"status":0,"blocked":"0","ip":"203.0.113.9","fname":"A B"}}"""))
        val c = client()
        val hs = c.handshake()
        assertTrue(hs.ok)
        assertEquals("token issued: yes", hs.summary.first())
        assertTrue(hs.summary.contains("not_valid: 0"), hs.summary.toString())
        assertFalse(hs.summary.joinToString().contains("ABCDEF0123"), "token value must never appear")
        assertFalse(hs.summary.joinToString().contains("32"), "token length must not be reported: ${hs.summary}")

        val r1 = server.takeRequest()
        assertEquals("/stalker_portal/server/load.php", r1.requestUrl!!.encodedPath)
        assertEquals("handshake", r1.requestUrl!!.queryParameter("action"))
        assertEquals("1-xml", r1.requestUrl!!.queryParameter("JsHttpRequest"))
        assertTrue(r1.getHeader("Cookie")!!.startsWith("mac=00%3A1A%3A79%3A00%3A00%3A01; stb_lang=en; timezone="))
        assertEquals(portalUrl(), r1.getHeader("Referer"))
        assertNull(r1.getHeader("Authorization"))
        assertNull(r1.getHeader("X-User-Agent"))
        assertEquals(ClientIdentity.HONEST_USER_AGENT, r1.getHeader("User-Agent"))

        val profile = c.profile()
        val r2 = server.takeRequest()
        assertEquals("Bearer ABCDEF0123456789ABCDEF0123456789", r2.getHeader("Authorization"))
        assertEquals("get_profile", r2.requestUrl!!.queryParameter("action"))
        // Summary: whitelisted fields only; identifying fields never appear.
        val text = profile.summary.joinToString("\n")
        assertTrue(text.contains("status: 0") && text.contains("blocked: 0"), text)
        assertFalse(text.contains("203.0.113.9") || text.contains("A B"), text)
    }

    @Test fun `MAG identity adds the MAG headers`() {
        server.enqueue(json("""{"js":{"token":"t0k3n"}}"""))
        client(StalkerIdentity.MAG).handshake()
        val r = server.takeRequest()
        assertTrue(r.getHeader("User-Agent")!!.contains("MAG200"))
        assertEquals("Model: MAG250; Link: WiFi", r.getHeader("X-User-Agent"))
    }

    @Test fun `channels page, create_link and stream URL extraction`() {
        server.enqueue(json("""{"js":{"token":"t0k3n"}}"""))
        server.enqueue(json("""{"js":{"total_items":"812","max_page_items":14,"data":[
            {"id":"1","number":"1","name":"News One","cmd":"ffmpeg http://localhost/ch/1_"},
            {"id":"2","number":"2","name":"Sport Two","cmd":"ffmpeg http://localhost/ch/2_"}]}}"""))
        server.enqueue(json("""{"js":{"id":"1","cmd":"ffmpeg http://edge.example.test/play/live.php?stream=1&token=abc123"}}"""))
        val c = client()
        c.handshake()
        val (page, cmd) = c.channelsPage1()
        val pageText = page.summary.joinToString("\n")
        assertTrue(pageText.contains("total channels: 812"), pageText)
        assertTrue(pageText.contains("1 News One | 2 Sport Two"), pageText)
        assertTrue(pageText.contains("ffmpeg http localhost (needs create_link)"), pageText)
        assertEquals("ffmpeg http://localhost/ch/1_", cmd)

        val (link, url) = c.createLink(cmd!!)
        assertEquals("http://edge.example.test/play/live.php?stream=1&token=abc123", url)
        assertTrue(link.summary.first().contains("remote host"), link.summary.toString())
        assertFalse(link.summary.joinToString().contains("edge.example.test"), "host must never be printed")
        server.takeRequest(); server.takeRequest()
        val linkReq = server.takeRequest()
        assertEquals("create_link", linkReq.requestUrl!!.queryParameter("action"))
        assertEquals("ffmpeg http://localhost/ch/1_", linkReq.requestUrl!!.queryParameter("cmd"))
    }

    @Test fun `non-JSON or error responses give a safe summary`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>Access denied</html>"))
        val r = client().handshake()
        assertFalse(r.ok)
        assertEquals(listOf("no JSON payload in the response"), r.summary)
    }

    @Test fun `create_link error field and non-standard hosts are summarized safely`() {
        assertEquals("- http remote host (non-standard name) port 8080", StalkerClient.cmdShape("http://edge_01.example.test:8080/live/1.ts?token=x"))
        assertEquals("- http remote IP address", StalkerClient.cmdShape("http://203.0.113.5/live/1.ts"))
        assertEquals("ffrt http localhost (needs create_link)", StalkerClient.cmdShape("ffrt http://localhost/ch/1_"))
        server.enqueue(json("""{"js":{"token":"t0k3n"}}"""))
        server.enqueue(json("""{"js":{"id":"1","cmd":"http://edge_01.example.test/x.ts?t=abc","error":""}}"""))
        val c = client(); c.handshake()
        val (link, _) = c.createLink("ffrt http://localhost/ch/1_")
        assertTrue(link.summary.contains("error: ''"), link.summary.toString())
        assertFalse(link.summary.joinToString().contains("edge_01"), link.summary.toString())
    }

    @Test fun `genre lookup by title and channel by name within that genre`() {
        server.enqueue(json("""{"js":{"token":"t0k3n"}}"""))
        server.enqueue(json("""{"js":[{"id":"*","title":"All"},{"id":"12","title":"English USA Local"},{"id":"13","title":"Kids"}]}"""))
        server.enqueue(json("""{"js":{"total_items":"57","max_page_items":14,"data":[
            {"number":"1","name":"NEWS 12 LONG ISLAND 4K","cmd":"ffrt http://localhost/ch/101_"},
            {"number":"2","name":"NEWS 12 CONNECTICUT","cmd":"ffrt http://localhost/ch/102_"}]}}"""))
        val c = client(); c.handshake()
        val (g, id) = c.findGenreId("ENGLISH USA LOCAL")
        assertEquals("12", id)
        assertTrue(g.summary.contains("requested genre 'ENGLISH USA LOCAL': found"), g.summary.toString())
        val (page, cmd, label) = c.channelInGenre(id!!, "news 12 long island")
        assertEquals("ffrt http://localhost/ch/101_", cmd)
        assertEquals("1 NEWS 12 LONG ISLAND 4K", label)
        assertTrue(page.summary.first().contains("channels in genre: 57"), page.summary.toString())
        server.takeRequest(); server.takeRequest()
        val listReq = server.takeRequest()
        assertEquals("12", listReq.requestUrl!!.queryParameter("genre"))
        assertEquals("get_ordered_list", listReq.requestUrl!!.queryParameter("action"))
    }

    @Test fun `missing genre or channel is reported, not guessed`() {
        server.enqueue(json("""{"js":{"token":"t0k3n"}}"""))
        server.enqueue(json("""{"js":[{"id":"1","title":"Kids"}]}"""))
        server.enqueue(json("""{"js":{"total_items":"1","data":[{"number":"9","name":"Other","cmd":"ffrt http://localhost/ch/9_"}]}}"""))
        val c = client(); c.handshake()
        assertNull(c.findGenreId("ENGLISH USA LOCAL").second)
        val (_, cmd, label) = c.channelInGenre("1", "NEWS 12")
        assertNull(cmd); assertNull(label)
    }

    @Test fun `vod categories and first page are summarized without cmd values`() {
        server.enqueue(json("""{"js":{"token":"t0k3n"}}"""))
        server.enqueue(json("""{"js":[{"id":"*","title":"All"},{"id":"5","title":"English Movies"},{"id":"9","title":"Kids"}]}"""))
        server.enqueue(json("""{"js":{"total_items":"99500","max_page_items":14,"data":[
            {"id":"1","name":"Fake Movie","year":"2024","is_series":"0","cmd":"eyJ0eXBlIjoibW92aWUiLCJzdHJlYW1faWQiOjEyM30=","series":[]},
            {"id":"2","name":"Fake Show","year":"2023","is_series":"1","cmd":"ffmpeg http://vod.example.test/series/77.mkv?token=SECRETVOD","series":[1,2,3]},
            {"id":"3","name":"Other Film","year":"2022","is_series":"0","cmd":"/media/file_3.mpg"}]}}"""))
        val c = client(); c.handshake()
        val (cats, list) = c.vodCategories("vod")
        assertEquals(listOf("*" to "All", "5" to "English Movies", "9" to "Kids"), list)
        assertTrue(cats.summary.contains("vod categories: 3") && cats.summary.contains("has All (*): yes"), cats.summary.toString())

        val page = c.vodPage("vod")
        val text = page.summary.joinToString("\n")
        assertTrue(text.contains("vod total: 99500 (page size 14)"), text)
        assertTrue(text.contains("page 1 items: 3; is_series=1: 1; with episode list: 1"), text)
        assertTrue(text.contains("Fake Movie (2024) ; Fake Show (2023)"), text)
        assertTrue(text.contains("base64-like") && text.contains("server path") && text.contains("url: ffmpeg http remote host"), text)
        listOf("eyJ0eXBl", "SECRETVOD", "vod.example.test", "/media/file_3").forEach {
            assertFalse(text.contains(it), "cmd value leaked: $it\n$text")
        }

        server.takeRequest(); server.takeRequest()
        val req = server.takeRequest()
        val u = req.requestUrl!!
        assertEquals("vod", u.queryParameter("type")); assertEquals("get_ordered_list", u.queryParameter("action"))
        assertEquals("*", u.queryParameter("category")); assertEquals("1", u.queryParameter("p"))
        assertEquals("added", u.queryParameter("sortby")); assertEquals("0", u.queryParameter("movie_id"))
        assertEquals("Bearer t0k3n", req.getHeader("Authorization"))
    }

    @Test fun `series module absent is reported, not fatal`() {
        server.enqueue(json("""{"js":{"token":"t0k3n"}}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("Unknown action"))
        val c = client(); c.handshake()
        val (r, cats) = c.vodCategories("series")
        assertFalse(r.ok); assertTrue(cats.isEmpty())
        assertEquals(listOf("no JSON payload in the response"), r.summary)
    }

    @Test fun `category picking prefers exact match, else shortest containing title`() {
        val cats = listOf("*" to "All", "11" to "HINDI DUB | ENGLISH MOVIES", "12" to "ENGLISH MOVIES 4K", "13" to "ENGLISH MOVIES")
        assertEquals("13" to "ENGLISH MOVIES", StalkerClient.pickCategory(cats, "english movies"))
        val noExact = listOf("11" to "HINDI DUB | ENGLISH SERIES", "14" to "ENGLISH SERIES HD")
        assertEquals("14" to "ENGLISH SERIES HD", StalkerClient.pickCategory(noExact, "ENGLISH SERIES"))
        assertNull(StalkerClient.pickCategory(cats, "KOREAN"))
    }

    @Test fun `vod items parse flags and never print cmd`() {
        val js = StalkerClient.parseJs("""{"js":{"data":[
            {"id":"5","name":"Show A","is_series":"1","cmd":"/media/show_a","series":[]},
            {"id":"5:1","name":"Season 1","is_season":true,"cmd":""},
            {"id":"5:1:3","name":"Episode 3","is_episode":"1","cmd":"/media/ep3.mkv"},
            {"id":"7","name":"Old Show","is_series":"1","cmd":"/media/old","series":["1","2"]}]}}""".toByteArray())
        val items = VodItem.listFrom(js)
        assertEquals(listOf(true, false, false, true), items.map { it.isSeries })
        assertTrue(items[1].isSeason && items[1].cmd == null)
        assertTrue(items[2].isEpisode && items[2].cmd == "/media/ep3.mkv")
        assertEquals(listOf("1", "2"), items[3].episodeNumbers)
        assertFalse(items.joinToString().contains("/media"), "toString must not show cmd")
    }

    @Test fun `season and episode navigation and vod create_link requests`() {
        server.enqueue(json("""{"js":{"token":"t0k3n"}}"""))
        server.enqueue(json("""{"js":{"total_items":"2","data":[{"id":"5:1","name":"Season 1","is_season":true},{"id":"5:2","name":"Season 2","is_season":true}]}}"""))
        server.enqueue(json("""{"js":{"total_items":"10","data":[{"id":"5:1:1","name":"Episode 1","is_episode":true,"cmd":"/media/e1.mkv"}]}}"""))
        server.enqueue(json("""{"js":{"cmd":"ffmpeg http://vod.example.test/e1.mkv?t=SECRETEP","error":""}}"""))
        val c = client(); c.handshake()
        val (sr, seasons) = c.vodChildren("5")
        assertEquals(listOf("5:1", "5:2"), seasons.map { it.id })
        assertTrue(sr.summary.any { it.contains("season=2") }, sr.summary.toString())
        val (er, episodes) = c.vodChildren("5", "5:1")
        assertEquals("/media/e1.mkv", episodes.single().cmd)
        assertTrue(er.summary.first().startsWith("episodes total: 10"), er.summary.toString())
        val (link, url) = c.vodCreateLink(episodes.single().cmd!!)
        assertEquals("http://vod.example.test/e1.mkv?t=SECRETEP", url)
        assertFalse(link.summary.joinToString().contains("SECRETEP") || link.summary.joinToString().contains("vod.example.test"))
        server.takeRequest()
        val s1 = server.takeRequest().requestUrl!!
        assertEquals("5", s1.queryParameter("movie_id")); assertEquals("0", s1.queryParameter("season_id"))
        val s2 = server.takeRequest().requestUrl!!
        assertEquals("5:1", s2.queryParameter("season_id"))
        val l = server.takeRequest().requestUrl!!
        assertEquals("vod", l.queryParameter("type")); assertEquals("create_link", l.queryParameter("action"))
        assertEquals("/media/e1.mkv", l.queryParameter("cmd")); assertEquals("", l.queryParameter("series"))
    }

    @Test fun `episode files level and inline series_files`() {
        server.enqueue(json("""{"js":{"token":"t0k3n"}}"""))
        server.enqueue(json("""{"js":{"total_items":"2","data":[
            {"id":"f1","name":"1080p","is_file":true,"cmd":"/media/f1.mkv"},
            {"id":"f2","name":"720p","is_file":"1","cmd":"/media/f2.mkv"}]}}"""))
        val c = client(); c.handshake()
        val (r, files) = c.vodChildren("5", "5:1", "5:1:3")
        assertEquals(listOf("/media/f1.mkv", "/media/f2.mkv"), files.map { it.playableCmd })
        assertTrue(files.all { it.isFile })
        assertTrue(r.summary.first().startsWith("files total: 2"), r.summary.toString())
        assertFalse(r.summary.joinToString().contains("/media/"), r.summary.toString())
        server.takeRequest()
        val u = server.takeRequest().requestUrl!!
        assertEquals("5", u.queryParameter("movie_id")); assertEquals("5:1", u.queryParameter("season_id"))
        assertEquals("5:1:3", u.queryParameter("episode_id"))

        val inline = VodItem.listFrom(StalkerClient.parseJs("""{"js":{"data":[
            {"id":"e1","name":"E1","is_episode":true,"series_files":[{"id":"x","cmd":"/media/e1.mkv"}]},
            {"id":"e2","name":"E2","is_episode":true,"series_files":3}]}}""".toByteArray()))
        assertEquals("/media/e1.mkv", inline[0].playableCmd)
        assertNull(inline[1].playableCmd)
        assertFalse(inline.joinToString().contains("/media"))
    }

    @Test fun `watchdog request shape and summary`() {
        server.enqueue(json("""{"js":{"token":"t0k3n"}}"""))
        server.enqueue(json("""{"js":{"data":{"msgs":0,"additional_services_on":"1","event":""}}}"""))
        val c = client(); c.handshake()
        val w = c.watchdog()
        assertTrue(w.ok && w.summary.first() == "watchdog ok", w.summary.toString())
        assertTrue(w.summary.any { it == "data keys: additional_services_on,event,msgs" }, w.summary.toString())
        server.takeRequest()
        val r = server.takeRequest()
        val u = r.requestUrl!!
        assertEquals("watchdog", u.queryParameter("type")); assertEquals("get_events", u.queryParameter("action"))
        assertEquals("0", u.queryParameter("init")); assertEquals("1", u.queryParameter("cur_play_type"))
        assertEquals("0", u.queryParameter("event_active_id")); assertEquals("Bearer t0k3n", r.getHeader("Authorization"))
    }

    @Test fun `Authorization failed is detected and reauthentication gets a new token`() {
        server.enqueue(json("""{"js":{"token":"OLDTOKEN1"}}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("Authorization failed."))
        server.enqueue(json("""{"js":{"token":"NEWTOKEN2","not_valid":0}}"""))
        server.enqueue(json("""{"js":{"status":0,"blocked":"0"}}"""))
        server.enqueue(json("""{"js":[{"id":"1","title":"News"}]}"""))
        val c = client(); c.handshake()
        val expired = c.genres()
        assertTrue(expired.authFailed && !expired.ok)
        assertEquals(listOf("portal says: Authorization failed (session expired)"), expired.summary)
        val (hs, profile) = c.reauthenticate()
        assertTrue(hs.ok && profile!!.ok && profile.field("status") == "0")
        assertTrue(c.genres().ok)
        repeat(4) { server.takeRequest() }
        assertEquals("Bearer NEWTOKEN2", server.takeRequest().getHeader("Authorization"))
        assertFalse(expired.summary.joinToString().contains("TOKEN"))
    }

    @Test fun `a normal non-JSON body is not mistaken for an auth failure`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>maintenance</html>"))
        val r = client().handshake()
        assertFalse(r.authFailed)
        assertEquals(listOf("no JSON payload in the response"), r.summary)
    }

    @Test fun `vod cmd kinds`() {
        assertEquals("server path", StalkerClient.vodCmdKind("/media/x.mpg"))
        assertTrue(StalkerClient.vodCmdKind("eyJ0eXBlIjoibW92aWUifQ==").startsWith("base64-like"))
        assertEquals("empty", StalkerClient.vodCmdKind(" "))
        assertTrue(StalkerClient.vodCmdKind("ffmpeg http://203.0.113.5/m.mp4").startsWith("url: ffmpeg http remote IP"))
    }

    @Test fun `stream signatures`() {
        val ts = ByteArray(188 * 3).also { for (i in 0 until 3) it[i * 188] = 0x47 }
        assertTrue(StalkerClient.streamSignature(ts).startsWith("MPEG-TS"))
        assertTrue(StalkerClient.streamSignature("#EXTM3U\n#EXT-X-VERSION:3".toByteArray()).startsWith("HLS"))
        assertEquals("empty", StalkerClient.streamSignature(ByteArray(0)))
    }

    @Test fun `request descriptions are leak-free`() {
        val secrets = Secrets(mapOf("PORTAL_URL" to "http://real.example.test/stalker_portal/c/", "EMU_MAC" to "00:1A:79:12:34:56"))
        val ctx = StalkerRequests.Context("http://real.example.test/stalker_portal/c/", "00:1A:79:12:34:56", token = "SECRETTOKEN12345")
        val text = listOf(StalkerRequests.handshake(ctx), StalkerRequests.profile(ctx), StalkerRequests.createLink(ctx, "ffmpeg http://localhost/ch/9_"))
            .joinToString("\n") { it.describe(secrets.redactor()) }
        listOf("real.example.test", "00:1A:79:12", "001A79123456", "00%3A1A", "SECRETTOKEN12345").forEach {
            assertFalse(text.contains(it, ignoreCase = true), "Leaked '$it':\n$text")
        }
        assertTrue(text.contains("Cookie: <REDACTED>") && text.contains("Authorization: <REDACTED>"), text)
    }
}
