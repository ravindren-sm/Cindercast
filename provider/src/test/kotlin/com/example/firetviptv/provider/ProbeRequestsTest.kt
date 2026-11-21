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
        )
    )
    private val redactor = secrets.redactor()
    private val rawValues = listOf("portal.example.test", "xt.example.test", "alice123", "s3cr3t", "Pa'ss", "Pa%27ss")

    private fun describeAll(step: ProbeStep) =
        ProbeRequests.forStep(step, secrets).joinToString("\n") { it.describe(redactor) }

    @Test fun `every step's dry-run description is leak-free`() {
        for (step in ProbeStep.entries) {
            val text = describeAll(step)
            rawValues.forEach { assertFalse(text.contains(it, ignoreCase = true), "$step leaked '$it':\n$text") }
            assertTrue(text.contains("User-Agent: ${ClientIdentity.HONEST_USER_AGENT}"), text)
        }
    }

    @Test fun `reachability asks for the host root only`() {
        val req = ProbeRequests.forStep(ProbeStep.REACHABILITY_PORTAL, secrets).single()
        assertEquals("http://portal.example.test:8080/", req.url)
        val x = ProbeRequests.forStep(ProbeStep.REACHABILITY_XTREAM, secrets).single()
        assertEquals("https://xt.example.test:8443/", x.url)
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

    @Test fun `no step uses the MAC in this build`() {
        assertTrue(ProbeStep.entries.none { it.usesMac })
    }

    @Test fun `request toString hides url and headers`() {
        val req = ProbeRequests.forStep(ProbeStep.XTREAM_ACCOUNT, secrets).single()
        assertFalse(req.toString().contains("alice123"))
    }
}
