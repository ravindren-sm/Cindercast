package com.example.firetviptv.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Redaction tests. All secrets here are fake. The core assertion is [assertNoLeak]:
 * no fake secret may survive in any spelling (raw, URL-encoded, any case, MAC
 * without separators).
 */
class RedactorTest {

    private val fake = mapOf(
        "EMU_MAC" to "00:1A:79:AB:CD:EF",
        "PORTAL_URL" to "http://portal.example.test:8080/c/",
        "XTREAM_HOST" to "http://xt.example.test:8000",
        "XTREAM_USERNAME" to "alice123",
        "XTREAM_PASSWORD" to "s3cr3t Pa'ss",
        "EMU_SERIAL" to "SN0042XYZ",
        "UNIFI_API_KEY" to "unifi-key-abc-123",
    )
    private val r = Redactor(fake)

    /** Every spelling a test checks for, per secret. */
    private fun spellings(value: String): Set<String> {
        val s = mutableSetOf(value, value.lowercase(), value.uppercase())
        s += java.net.URLEncoder.encode(value, Charsets.UTF_8)
        s += java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
        if (value.count { it == ':' } == 5) {
            val hex = value.replace(":", "")
            s += listOf(hex, hex.lowercase(), value.replace(":", "-"), value.lowercase().replace(":", "-"))
        }
        return s
    }

    private fun assertNoLeak(output: String) {
        for ((name, value) in fake) for (sp in spellings(value)) {
            assertFalse(output.contains(sp, ignoreCase = true), "Leaked $name as '$sp' in: $output")
        }
    }

    @Test fun `plain values become name placeholders`() {
        val out = r.redact("user alice123 with serial SN0042XYZ")
        assertNoLeak(out)
        assertEquals("user <XTREAM_USERNAME> with serial <EMU_SERIAL>", out)
    }

    @Test fun `MAC in every spelling`() {
        val input = listOf(
            "00:1A:79:AB:CD:EF", "00:1a:79:ab:cd:ef", "00-1A-79-AB-CD-EF",
            "001A79ABCDEF", "001a79abcdef", "00%3A1A%3A79%3AAB%3ACD%3AEF", "00%3a1a%3a79%3aab%3acd%3aef",
        ).joinToString(" | ")
        val out = r.redact(input)
        assertNoLeak(out)
        assertEquals(7, Regex("<EMU_MAC>").findAll(out).count(), out)
    }

    @Test fun `bare hostnames of URL secrets are caught everywhere`() {
        val out = r.redact(
            "CN=xt.example.test, O=Example | Server: nginx on portal.example.test | " +
                "redirect to https://xt.example.test/login | www.xt.example.test | xt.example.test:8000"
        )
        assertNoLeak(out)
        listOf("xt.example.test", "portal.example.test").forEach {
            assertFalse(out.contains(it, ignoreCase = true), "Leaked host '$it' in: $out")
        }
        assertTrue(out.contains("CN=<XTREAM_HOST>"), out)
    }

    @Test fun `URL shape keeps a known host placeholder but hides other hosts`() {
        val out = r.redact("Location: https://xt.example.test/stalker_portal/c and http://cdn.other.test/stalker_portal/c")
        assertNoLeak(out)
        assertTrue(out.contains("https://<XTREAM_HOST>/stalker_portal/c"), out)
        assertTrue(out.contains("http://<HOST>/stalker_portal/c"), out)
    }

    @Test fun `stalker profile fields and IP addresses are masked`() {
        val profile = """{"js":{"id":"77","name":"Alex","fname":"A B","ip":"203.0.113.45","phone":"555-0100",
            "status":0,"blocked":"0","random":"r4nd0m","expire_billing_date":"2027-01-01 00:00:00"}}"""
        val out = r.redact(profile)
        listOf("203.0.113.45", "A B", "555-0100", "r4nd0m").forEach {
            assertFalse(out.contains(it), "Leaked '$it' in: $out")
        }
        assertTrue(out.contains("\"status\":0") && out.contains("2027-01-01"), out)
        assertEquals("seen from <IP> and <IP>", r.redact("seen from 192.0.2.10 and 198.51.100.7"))
    }

    @Test fun `unknown MACs are masked too`() {
        val out = r.redact("other device 11:22:33:44:55:66 and 11%3A22%3A33%3A44%3A55%3A66")
        assertFalse(out.contains("11:22", ignoreCase = true))
        assertFalse(out.contains("11%3A22", ignoreCase = true))
    }

    @Test fun `password with space and quote in encoded forms`() {
        val out = r.redact("a=s3cr3t+Pa%27ss b=s3cr3t%20Pa%27ss c=s3cr3t Pa'ss")
        assertNoLeak(out)
    }

    @Test fun `Xtream stream URL with credentials in the path`() {
        val out = r.redact("playing http://xt.example.test:8000/live/alice123/s3cr3t%20Pa%27ss/1234.ts")
        assertNoLeak(out)
        assertTrue(out.contains("<XTREAM_HOST>/live/<XTREAM_USERNAME>/<XTREAM_PASSWORD>/"), out)
    }

    @Test fun `Xtream API URL with credentials in the query`() {
        val out = r.redact("GET http://xt.example.test:8000/player_api.php?username=alice123&password=s3cr3t+Pa%27ss")
        assertNoLeak(out)
    }

    @Test fun `unknown hosts reduced to path shape and query dropped`() {
        val out = r.redact("see https://cdn.other.test/hls/abcdef/chunk_77.ts?token=xyz&e=123")
        assertEquals("see https://<HOST>/hls/<p>/<p>?<q>", out)
    }

    @Test fun `JSON values under sensitive keys are redacted at any depth`() {
        val json = """
            {"js":{"token":"T0KEN-value-1","profile":{"login":"bob","mac":"aa:bb:cc:dd:ee:ff","status":1},
             "data":[{"name":"News HD","cmd":"ffmpeg http://edge.test/ch/5?play_token=Q1W2E3","number":"5"}],
             "device_id2":"D2-value","server_info":{"url":"edge.test","port":"8080"}}}
        """.trimIndent()
        val out = r.redact(json)
        listOf("T0KEN-value-1", "bob", "aa:bb", "Q1W2E3", "edge.test", "D2-value").forEach {
            assertFalse(out.contains(it, ignoreCase = true), "Leaked '$it' in: $out")
        }
        // Non-sensitive fields survive, so findings stay useful.
        assertTrue(out.contains("\"name\":\"News HD\""), out)
        assertTrue(out.contains("\"status\":1"), out)
        assertTrue(out.contains("\"port\":\"8080\""), out)
    }

    @Test fun `secret values inside JSON non-sensitive fields are still caught`() {
        val out = r.redact("""{"note":"welcome alice123","channels":42}""")
        assertNoLeak(out)
        assertTrue(out.contains("<XTREAM_USERNAME>") && out.contains("\"channels\":42"), out)
    }

    @Test fun `auth and cookie headers are masked whole`() {
        val out = r.redact(
            """
            Authorization: Bearer abc.def.ghi
            Cookie: mac=00%3A1A%3A79%3AAB%3ACD%3AEF; stb_lang=en; timezone=Europe/Paris
            Set-Cookie: PHPSESSID=8f3k2l1
            User-Agent: firetv-iptv-probe/0.1
            """.trimIndent()
        )
        assertNoLeak(out)
        assertFalse(out.contains("abc.def") || out.contains("8f3k2l1") || out.contains("stb_lang"), out)
        assertTrue(out.contains("User-Agent: firetv-iptv-probe/0.1"), out)
    }

    @Test fun `bearer tokens and form pairs in free text`() {
        val out = r.redact("sent Bearer zzz111 then body login=carol&password=hunter22&mode=x")
        assertFalse(out.contains("zzz111") || out.contains("carol") || out.contains("hunter22"), out)
        assertTrue(out.contains("mode=x"), out)
    }

    @Test fun `long hex and token-like runs are masked`() {
        val out = r.redact("sig 0123456789abcdef0123456789abcdef and tok eyJhbGciOiJIUzI1NiJ9eyJzdWIiOiIxMjM0NTY3ODkwIn0")
        assertFalse(out.contains("0123456789abcdef") || out.contains("eyJhbGci"), out)
    }

    @Test fun `exceptions are redacted through the cause chain`() {
        val e = RuntimeException("failed for alice123", IllegalStateException("host http://xt.example.test:8000 refused"))
        val out = r.redact(e)
        assertNoLeak(out)
        assertTrue(out.contains("caused by"), out)
    }

    @Test fun `ordinary text is unchanged`() {
        val text = "Channels: 312, categories: 18, container: HLS, status 200 OK"
        assertEquals(text, r.redact(text))
    }

    @Test fun `sensitive key rules`() {
        listOf("token", "TOKEN", "play_token", "device_id1", "stream_url", "cmd", "Password", "api_key")
            .forEach { assertTrue(Redactor.isSensitiveKey(it), it) }
        listOf("name", "number", "status", "port", "max_connections", "exp_date")
            .forEach { assertFalse(Redactor.isSensitiveKey(it), it) }
    }
}
