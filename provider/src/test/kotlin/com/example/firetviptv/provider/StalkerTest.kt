package com.example.firetviptv.provider

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
        server.enqueue(json("""{"js":{"token":"ABCDEF0123456789ABCDEF0123456789","random":"x"}}"""))
        server.enqueue(json("""{"js":{"status":0,"blocked":"0","ip":"203.0.113.9","fname":"A B"}}"""))
        val c = client()
        val hs = c.handshake()
        assertTrue(hs.ok)
        assertEquals("token issued: yes", hs.summary.first())
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
