package io.github.ravindren_sm.openstream.provider

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import javax.crypto.KeyGenerator
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for the M2 building blocks: sealed storage, settings validation, pacing, session manager. */
class AppFoundationTest {

    // ---- AesGcmBox ------------------------------------------------------------------------

    private fun newBox() = AesGcmBox(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())

    @Test fun `seal then open returns the plaintext`() {
        val box = newBox()
        val plain = "http://portal.example.test/c/".toByteArray()
        assertContentEquals(plain, box.open(box.seal(plain)))
    }

    @Test fun `sealing twice gives different output`() {
        val box = newBox()
        val a = box.seal("same".toByteArray()); val b = box.seal("same".toByteArray())
        assertFalse(a.contentEquals(b))
    }

    @Test fun `plaintext does not appear in the sealed bytes`() {
        val box = newBox()
        val sealed = box.seal("portal.example.test".toByteArray())
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("portal.example.test"))
    }

    @Test fun `tampering, wrong key and wrong associated data are rejected without leaking`() {
        val box = newBox()
        val sealed = box.seal("secret value".toByteArray(), "settings-v1".toByteArray())
        val tampered = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        for (bad in listOf<() -> Unit>(
            { box.open(tampered, "settings-v1".toByteArray()) },
            { newBox().open(sealed, "settings-v1".toByteArray()) },
            { box.open(sealed, "other".toByteArray()) },
            { box.open(ByteArray(5)) },
        )) {
            val e = assertFailsWith<SealedBoxException> { bad() }
            assertFalse(e.message!!.contains("secret value"))
        }
        assertContentEquals("secret value".toByteArray(), box.open(sealed, "settings-v1".toByteArray()))
    }

    // ---- PortalSettings -------------------------------------------------------------------

    @Test fun `settings accept common MAC spellings and normalize them`() {
        for (m in listOf("00:1a:79:12:34:56", "00-1A-79-12-34-56", "001a79123456", " 00:1A:79:12:34:56 ")) {
            val r = PortalSettings.validate("http://portal.example.test/stalker_portal/c/", m) as PortalSettings.Result.Valid
            assertEquals("00:1A:79:12:34:56", r.settings.mac)
        }
    }

    @Test fun `settings problems are specific and fixed messages`() {
        fun problems(url: String, mac: String) =
            (PortalSettings.validate(url, mac) as PortalSettings.Result.Invalid).problems
        assertEquals(listOf(PortalSettings.Problem.URL_EMPTY, PortalSettings.Problem.MAC_EMPTY), problems("", " "))
        assertEquals(listOf(PortalSettings.Problem.URL_BAD_SCHEME), problems("ftp://h.test/", "00:1A:79:12:34:56"))
        assertEquals(listOf(PortalSettings.Problem.URL_BAD_SCHEME), problems("portal.example.test", "00:1A:79:12:34:56"))
        assertEquals(listOf(PortalSettings.Problem.MAC_BAD), problems("http://h.test/c/", "00:1A:79:12:34"))
        assertEquals(listOf(PortalSettings.Problem.MAC_BAD), problems("http://h.test/c/", "ZZ:1A:79:12:34:56"))
        // Problem messages never echo what the user typed.
        PortalSettings.Problem.entries.forEach { assertFalse(it.message.contains("h.test")) }
    }

    @Test fun `non-standard host names are accepted and cleartext is flagged`() {
        val r = PortalSettings.validate("http://my_portal.example.test:8080/c/", "00:1A:79:12:34:56") as PortalSettings.Result.Valid
        assertTrue(r.settings.isCleartext)
        val https = PortalSettings.validate("https://p.example.test/c/", "00:1A:79:12:34:56") as PortalSettings.Result.Valid
        assertFalse(https.settings.isCleartext)
    }

    @Test fun `settings never print their values and survive a sealed round trip`() {
        val s = (PortalSettings.validate("http://portal.example.test/c/", "00:1A:79:12:34:56") as PortalSettings.Result.Valid).settings
        assertFalse(s.toString().contains("portal.example.test") || s.toString().contains("00:1A"))
        val box = newBox()
        val back = PortalSettings.fromBytes(box.open(box.seal(s.toBytes())))
        assertNotNull(back)
        assertEquals(s.portalUrl, back.portalUrl); assertEquals(s.mac, back.mac)
        assertNull(PortalSettings.fromBytes("not json".toByteArray()))
        assertNull(PortalSettings.fromBytes("""{"portalUrl":"","mac":"x"}""".toByteArray()))
        // The redactor built from the settings masks both values.
        val redactor = Redactor(s.secrets())
        val out = redactor.redact("GET http://portal.example.test/c/ for 00:1A:79:12:34:56")
        assertFalse(out.contains("portal.example.test") || out.contains("00:1A:79"), out)
    }

    @Test fun `masked display values hide most of the host and MAC`() {
        val s = (PortalSettings.validate("http://my_portal.example.test:8080/c/", "00:1A:79:12:34:56") as PortalSettings.Result.Valid).settings
        assertEquals("http://•••.test:8080", s.maskedUrl())
        assertEquals("••:••:••:••:34:56", s.maskedMac())
        assertFalse(s.maskedUrl().contains("portal") || s.maskedMac().contains("1A"))
    }

    // ---- RequestPacer ---------------------------------------------------------------------

    @Test fun `pacer spaces requests but has no count cap`() {
        var now = 0L; val sleeps = mutableListOf<Long>()
        val pacer = RequestPacer(350, { now }, { sleeps += it; now += it })
        repeat(50) { pacer.acquire() }
        assertEquals(49, sleeps.size)
        assertTrue(sleeps.all { it == 350L })
        now += 10_000; pacer.acquire()
        assertEquals(49, sleeps.size)
    }

    // ---- SessionManager -------------------------------------------------------------------

    private lateinit var server: MockWebServer
    private var clock = 0L
    @BeforeTest fun start() { server = MockWebServer().apply { start() } }
    @AfterTest fun stop() { server.shutdown() }

    private fun json(body: String) = MockResponse().setResponseCode(200).setBody(body)
    private fun session() = SessionManager(
        StalkerClient(server.url("/stalker_portal/c/").toString(), "00:1A:79:00:00:01", HttpProbe(RequestPacer(0)), StalkerIdentity.HONEST),
        nowMillis = { clock },
    )

    @Test fun `start needs a token and an authorized profile`() {
        server.enqueue(json("""{"js":{"token":"t0k3n"}}"""))
        server.enqueue(json("""{"js":{"status":0,"blocked":"0"}}"""))
        val s = session()
        assertEquals(SessionManager.State.READY, s.start())

        server.enqueue(json("""{"js":{"token":"t0k3n"}}"""))
        server.enqueue(json("""{"js":{"status":2}}"""))
        val denied = session()
        assertEquals(SessionManager.State.FAILED, denied.start())
        assertEquals("account not authorized (status 2)", denied.failure)

        server.enqueue(MockResponse().setResponseCode(500))
        val down = session()
        assertEquals(SessionManager.State.FAILED, down.start())
        assertTrue(down.failure!!.startsWith("handshake failed"))
    }

    @Test fun `the session records the account expiry from the profile`() {
        server.enqueue(json("""{"js":{"token":"t0k3n"}}"""))
        server.enqueue(json("""{"js":{"status":0,"expire_billing_date":"2027-11-04 19:14:32"}}"""))
        val s = session(); s.start()
        assertEquals("2027-11-04 19:14:32", s.accountExpiry)
        server.enqueue(json("""{"js":{"token":"t0k3n"}}"""))
        server.enqueue(json("""{"js":{"status":0,"expire_billing_date":null}}"""))
        val none = session(); none.start()
        assertNull(none.accountExpiry)
    }

    @Test fun `an expired session is re-authenticated once and the call runs again`() {
        server.enqueue(json("""{"js":{"token":"OLD1"}}"""));  server.enqueue(json("""{"js":{"status":0}}"""))   // start
        server.enqueue(MockResponse().setBody("Authorization failed."))                                          // genres: expired
        server.enqueue(json("""{"js":{"token":"NEW2"}}"""));  server.enqueue(json("""{"js":{"status":0}}"""))   // re-auth
        server.enqueue(json("""{"js":[{"id":"1","title":"News"}]}"""))                                          // genres again
        val s = session(); s.start()
        val r = s.call { it.genres() }
        assertTrue(r.ok)
        repeat(4) { server.takeRequest() }
        server.takeRequest()                                         // the retried genres request
        assertEquals(6, server.requestCount)
    }

    @Test fun `re-authentication is not attempted again within the gap`() {
        server.enqueue(json("""{"js":{"token":"OLD1"}}"""));  server.enqueue(json("""{"js":{"status":0}}"""))
        server.enqueue(MockResponse().setBody("Authorization failed."))
        server.enqueue(MockResponse().setResponseCode(500))          // re-auth handshake fails
        val s = session(); s.start()
        val first = s.call { it.genres() }
        assertTrue(first.authFailed)
        assertEquals(SessionManager.State.FAILED, s.state)
        val before = server.requestCount

        server.enqueue(MockResponse().setBody("Authorization failed."))
        clock += 5_000                                              // inside the 30 s gap
        val second = s.call { it.genres() }
        assertTrue(second.authFailed)
        assertEquals(before + 1, server.requestCount)               // only the call itself: no handshake
    }

    @Test fun `keep-alive uses the watchdog and other failures are not retried`() {
        server.enqueue(json("""{"js":{"token":"t0k3n"}}"""));  server.enqueue(json("""{"js":{"status":0}}"""))
        server.enqueue(json("""{"js":{"data":{"msgs":0}}}"""))
        val s = session(); s.start()
        assertTrue(s.keepAlive().ok)
        server.takeRequest(); server.takeRequest()
        assertEquals("get_events", server.takeRequest().requestUrl!!.queryParameter("action"))

        server.enqueue(MockResponse().setResponseCode(503))
        val failed = s.keepAlive()
        assertFalse(failed.ok)
        assertEquals(4, server.requestCount)                        // 2 start + 2 watchdog, no re-auth, no retry
        assertEquals(120_000L, SessionManager.WATCHDOG_INTERVAL_MILLIS)
    }
}
