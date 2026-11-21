package com.example.firetviptv.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProbeRequestsTest {

    private val secrets = Secrets(
        mapOf(
            "PORTAL_URL" to "http://portal.example.test:8080/c/",
            "XTREAM_HOST" to "https://xt.example.test:8443/",
            "XTREAM_USERNAME" to "alice123",
            "XTREAM_PASSWORD" to "s3cr3t Pa'ss",
            "M3U_URL" to "https://xt.example.test:8443/get.php?username=alice123&password=s3cr3t&type=m3u_plus",
            "EMU_MAC" to "00:1A:79:12:34:56",
        )
    )
    private val redactor = secrets.redactor()
    private val rawValues = listOf("portal.example.test", "xt.example.test", "alice123", "s3cr3t", "Pa'ss", "Pa%27ss",
        "00:1A:79:12", "001A79123456", "00%3A1A%3A79")

    private fun describeAll(step: ProbeStep) =
        ProbeRequests.forStep(step, secrets).joinToString("\n") { it.describe(redactor) }

    @Test fun `every step's dry-run description is leak-free`() {
        for (step in ProbeStep.entries) {
            val text = describeAll(step)
            rawValues.forEach { assertFalse(text.contains(it, ignoreCase = true), "$step leaked '$it':\n$text") }
            assertTrue(text.contains("User-Agent: ${ClientIdentity.HONEST_USER_AGENT}"), "$step:\n$text")
        }
    }

    @Test fun `reachability asks for the host root only, HTTPS first`() {
        val portal = ProbeRequests.forStep(ProbeStep.REACHABILITY_PORTAL, secrets)
        assertEquals(listOf("https://portal.example.test/", "http://portal.example.test:8080/"), portal.map { it.url })
        assertTrue(portal[1].purpose.contains("FALLBACK"))
        val x = ProbeRequests.forStep(ProbeStep.REACHABILITY_XTREAM, secrets)
        assertEquals(listOf("https://xt.example.test/", "https://xt.example.test:8443/"), x.map { it.url })
        val plainHost = Secrets(mapOf("XTREAM_HOST" to "http://plain.example.test"))
        assertEquals(
            listOf("https://plain.example.test/", "http://plain.example.test/"),
            ProbeRequests.forStep(ProbeStep.REACHABILITY_XTREAM, plainHost).map { it.url },
        )
        val httpsOnly = Secrets(mapOf("XTREAM_HOST" to "https://secure.example.test"))
        assertEquals(listOf("https://secure.example.test/"), ProbeRequests.forStep(ProbeStep.REACHABILITY_XTREAM, httpsOnly).map { it.url })
    }

    @Test fun `discovery sends no credentials, query or MAC`() {
        val reqs = ProbeRequests.forStep(ProbeStep.DISCOVERY, secrets)
        assertEquals(
            listOf("https://xt.example.test:8443/player_api.php", "https://xt.example.test:8443/stalker_portal/c/"),
            reqs.map { it.url },
        )
        reqs.forEach { r ->
            assertFalse(r.url.contains("?") || r.url.contains("alice123"), r.toString())
            assertEquals(setOf("User-Agent"), r.headers.keys)
        }
    }

    @Test fun `portal URL from a redirect keeps scheme, host and port only`() {
        val from = "https://redirector.example.test/"
        assertEquals("http://real.example.test/stalker_portal/c/",
            ProbeRequests.portalUrlFromRedirect(from, "http://real.example.test/stalker_portal/c"))
        assertEquals("http://real.example.test:8080/stalker_portal/c/",
            ProbeRequests.portalUrlFromRedirect(from, "http://real.example.test:8080/stalker_portal/c/index.html?x=1"))
        assertEquals("https://redirector.example.test/stalker_portal/c/",
            ProbeRequests.portalUrlFromRedirect(from, "/stalker_portal/c"))
        assertEquals(null, ProbeRequests.portalUrlFromRedirect(from, "http://real.example.test/login"))
        assertEquals(null, ProbeRequests.portalUrlFromRedirect(from, null))
        assertEquals(null, ProbeRequests.portalUrlFromRedirect(from, "ftp://real.example.test/stalker_portal/c"))
    }

    @Test fun `capture and portal discovery requests carry no credentials`() {
        val capture = ProbeRequests.forStep(ProbeStep.CAPTURE_PORTAL, secrets).single()
        assertEquals("https://xt.example.test/", capture.url)
        val disc = ProbeRequests.forStep(ProbeStep.DISCOVERY_PORTAL, secrets)
        assertEquals(listOf("http://portal.example.test:8080/player_api.php", "http://portal.example.test:8080/stalker_portal/c/"), disc.map { it.url })
    }

    @Test fun `xtream account request encodes credentials`() {
        val req = ProbeRequests.forStep(ProbeStep.XTREAM_ACCOUNT, secrets).single()
        assertTrue(req.url.endsWith("/player_api.php?username=alice123&password=s3cr3t+Pa%27ss"), "unexpected shape")
    }

    @Test fun `missing secret names the key only`() {
        val e = assertFailsWith<MissingSecretException> {
            ProbeRequests.forStep(ProbeStep.XTREAM_ACCOUNT, Secrets(mapOf("XTREAM_HOST" to "https://h.test")))
        }
        assertEquals("XTREAM_USERNAME", e.secretName)
    }

    @Test fun `only the Step 4 portal steps use the MAC`() {
        assertEquals(
            setOf(ProbeStep.PORTAL_HANDSHAKE, ProbeStep.PORTAL_PROFILE, ProbeStep.PORTAL_CHANNELS, ProbeStep.PORTAL_LINK),
            ProbeStep.entries.filter { it.usesMac }.toSet(),
        )
        assertEquals(listOf(1, 2, 4, 5), listOf(
            ProbeStep.PORTAL_HANDSHAKE, ProbeStep.PORTAL_PROFILE, ProbeStep.PORTAL_CHANNELS, ProbeStep.PORTAL_LINK,
        ).map { ProbeRequests.forStep(it, secrets).size })
    }

    @Test fun `request toString hides url and headers`() {
        val req = ProbeRequests.forStep(ProbeStep.XTREAM_ACCOUNT, secrets).single()
        assertFalse(req.toString().contains("alice123"))
    }
}
