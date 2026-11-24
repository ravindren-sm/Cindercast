package io.github.ravindren_sm.openstream

import io.github.ravindren_sm.openstream.data.PortalSession
import io.github.ravindren_sm.openstream.data.PortalTransport
import io.github.ravindren_sm.openstream.provider.HttpProbe
import io.github.ravindren_sm.openstream.provider.PortalSettings
import io.github.ravindren_sm.openstream.provider.ProbeOutcome
import io.github.ravindren_sm.openstream.provider.ProbeRequest
import io.github.ravindren_sm.openstream.provider.RequestPacer
import io.github.ravindren_sm.openstream.provider.StalkerIdentity
import io.github.ravindren_sm.openstream.provider.StalkerRequests
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy

/**
 * Debug logger against a local fake portal (127.0.0.1). Nothing reaches the provider.
 * These tests live in src/testDebug, so the release unit-test build never sees the logger.
 */
class PortalTrafficLogTest {
    private lateinit var server: MockWebServer
    private val lines = CopyOnWriteArrayList<String>()
    private val log = PortalTrafficLog { lines += it }
    private val mac = "00:1A:79:AB:CD:EF"
    private val token = "Zq9xT7kLmN2pR8sV4wY6bH1cD3fG5jK0"
    private val marker = "UNIQUEMARKER"

    @BeforeTest fun start() { server = MockWebServer().apply { start() } }

    @AfterTest fun stop() {
        server.shutdown()
        PortalTransport.interceptors = emptyList()
    }

    private fun portalUrl() = server.url("/stalker_portal/c/").toString()

    /** A handshake request as the session would send it, with a bearer token and the MAC cookie. */
    private fun handshakeRequest(withToken: Boolean = true): ProbeRequest = StalkerRequests.handshake(
        StalkerRequests.Context(portalUrl(), mac, StalkerIdentity.HONEST, token = if (withToken) token else null),
    )

    private fun probe() = HttpProbe(RequestPacer(0), 1_000_000, listOf(log))

    @Test fun `each line shows method, redacted host, type and action, status and elapsed time`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        assertIs<ProbeOutcome.Response>(probe().send(handshakeRequest()))

        val line = lines.single()
        val expected = Regex("""portal GET http://<HOST>/stalker_portal/server/load\.php type=stb action=handshake -> HTTP 200 in \d+ ms""")
        assertTrue(expected.matches(line), "unexpected line: $line")
    }

    @Test fun `no host, MAC, token, cookie or query value except type and action appears`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        // Add an extra query value that is not type or action, to prove query values are dropped.
        val base = handshakeRequest()
        val request = base.copy(url = base.url + "&extra=$marker")
        probe().send(request)

        val all = lines.joinToString("\n")
        val forbidden = listOf(
            "localhost",                       // host
            server.port.toString(),            // port
            mac,                               // MAC with separators
            mac.replace(":", "").lowercase(),  // MAC without separators
            mac.replace(":", "%3A"),           // MAC URL-encoded
            token,                             // bearer token
            marker,                            // query value that is not type/action
            "mac=", "Cookie", "Bearer", "Authorization",
        )
        for (f in forbidden) assertTrue(!all.contains(f, ignoreCase = true), "leaked '$f' in: $all")
        assertTrue(all.contains("type=stb action=handshake"))
    }

    @Test fun `a failing request still logs one redacted FAILED line and rethrows to the probe`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val out = probe().send(handshakeRequest())
        assertIs<ProbeOutcome.Failure>(out)

        val line = lines.single()
        assertTrue(Regex("""portal GET http://<HOST>/stalker_portal/server/load\.php type=stb action=handshake -> FAILED \w+ in \d+ ms""").matches(line), "unexpected line: $line")
        assertTrue(!line.contains(token) && !line.contains(mac, ignoreCase = true) && !line.contains("localhost"), "leak: $line")
    }

    @Test fun `unknown or unsafe type and action values show as question marks`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val base = handshakeRequest()
        val request = base.copy(url = base.url.replace("action=handshake", "action=ev%20il%3D") )
        probe().send(request)
        assertTrue(lines.single().contains("action=?"), lines.single())
    }

    @Test fun `the portal session's traffic is logged through the debug hook`() {
        val watchdogs = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val q = request.path.orEmpty()
                return when {
                    "handshake" in q -> MockResponse().setResponseCode(200).setBody("""{"js":{"token":"$token"}}""")
                    "get_profile" in q -> MockResponse().setResponseCode(200).setBody("""{"js":{"status":0,"expire_billing_date":"2027-11-04 19:14:32"}}""")
                    "get_events" in q -> { watchdogs.incrementAndGet(); MockResponse().setResponseCode(200).setBody("""{"js":{"data":{"msgs":0}}}""") }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        PortalTransport.interceptors = listOf(log)
        val session = PortalSession(intervalMillis = 100)
        try {
            val settings = (PortalSettings.validate(portalUrl(), mac) as PortalSettings.Result.Valid).settings
            session.configure(settings)
            session.onForeground()
            val end = System.nanoTime() + 5_000_000_000L
            // Wait for the logged line itself: the interceptor logs after the fake server has answered.
            while (lines.none { "type=watchdog" in it } && System.nanoTime() < end) Thread.sleep(10)
            assertTrue(watchdogs.get() >= 1, "no keep-alive reached the fake portal")

            val all = lines.joinToString("\n")
            assertTrue(all.contains("type=stb action=handshake -> HTTP 200"), all)
            assertTrue(all.contains("action=get_profile"), all)
            assertTrue(all.contains("type=watchdog action=get_events -> HTTP 200"), all)
            assertTrue(!all.contains(token) && !all.contains("localhost") && !all.contains(mac, ignoreCase = true), all)
        } finally {
            session.shutdown()
        }
    }
}
