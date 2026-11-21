package io.github.ravindren_sm.openstream

import io.github.ravindren_sm.openstream.data.PortalSession
import io.github.ravindren_sm.openstream.data.SessionStatus
import io.github.ravindren_sm.openstream.provider.HttpProbe
import io.github.ravindren_sm.openstream.provider.PortalSettings
import io.github.ravindren_sm.openstream.provider.RequestPacer
import io.github.ravindren_sm.openstream.provider.SessionManager
import io.github.ravindren_sm.openstream.provider.StalkerClient
import io.github.ravindren_sm.openstream.provider.StalkerIdentity
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PortalSessionTest {
    private lateinit var server: MockWebServer
    private val watchdogs = AtomicInteger()
    private val handshakes = AtomicInteger()
    @Volatile private var refuse = false
    private val expireNext = java.util.concurrent.atomic.AtomicBoolean(false)
    private lateinit var session: PortalSession

    @Before fun setUp() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val q = request.path.orEmpty()
                    if (refuse && "handshake" in q) { handshakes.incrementAndGet(); return MockResponse().setResponseCode(403) }
                    return when {
                        "handshake" in q -> { handshakes.incrementAndGet(); ok("""{"js":{"token":"t0k3n"}}""") }
                        "get_profile" in q -> ok("""{"js":{"status":0,"expire_billing_date":"2027-11-04 19:14:32"}}""")
                        // The portal's expired-session reply: plain text, no "js" payload.
                        "get_events" in q && expireNext.getAndSet(false) -> ok("Authorization failed.")
                        "get_events" in q -> { watchdogs.incrementAndGet(); ok("""{"js":{"data":{"msgs":0}}}""") }
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start()
        }
        session = PortalSession(newManager = { s ->
            SessionManager(StalkerClient(s.portalUrl, s.mac, HttpProbe(RequestPacer(0)), StalkerIdentity.HONEST))
        }, intervalMillis = 100)
    }

    @After fun tearDown() { session.shutdown(); server.shutdown() }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)
    private fun settings() = (PortalSettings.validate(server.url("/stalker_portal/c/").toString(), "00:1A:79:00:00:01") as PortalSettings.Result.Valid).settings
    private fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.nanoTime() + 5_000_000_000L
        while (!cond()) { check(System.nanoTime() < end) { "timed out waiting for $what" }; Thread.sleep(10) }
    }

    @Test fun `connects in the foreground and reports the account end date`() {
        session.configure(settings()); session.onForeground()
        waitFor("ready") { session.status is SessionStatus.Ready }
        assertEquals("2027-11-04", (session.status as SessionStatus.Ready).expiry)
    }

    @Test fun `sends keep-alives while in the foreground`() {
        session.configure(settings()); session.onForeground()
        waitFor("three keep-alives") { watchdogs.get() >= 3 }
        assertTrue(session.status is SessionStatus.Ready)
    }

    @Test fun `goes quiet in the background and reconnects on return`() {
        session.configure(settings()); session.onForeground()
        waitFor("a keep-alive") { watchdogs.get() >= 1 }
        session.onBackground()
        waitFor("idle") { session.status == SessionStatus.Idle }
        Thread.sleep(150)                       // let any in-flight tick finish
        val before = watchdogs.get(); Thread.sleep(400)
        assertEquals("no keep-alives while in the background", before, watchdogs.get())
        session.onForeground()
        waitFor("ready again") { session.status is SessionStatus.Ready }
        assertEquals(2, handshakes.get())
    }

    @Test fun `does nothing without a configured portal`() {
        session.onForeground(); Thread.sleep(300)
        assertEquals(SessionStatus.Idle, session.status)
        assertEquals(0, handshakes.get())
    }

    @Test fun `a refused handshake shows a fixed failure message and sends no keep-alives`() {
        refuse = true
        session.configure(settings()); session.onForeground()
        waitFor("failed") { session.status is SessionStatus.Failed }
        val msg = (session.status as SessionStatus.Failed).message
        assertTrue(msg, "403" in msg)
        Thread.sleep(400)
        assertEquals(0, watchdogs.get())
    }

    @Test fun `an expired session is re-authenticated by the keep-alive and stays connected`() {
        session.configure(settings()); session.onForeground()
        waitFor("a keep-alive") { watchdogs.get() >= 1 }
        val before = watchdogs.get()
        expireNext.set(true)
        waitFor("a second handshake and a repeated keep-alive") { handshakes.get() >= 2 && watchdogs.get() > before }
        waitFor("ready again") { session.status is SessionStatus.Ready }
        assertEquals(2, handshakes.get())
    }

    @Test fun `a refused re-authentication shows a failure and does not hammer the portal`() {
        session.configure(settings()); session.onForeground()
        waitFor("a keep-alive") { watchdogs.get() >= 1 }
        refuse = true            // the re-handshake will get 403
        expireNext.set(true)
        waitFor("failed") { session.status is SessionStatus.Failed }
        Thread.sleep(400)        // several more keep-alive intervals (100 ms in this test)
        assertEquals("one re-handshake attempt, no more", 2, handshakes.get())
        assertTrue(session.status is SessionStatus.Failed)
    }
}
