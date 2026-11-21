package com.example.firetviptv.provider

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * HttpProbe against a local MockWebServer on 127.0.0.1. These tests never reach
 * the provider or any other network host.
 */
class HttpProbeTest {

    private lateinit var server: MockWebServer
    private var now = 0L
    private fun budget(max: Int = 10) = RequestBudget(max, nowMillis = { now }, sleepMillis = { now += it })

    @BeforeTest fun start() { server = MockWebServer().apply { start() } }
    @AfterTest fun stop() { server.shutdown() }

    private fun req(path: String = "/") =
        ProbeRequest("GET", server.url(path).toString(), ClientIdentity.honestHeaders(), "test")

    @Test fun `records status, headers and a capped body and sends the honest user agent`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Server", "nginx").setBody("x".repeat(10_000)))
        val out = HttpProbe(budget(), maxBodyBytes = 100).send(req())
        assertIs<ProbeOutcome.Response>(out)
        assertEquals(200, out.status)
        assertEquals("nginx", out.header("server"))
        assertEquals(100, out.bodyPrefix.size)
        assertEquals(ClientIdentity.HONEST_USER_AGENT, server.takeRequest().getHeader("User-Agent"))
    }

    @Test fun `does not follow redirects`() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/elsewhere")))
        server.enqueue(MockResponse().setResponseCode(200))
        val out = HttpProbe(budget()).send(req())
        assertIs<ProbeOutcome.Response>(out)
        assertEquals(302, out.status)
        assertEquals(1, server.requestCount)
    }

    @Test fun `a dropped connection is a failure, not a retry`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setResponseCode(200))
        val b = budget()
        val out = HttpProbe(b).send(req())
        assertIs<ProbeOutcome.Failure>(out)
        assertEquals(1, b.used)
        assertTrue(server.requestCount <= 1, "client retried: ${server.requestCount} requests")
    }

    @Test fun `every send consumes budget and the cap stops the next one`() {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(204)) }
        val p = HttpProbe(budget(max = 2))
        p.send(req()); p.send(req())
        assertFailsWith<BudgetExhaustedException> { p.send(req()) }
        assertEquals(2, server.requestCount)
    }

    @Test fun `describe redacts secrets in headers and body`() {
        val secrets = Secrets(mapOf("XTREAM_USERNAME" to "alice123", "XTREAM_HOST" to "http://xt.example.test"))
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Server", "panel for xt.example.test")
                .setHeader("Set-Cookie", "session=abcdef123456")
                .setBody("""{"user_info":{"username":"alice123","status":"Active"}}""")
        )
        val text = HttpProbe(budget()).send(req()).describe(secrets.redactor())
        listOf("alice123", "xt.example.test", "abcdef123456").forEach {
            assertFalse(text.contains(it, ignoreCase = true), "Leaked '$it':\n$text")
        }
        assertTrue(text.contains("HTTP 200") && text.contains("tls: none (plain HTTP)") && text.contains("Active"), text)
    }
}
