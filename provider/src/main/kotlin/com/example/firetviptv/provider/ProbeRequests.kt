package com.example.firetviptv.provider

import java.net.URLEncoder

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
    DISCOVERY("discovery", "Step 3a", listOf("XTREAM_HOST"), usesMac = false),
    /** Step 3b part 1: repeat the Step 2 root GET and store the redirect target as PORTAL_URL. */
    CAPTURE_PORTAL("capture-portal", "Step 3b", listOf("XTREAM_HOST"), usesMac = false),
    /** Step 3b part 2: the Step 3a pair against the real portal host. Run by capture-portal. */
    DISCOVERY_PORTAL("discovery-portal", "Step 3b", listOf("PORTAL_URL"), usesMac = false),
    XTREAM_ACCOUNT("xtream-account", "Step 3", listOf("XTREAM_HOST", "XTREAM_USERNAME", "XTREAM_PASSWORD"), usesMac = false),
    M3U_PLAYLIST("m3u-playlist", "Step 3", listOf("M3U_URL"), usesMac = false),

    // Step 4: the portal API with the registered MAC. Each run starts with its own
    // handshake. Live use needs a separate approval per step (LIVE_APPROVED in :probe).
    PORTAL_HANDSHAKE("portal-handshake", "Step 4a", listOf("PORTAL_URL", "EMU_MAC"), usesMac = true),
    PORTAL_PROFILE("portal-profile", "Step 4b", listOf("PORTAL_URL", "EMU_MAC"), usesMac = true),
    PORTAL_CHANNELS("portal-channels", "Step 4c", listOf("PORTAL_URL", "EMU_MAC"), usesMac = true),
    PORTAL_LINK("portal-link", "Step 4d", listOf("PORTAL_URL", "EMU_MAC"), usesMac = true),
    /** 4d on a chosen channel (--genre="..." --channel="..."), for example one known to play in TiviMate. */
    PORTAL_LINK_CHANNEL("portal-link-channel", "Step 4d (chosen channel)", listOf("PORTAL_URL", "EMU_MAC"), usesMac = true),
    /** 4e: VOD catalog (movies and shows). Runs in the Fire TV debug player; listed here for dry runs. */
    PORTAL_VOD_CATALOG("portal-vod-catalog", "Step 4e", listOf("PORTAL_URL", "EMU_MAC"), usesMac = true);

    companion object {
        fun fromCliName(name: String): ProbeStep? = entries.firstOrNull { it.cliName == name }
    }
}

/** Builds the request(s) for a step from loaded secrets. Pure: no I/O. */
object ProbeRequests {

    fun forStep(step: ProbeStep, secrets: Secrets): List<ProbeRequest> = when (step) {
        ProbeStep.REACHABILITY_PORTAL -> rootGets(secrets.require("PORTAL_URL"), "portal")
        ProbeStep.REACHABILITY_XTREAM -> rootGets(secrets.require("XTREAM_HOST"), "Xtream")
        ProbeStep.DISCOVERY -> discovery(secrets.require("XTREAM_HOST"))
        ProbeStep.CAPTURE_PORTAL -> listOf(
            rootGets(secrets.require("XTREAM_HOST"), "Xtream").first().copy(
                purpose = "Step 3b: repeat the Step 2 HTTPS root GET; store the redirect target as PORTAL_URL without printing it",
            )
        )
        ProbeStep.DISCOVERY_PORTAL -> discovery(secrets.require("PORTAL_URL"))
        ProbeStep.PORTAL_HANDSHAKE, ProbeStep.PORTAL_PROFILE, ProbeStep.PORTAL_CHANNELS, ProbeStep.PORTAL_LINK,
        ProbeStep.PORTAL_LINK_CHANNEL, ProbeStep.PORTAL_VOD_CATALOG ->
            portalPlan(step, secrets)
        ProbeStep.XTREAM_ACCOUNT -> listOf(xtreamAccount(secrets))
        ProbeStep.M3U_PLAYLIST -> listOf(
            ProbeRequest("GET", secrets.require("M3U_URL"), ClientIdentity.honestHeaders(),
                "Step 3: download the playlist once; parse locally; record counts and URL schemes only")
        )
    }

    /**
     * Step 2 requests: GET of the host root only (no path, no query, no credentials).
     * First HTTPS on the default port. If the configured address is plain HTTP, a
     * second request to it is listed as a FALLBACK: the runner sends it only when the
     * HTTPS request gets no HTTP response at all (refused, timed out, TLS failure).
     */
    private fun rootGets(configured: String, label: String): List<ProbeRequest> {
        val uri = java.net.URI(configured.trim())
        require(uri.scheme != null && uri.host != null) { "Configured URL has no scheme or host" }
        val configuredPort = if (uri.port >= 0) ":${uri.port}" else ""
        val https = ProbeRequest(
            "GET", "https://${uri.host}/", ClientIdentity.honestHeaders(),
            "Step 2: does the $label host answer over HTTPS; is its certificate valid",
        )
        if (uri.scheme.equals("https", ignoreCase = true) && configuredPort.isEmpty()) return listOf(https)
        val configuredRoot = ProbeRequest(
            "GET", "${uri.scheme}://${uri.host}$configuredPort/", ClientIdentity.honestHeaders(),
            "Step 2 FALLBACK (only if the HTTPS request got no response): does the configured $label address answer",
        )
        return listOf(https, configuredRoot)
    }

    /**
     * Step 3a: two anonymous GETs on the configured scheme and host (no query string,
     * no credentials, no MAC, no cookies).
     */
    private fun discovery(configured: String): List<ProbeRequest> {
        val uri = java.net.URI(configured.trim())
        require(uri.scheme != null && uri.host != null) { "Configured URL has no scheme or host" }
        val base = "${uri.scheme}://${uri.host}" + if (uri.port >= 0) ":${uri.port}" else ""
        return listOf(
            ProbeRequest("GET", "$base/player_api.php", ClientIdentity.honestHeaders(),
                "Step 3a: does an Xtream API exist (no credentials sent; Xtream replies with JSON, Stalker-only with 404)"),
            ProbeRequest("GET", "$base/stalker_portal/c/", ClientIdentity.honestHeaders(),
                "Step 3a: what the Stalker portal serves anonymously (start page, version hints)"),
        )
    }

    /**
     * Planned Step 4 requests, for dry runs and request counting. Calls after the
     * handshake carry a placeholder token; 4d's create_link uses a placeholder cmd and
     * is followed by one GET of the returned stream URL (not known in advance).
     */
    fun portalPlan(step: ProbeStep, secrets: Secrets, identity: StalkerIdentity = StalkerIdentity.HONEST): List<ProbeRequest> {
        val first = StalkerRequests.Context(secrets.require("PORTAL_URL"), secrets.require("EMU_MAC"), identity)
        val after = first.copy(token = "TOKEN-FROM-HANDSHAKE")
        return when (step) {
            ProbeStep.PORTAL_HANDSHAKE -> listOf(StalkerRequests.handshake(first))
            ProbeStep.PORTAL_PROFILE -> listOf(StalkerRequests.handshake(first), StalkerRequests.profile(after))
            ProbeStep.PORTAL_CHANNELS -> listOf(
                StalkerRequests.handshake(first), StalkerRequests.profile(after),
                StalkerRequests.genres(after), StalkerRequests.channelsPage1(after),
            )
            ProbeStep.PORTAL_LINK -> listOf(
                StalkerRequests.handshake(first), StalkerRequests.profile(after),
                StalkerRequests.channelsPage1(after), StalkerRequests.createLink(after, "CMD-OF-FIRST-CHANNEL"),
                ProbeRequest("GET", "http://stream-url-from-create-link.invalid/", mapOf("User-Agent" to identity.userAgent),
                    "Step 4d: first 64 KB of the stream only (format check, no playback)"),
            )
            ProbeStep.PORTAL_LINK_CHANNEL -> listOf(
                StalkerRequests.handshake(first), StalkerRequests.profile(after),
                StalkerRequests.genres(after), StalkerRequests.channelsInGenre(after, "GENRE-ID-FROM-GET_GENRES"),
                StalkerRequests.createLink(after, "CMD-OF-CHOSEN-CHANNEL"),
                ProbeRequest("GET", "http://stream-url-from-create-link.invalid/", mapOf("User-Agent" to identity.userAgent),
                    "Step 4d: first 64 KB of the stream only (format check, no playback)"),
            )
            ProbeStep.PORTAL_VOD_CATALOG -> vodCatalogPlan(first, after)
            else -> error("not a portal step")
        }
    }

    /**
     * Step 4e request plan, shared by the debug player and dry runs: 6 requests.
     * handshake, profile, vod categories, vod page 1 (all), series categories, series page 1.
     */
    fun vodCatalogPlan(first: StalkerRequests.Context, after: StalkerRequests.Context): List<ProbeRequest> = listOf(
        StalkerRequests.handshake(first), StalkerRequests.profile(after),
        StalkerRequests.vodCategories(after, "vod"), StalkerRequests.vodPage(after, "vod"),
        StalkerRequests.vodCategories(after, "series"), StalkerRequests.vodPage(after, "series"),
    )

    /**
     * From a redirect response, the portal URL to store: the Location's scheme, host and
     * port plus the standard Stalker client path. Returns null (caller stops) when there
     * is no Location, or it does not point at a Stalker portal path.
     */
    fun portalUrlFromRedirect(requestUrl: String, location: String?): String? {
        if (location.isNullOrBlank()) return null
        val target = try { java.net.URI(requestUrl).resolve(location.trim()) } catch (_: Exception) { return null }
        val scheme = target.scheme?.lowercase() ?: return null
        val host = target.host ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (target.path?.startsWith("/stalker_portal") != true) return null
        val port = if (target.port >= 0) ":${target.port}" else ""
        return "$scheme://$host$port/stalker_portal/c/"
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

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8") /* String overload: Android 11 lacks the Charset one */
}
