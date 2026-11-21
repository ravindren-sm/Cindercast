package com.example.firetviptv.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Scrubs secrets from any text before it is printed, logged or saved.
 *
 * Every log line, summary, exception message and saved response goes through
 * [redact]. Rules, applied in this order (docs/PHASE2_PROBE_PLAN.md, section 5):
 *  1. JSON bodies: values under sensitive keys become "<REDACTED>", at any depth.
 *  2. Known secret values become <NAME> placeholders: raw, URL-encoded, any letter
 *     case, and for MAC addresses with ':' or '-' separators or none.
 *  3. Authorization, Cookie and Set-Cookie headers are masked entirely.
 *  4. key=value pairs with sensitive keys (query strings, form bodies) are masked.
 *  5. URLs are reduced to scheme + host placeholder + path shape; query dropped.
 *  6. Any remaining MAC-like value, bearer token, or long hex/base64 run is masked.
 *
 * Over-redaction is acceptable; leaking is not.
 */
class Redactor(secrets: Map<String, String>) {

    /** (pattern, replacement) pairs for known secret values, longest values first. */
    private val secretPatterns: List<Pair<Regex, String>> = secrets.entries
        .filter { it.value.length >= MIN_SECRET_LENGTH }
        .sortedByDescending { it.value.length }
        .flatMap { (name, value) -> variantsOf(value).map { variantPattern(it) to "<$name>" } }

    fun redact(text: String): String {
        var out = redactJsonIfJson(text)
        for ((pattern, replacement) in secretPatterns) out = pattern.replace(out, replacement)
        out = HEADER.replace(out) { "${it.groupValues[1]}: <REDACTED>" }
        // URLs before key=value pairs: masking a query value first would insert '<'
        // and cut the URL short, leaving the rest of the query outside the URL shape.
        out = URL.replace(out) { urlShape(it.groupValues[1], it.groupValues[2]) }
        out = KEY_VALUE_PAIR.replace(out) { m ->
            if (isSensitiveKey(m.groupValues[1])) "${m.groupValues[1]}=<REDACTED>" else m.value
        }
        out = MAC.replace(out, "<MAC>")
        out = MAC_URL_ENCODED.replace(out, "<MAC>")
        out = BEARER.replace(out, "Bearer <TOKEN>")
        out = LONG_HEX.replace(out, "<HEX>")
        out = LONG_TOKEN.replace(out, "<TOKEN>")
        return out
    }

    /** Redacts a throwable's class name, message and cause chain into one line. */
    fun redact(error: Throwable): String =
        generateSequence(error) { it.cause }
            .joinToString(" <- caused by ") { "${it::class.simpleName}: ${redact(it.message ?: "")}" }

    // ---- JSON ---------------------------------------------------------------

    private fun redactJsonIfJson(text: String): String {
        val trimmed = text.trim()
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return text
        return try {
            JSON.encodeToString(JsonElement.serializer(), redactJson(JSON.parseToJsonElement(trimmed)))
        } catch (_: Exception) {
            text // Not valid JSON: the text rules below still apply.
        }
    }

    private fun redactJson(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (key, value) ->
            if (isSensitiveKey(key)) JsonPrimitive("<REDACTED>") else redactJson(value)
        })
        is JsonArray -> JsonArray(element.map { redactJson(it) })
        else -> element
    }

    // ---- secret value variants ---------------------------------------------

    /** All spellings of [value] that could appear on the wire or in a log. */
    private fun variantsOf(value: String): Set<String> {
        val base = mutableSetOf(value)
        if (MAC_EXACT.matches(value)) {
            val hex = value.filter { it.isLetterOrDigit() }
            val pairs = hex.chunked(2)
            base += hex
            base += pairs.joinToString(":")
            base += pairs.joinToString("-")
        }
        val withEncoded = base.toMutableSet()
        for (v in base) {
            withEncoded += URLEncoder.encode(v, StandardCharsets.UTF_8)            // space -> '+'
            withEncoded += URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20")
        }
        return withEncoded
    }

    /** Case-insensitive literal match; percent-escapes match in either hex case. */
    private fun variantPattern(variant: String): Regex =
        Regex(Regex.escape(variant), RegexOption.IGNORE_CASE)

    // ---- URLs ----------------------------------------------------------------

    private fun urlShape(scheme: String, rest: String): String {
        // rest = host[:port][/path][?query][#fragment]
        val pathStart = rest.indexOf('/')
        val queryStart = rest.indexOfFirst { it == '?' || it == '#' }
        val pathPart = when {
            pathStart < 0 -> ""
            queryStart in 0 until pathStart -> ""
            queryStart >= 0 -> rest.substring(pathStart, queryStart)
            else -> rest.substring(pathStart)
        }
        val segments = pathPart.split('/').filter { it.isNotEmpty() }.map { seg ->
            if (seg.startsWith("<") && seg.endsWith(">") || seg.lowercase() in SAFE_PATH_SEGMENTS) seg else "<p>"
        }
        val path = if (segments.isEmpty()) "" else "/" + segments.joinToString("/")
        val query = if (queryStart >= 0) "?<q>" else ""
        return "$scheme://<HOST>$path$query"
    }

    companion object {
        /** Shorter values are too likely to match ordinary text; the loader warns about them. */
        const val MIN_SECRET_LENGTH = 3

        private val JSON = Json { prettyPrint = false }

        private val SENSITIVE_KEYS = setOf(
            "token", "password", "pass", "username", "user", "login", "mac", "sn", "serial",
            "signature", "cmd", "url", "stream_url", "auth", "authorization", "cookie",
            "session", "sessionid", "key", "api_key", "apikey",
        )

        fun isSensitiveKey(key: String): Boolean {
            val k = key.lowercase()
            return k in SENSITIVE_KEYS ||
                k.startsWith("device_id") ||
                k.endsWith("token") ||
                k.endsWith("_url") ||
                k.contains("password") ||
                k.contains("secret")
        }

        /** Path segments that carry protocol shape, not secrets. Everything else becomes <p>. */
        private val SAFE_PATH_SEGMENTS = setOf(
            "c", "portal.php", "server", "load.php", "stalker_portal", "player_api.php",
            "get.php", "xmltv.php", "live", "movie", "series", "timeshift", "hls",
            "index.m3u8", "playlist.m3u8", "api",
        )

        private val HEADER = Regex("(?im)^\\s*(Authorization|Cookie|Set-Cookie)\\s*:.*$")

        // key=value pairs (query strings, form bodies, cookies). Only pairs whose key is
        // sensitive are masked; the value runs to '&', ';', whitespace or a quote.
        private val KEY_VALUE_PAIR = Regex("\\b([A-Za-z_][A-Za-z0-9_]*)=([^&;\\s\"']+)")

        // The URL body may contain <NAME> placeholders inserted by the secret-value pass.
        private val URL = Regex("(?i)\\b(https?|rtmp|rtsp|udp|rtp)://((?:<[A-Z0-9_]+>|[^\\s\"'<>])+)")

        private val MAC_EXACT = Regex("(?i)^([0-9a-f]{2}[:-]){5}[0-9a-f]{2}$|^[0-9a-f]{12}$")
        private val MAC = Regex("(?i)\\b([0-9a-f]{2}[:-]){5}[0-9a-f]{2}\\b")
        private val MAC_URL_ENCODED = Regex("(?i)\\b([0-9a-f]{2}%3a){5}[0-9a-f]{2}\\b")
        private val BEARER = Regex("(?i)\\bBearer\\s+[^\\s\"',;]+")
        private val LONG_HEX = Regex("(?i)\\b[0-9a-f]{24,}\\b")
        private val LONG_TOKEN = Regex("[A-Za-z0-9+/_=-]{32,}")
    }
}
