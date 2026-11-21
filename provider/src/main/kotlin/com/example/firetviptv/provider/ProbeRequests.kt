package com.example.firetviptv.provider

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * A request the probe would send, described as data. In the Step 0 build nothing
 * sends these: they exist so dry runs can show the user exactly what a NETWORK step
 * would do, redacted, before the user approves it.
 */
data class ProbeRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    /** Why this request exists, shown in dry runs. */
    val purpose: String,
) {
    /** Redacted multi-line description, safe to print and log. */
    fun describe(redactor: Redactor): String = buildString {
        appendLine("$method ${redactor.redact(url)}")
        headers.forEach { (name, value) -> appendLine(redactor.redact("$name: $value")) }
        append("purpose: $purpose")
    }

    /** Deliberately never prints the URL or headers. */
    override fun toString(): String = "ProbeRequest($method, purpose=$purpose)"
}

/** Client identities. MAG headers are used only if the user allows them (plan section 9). */
object ClientIdentity {
    const val HONEST_USER_AGENT = "firetv-iptv-probe/0.1 (personal use; Kotlin/JVM)"

    fun honestHeaders(): Map<String, String> = mapOf("User-Agent" to HONEST_USER_AGENT)
}

/** Named probe steps that exist in this build, with the secrets each one needs. */
enum class ProbeStep(val cliName: String, val planStep: String, val needs: List<String>, val usesMac: Boolean) {
    REACHABILITY_PORTAL("reachability-portal", "Step 2", listOf("PORTAL_URL"), usesMac = false),
    REACHABILITY_XTREAM("reachability-xtream", "Step 2", listOf("XTREAM_HOST"), usesMac = false),
    XTREAM_ACCOUNT("xtream-account", "Step 3", listOf("XTREAM_HOST", "XTREAM_USERNAME", "XTREAM_PASSWORD"), usesMac = false),
    M3U_PLAYLIST("m3u-playlist", "Step 3", listOf("M3U_URL"), usesMac = false);
    // Step 4 (portal handshake with the registered MAC) is intentionally not defined yet.

    companion object {
        fun fromCliName(name: String): ProbeStep? = entries.firstOrNull { it.cliName == name }
    }
}

/** Builds the request(s) for a step from loaded secrets. Pure: no I/O. */
object ProbeRequests {

    fun forStep(step: ProbeStep, secrets: Secrets): List<ProbeRequest> = when (step) {
        ProbeStep.REACHABILITY_PORTAL -> listOf(rootGet(secrets.require("PORTAL_URL"), "Step 2: is the portal host reachable; HTTPS or HTTP; TLS valid"))
        ProbeStep.REACHABILITY_XTREAM -> listOf(rootGet(secrets.require("XTREAM_HOST"), "Step 2: is the Xtream host reachable; HTTPS or HTTP; TLS valid"))
        ProbeStep.XTREAM_ACCOUNT -> listOf(xtreamAccount(secrets))
        ProbeStep.M3U_PLAYLIST -> listOf(
            ProbeRequest("GET", secrets.require("M3U_URL"), ClientIdentity.honestHeaders(),
                "Step 3: download the playlist once; parse locally; record counts and URL schemes only")
        )
    }

    /** GET of scheme://host[:port]/ only: no path, no query, no credentials. */
    private fun rootGet(configured: String, purpose: String): ProbeRequest {
        val uri = java.net.URI(configured.trim())
        require(uri.scheme != null && uri.host != null) { "Configured URL has no scheme or host" }
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        return ProbeRequest("GET", "${uri.scheme}://${uri.host}$port/", ClientIdentity.honestHeaders(), purpose)
    }

    private fun xtreamAccount(secrets: Secrets): ProbeRequest {
        val host = secrets.require("XTREAM_HOST").trim().trimEnd('/')
        val user = enc(secrets.require("XTREAM_USERNAME"))
        val pass = enc(secrets.require("XTREAM_PASSWORD"))
        return ProbeRequest(
            "GET", "$host/player_api.php?username=$user&password=$pass", ClientIdentity.honestHeaders(),
            "Step 3: Xtream account info (status, expiry, max connections); no channel data yet",
        )
    }

    private fun enc(v: String) = URLEncoder.encode(v, StandardCharsets.UTF_8)
}
