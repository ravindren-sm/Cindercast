package com.example.firetviptv.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI

/**
 * What the user types on the TV: the portal URL and the registered device MAC. Held in memory
 * and stored only sealed (see [AesGcmBox]). [toString] prints no values (CLAUDE.md rule 3).
 */
class PortalSettings private constructor(val portalUrl: String, val mac: String) {

    override fun toString() = "PortalSettings(portalUrl=<redacted>, mac=<redacted>)"

    /** True for plain-HTTP portals; the UI can say so (this provider has no HTTPS). */
    val isCleartext: Boolean get() = portalUrl.startsWith("http://", ignoreCase = true)

    /** Values the [Redactor] must mask, with the same labels the probe uses. */
    fun secrets(): Map<String, String> = mapOf("PORTAL_URL" to portalUrl, "EMU_MAC" to mac)

    fun toBytes(): ByteArray =
        Json.encodeToString(JsonObject.serializer(), JsonObject(mapOf(
            "portalUrl" to JsonPrimitive(portalUrl), "mac" to JsonPrimitive(mac),
        ))).toByteArray(Charsets.UTF_8)

    sealed interface Result {
        class Valid(val settings: PortalSettings) : Result
        /** [problems] are short fixed messages, safe to show on screen and to log. */
        class Invalid(val problems: List<Problem>) : Result
    }

    enum class Problem(val message: String) {
        URL_EMPTY("Enter the portal address."),
        URL_BAD_SCHEME("The portal address must start with http:// or https://"),
        URL_NO_HOST("The portal address has no host name."),
        MAC_EMPTY("Enter the registered MAC address."),
        MAC_BAD("The MAC address must be 12 hexadecimal digits, like 00:1A:79:12:34:56."),
    }

    companion object {
        fun validate(portalUrl: String, mac: String): Result {
            val problems = mutableListOf<Problem>()
            val url = portalUrl.trim()
            var host: String? = null
            if (url.isEmpty()) problems += Problem.URL_EMPTY
            else {
                val uri = try { URI(url) } catch (_: Exception) { null }
                when {
                    uri?.scheme?.lowercase() !in setOf("http", "https") -> problems += Problem.URL_BAD_SCHEME
                    // Non-standard names (for example with underscores) leave URI.host null, so
                    // fall back to the raw authority.
                    (uri?.host ?: uri?.rawAuthority).isNullOrBlank() -> problems += Problem.URL_NO_HOST
                    else -> host = uri?.host ?: uri?.rawAuthority
                }
            }
            val normalizedMac = normalizeMac(mac)
            if (mac.isBlank()) problems += Problem.MAC_EMPTY else if (normalizedMac == null) problems += Problem.MAC_BAD
            if (problems.isNotEmpty() || host == null || normalizedMac == null) return Result.Invalid(problems)
            return Result.Valid(PortalSettings(url, normalizedMac))
        }

        /** Accepts "00:1a:79:..", "00-1A-79-..", or 12 digits without separators; returns AA:BB:CC:DD:EE:FF. */
        fun normalizeMac(input: String): String? {
            val hex = input.trim().filter { it != ':' && it != '-' && !it.isWhitespace() }
            if (hex.length != 12 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
            return hex.uppercase().chunked(2).joinToString(":")
        }

        /** Rebuilds settings from [toBytes] output; null if the bytes are not valid settings. */
        fun fromBytes(bytes: ByteArray): PortalSettings? = try {
            val o = Json.parseToJsonElement(String(bytes, Charsets.UTF_8)) as? JsonObject
            val url = (o?.get("portalUrl") as? JsonPrimitive)?.contentOrNull
            val mac = (o?.get("mac") as? JsonPrimitive)?.contentOrNull
            (validate(url ?: "", mac ?: "") as? Result.Valid)?.settings
        } catch (_: Exception) {
            null
        }
    }
}
