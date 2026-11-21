package com.example.firetviptv.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URI
import java.net.URLEncoder
import java.time.ZoneId

/*
 * Stalker (Ministra) portal protocol, the API that MAG boxes and StbEmu use.
 *
 * Request shape: GET <api>?type=<t>&action=<a>&...&JsHttpRequest=1-xml
 *   api      = scheme://host[:port]/stalker_portal/server/load.php   (or /portal.php)
 *   Cookie   = mac=<MAC>; stb_lang=en; timezone=<zone>               (identifies the device)
 *   Referer  = the portal client URL
 *   Authorization: Bearer <token>   (after the handshake)
 * Responses are JSON wrapped as {"js": ...}.
 *
 * Shared by the probe (Phase 2) and the app (Phase 3). Pure request building lives in
 * [StalkerRequests]; [StalkerClient] sends through HttpProbe (budget, no retries).
 */

/** How the client presents itself. MAG only with the user's per-run consent (plan section 9). */
enum class StalkerIdentity(val userAgent: String, val xUserAgent: String?) {
    HONEST(ClientIdentity.HONEST_USER_AGENT, null),
    MAG(
        "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
        "Model: MAG250; Link: WiFi",
    ),
}

object StalkerRequests {

    /** Device API endpoint for a portal client URL. */
    fun apiUrl(portalUrl: String): String {
        val u = URI(portalUrl.trim())
        require(u.scheme != null && u.host != null) { "Portal URL has no scheme or host" }
        val base = "${u.scheme}://${u.host}" + if (u.port >= 0) ":${u.port}" else ""
        return if ((u.path ?: "").contains("/stalker_portal")) "$base/stalker_portal/server/load.php" else "$base/portal.php"
    }

    fun handshake(ctx: Context) = build(ctx, "Step 4a: handshake (portal issues a session token for this MAC)",
        "type" to "stb", "action" to "handshake", "token" to "")

    fun profile(ctx: Context) = build(ctx, "Step 4b: get_profile (account status; activates the token)",
        "type" to "stb", "action" to "get_profile", "hd" to "1", "stb_type" to "MAG250",
        "auth_second_step" to "1", "not_valid_token" to "0")

    fun genres(ctx: Context) = build(ctx, "Step 4c: live TV genres (categories)",
        "type" to "itv", "action" to "get_genres")

    /** First page only (about 14 channels) plus the total count: keeps the response small. */
    fun channelsPage1(ctx: Context) = build(ctx, "Step 4c: first page of live channels and the total count",
        "type" to "itv", "action" to "get_ordered_list", "genre" to "*", "fav" to "0", "sortby" to "number", "p" to "1")

    /** One page of live channels in a single genre (by genre id from get_genres). */
    fun channelsInGenre(ctx: Context, genreId: String, page: Int = 1) = build(ctx,
        "Step 4d (chosen channel): channels in one genre, page $page",
        "type" to "itv", "action" to "get_ordered_list", "genre" to genreId, "fav" to "0", "sortby" to "number", "p" to page.toString())

    /**
     * VOD categories. [type] is "vod" (movies, and on many portals shows too) or "series"
     * (separate series module on newer Ministra portals; may be absent).
     */
    fun vodCategories(ctx: Context, type: String) = build(ctx, "Step 4e: $type categories",
        "type" to type, "action" to "get_categories")

    /**
     * One page of a VOD list. category "*" = all. movie_id/season_id/episode_id = 0 asks for
     * the top level (titles), not a series' seasons or episodes.
     */
    fun vodPage(ctx: Context, type: String, category: String = "*", page: Int = 1) = build(ctx,
        "Step 4e: $type list, category $category, page $page",
        "type" to type, "action" to "get_ordered_list", "movie_id" to "0", "season_id" to "0",
        "episode_id" to "0", "category" to category, "fav" to "0", "sortby" to "added",
        "hd" to "0", "not_ended" to "0", "p" to page.toString())

    fun createLink(ctx: Context, cmd: String) = build(ctx, "Step 4d: create_link (turns a channel's cmd into a stream URL)",
        "type" to "itv", "action" to "create_link", "cmd" to cmd, "series" to "", "forced_storage" to "0",
        "disable_ad" to "0", "download" to "0")

    data class Context(
        val portalUrl: String,
        val mac: String,
        val identity: StalkerIdentity = StalkerIdentity.HONEST,
        val token: String? = null,
        val timezone: String = ZoneId.systemDefault().id,
    )

    private fun build(ctx: Context, purpose: String, vararg params: Pair<String, String>): ProbeRequest {
        val query = (params.toList() + ("JsHttpRequest" to "1-xml"))
            .joinToString("&") { (k, v) -> "$k=${enc(v)}" }
        val headers = linkedMapOf(
            "User-Agent" to ctx.identity.userAgent,
            "Referer" to ctx.portalUrl.trim(),
            "Cookie" to "mac=${enc(ctx.mac.trim())}; stb_lang=en; timezone=${enc(ctx.timezone)}",
            "Accept" to "*/*",
        )
        ctx.identity.xUserAgent?.let { headers["X-User-Agent"] = it }
        ctx.token?.takeIf { it.isNotEmpty() }?.let { headers["Authorization"] = "Bearer $it" }
        return ProbeRequest("GET", "${apiUrl(ctx.portalUrl)}?$query", headers, purpose)
    }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8") /* String overload: Android 11 lacks the Charset one */
}

/**
 * Result of one portal call: the raw outcome (print only via describe) plus [summary]
 * lines that are safe by construction: counts, key names, and whitelisted fields.
 */
class StalkerResult(val outcome: ProbeOutcome, internal val js: JsonElement?, val summary: List<String>) {
    /** Whether the response carried a parseable {"js": ...} payload. */
    val hasJson: Boolean get() = js != null

    val ok: Boolean get() = outcome is ProbeOutcome.Response && outcome.status == 200 && js != null

    /** Top-level scalar field of the "js" object as text (null if absent or not a scalar). Callers redact before printing. */
    fun field(key: String): String? = ((js as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull

    /** Whether the payload is a JSON object (as opposed to an array, scalar or nothing). */
    val isObject: Boolean get() = js is JsonObject
}

class StalkerClient(
    private val portalUrl: String,
    private val mac: String,
    private val probe: HttpProbe,
    private val identity: StalkerIdentity = StalkerIdentity.HONEST,
) {
    var token: String? = null
        private set

    private fun ctx() = StalkerRequests.Context(portalUrl, mac, identity, token)

    fun handshake(): StalkerResult = call(StalkerRequests.handshake(ctx())) { js ->
        val t = (js as? JsonObject)?.str("token")
        token = t?.takeIf { it.isNotEmpty() }
        // Presence only: no length, hash, or any other property of the token is reported.
        // not_valid is a 0/1 portal flag (not identifying), shown to help diagnose get_profile.
        val notValid = (js as? JsonObject)?.get("not_valid")?.let { "not_valid: ${shortValue(it)}" }
        listOfNotNull("token issued: ${if (token != null) "yes" else "no"}", notValid) + keysLine(js)
    }

    fun profile(): StalkerResult = call(StalkerRequests.profile(ctx())) { js ->
        val o = js as? JsonObject
        val lines = mutableListOf<String>()
        // Whitelisted, non-identifying fields only. Messages go through the redactor later.
        listOf("status", "blocked", "expire_billing_date", "tariff_expired_date", "msg", "block_msg", "watchdog_timeout", "storages")
            .forEach { k -> o?.get(k)?.let { v -> lines += "$k: ${shortValue(v)}" } }
        lines + keysLine(js)
    }

    fun genres(): StalkerResult = call(StalkerRequests.genres(ctx())) { js ->
        val arr = js as? JsonArray
        val titles = arr?.mapNotNull { (it as? JsonObject)?.str("title") }.orEmpty()
        listOf("genres: ${arr?.size ?: "?"}", "first titles: ${titles.take(12).joinToString(" ; ")}")
    }

    /**
     * get_genres, then the id of the genre whose title equals [title] (case-insensitive).
     * Genre titles are catalog labels, not identifying data.
     */
    fun findGenreId(title: String): Pair<StalkerResult, String?> {
        var id: String? = null
        val r = call(StalkerRequests.genres(ctx())) { js ->
            val items = (js as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            id = items.firstOrNull { it.str("title")?.trim().equals(title.trim(), ignoreCase = true) }?.str("id")
            listOf("genres: ${items.size}", "requested genre '$title': ${if (id != null) "found" else "not found"}")
        }
        return r to id
    }

    /**
     * Page 1 of genre [genreId]; picks the first channel whose name contains [nameContains]
     * (case-insensitive). Returns the result, that channel's cmd, and "number name" for the log.
     */
    fun channelInGenre(genreId: String, nameContains: String): Triple<StalkerResult, String?, String?> {
        var cmd: String? = null
        var label: String? = null
        val r = call(StalkerRequests.channelsInGenre(ctx(), genreId)) { js ->
            val o = js as? JsonObject
            val items = (o?.get("data") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            val hit = items.firstOrNull { it.str("name")?.contains(nameContains.trim(), ignoreCase = true) == true }
            cmd = hit?.str("cmd")?.takeIf { it.isNotBlank() }
            label = hit?.let { "${it.str("number") ?: "?"} ${it.str("name") ?: "?"}" }
            listOf(
                "channels in genre: ${o?.get("total_items")?.let(::shortValue) ?: "?"} (page 1 shows ${items.size})",
                "requested channel '$nameContains': ${label?.let { "found ($it)" } ?: "not found on page 1"}",
                "cmd shape: ${cmd?.let(::cmdShape) ?: "-"}",
            )
        }
        return Triple(r, cmd, label)
    }

    /** VOD categories as (id, title); summary: count, first titles, whether an "All" (*) entry exists. */
    fun vodCategories(type: String): Pair<StalkerResult, List<Pair<String, String>>> {
        var cats: List<Pair<String, String>> = emptyList()
        val r = call(StalkerRequests.vodCategories(ctx(), type)) { js ->
            val items = (js as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            cats = items.mapNotNull { o -> o.str("id")?.let { id -> id to (o.str("title") ?: "?") } }
            listOf(
                "$type categories: ${items.size}",
                "has All (*): ${if (cats.any { it.first == "*" }) "yes" else "no"}",
                // " ; " separator: some portal category titles contain " | " themselves.
                "first titles: ${cats.take(12).joinToString(" ; ") { it.second }}",
            )
        }
        return r to cats
    }

    /**
     * Page [page] of a VOD list. Summary is safe by construction: totals, page size, sample
     * titles (with year), how many items on the page are series, cmd KINDS (never values),
     * and the item field names.
     */
    fun vodPage(type: String, category: String = "*", page: Int = 1): StalkerResult =
        call(StalkerRequests.vodPage(ctx(), type, category, page)) { js ->
            val o = js as? JsonObject
            val items = (o?.get("data") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            val seriesFlag = items.count { it.str("is_series") == "1" }
            val withEpisodes = items.count { (it["series"] as? JsonArray)?.isNotEmpty() == true }
            val cmdKinds = items.mapNotNull { it.str("cmd")?.let(::vodCmdKind) }.groupingBy { it }.eachCount()
            listOf(
                "$type total: ${o?.get("total_items")?.let(::shortValue) ?: "?"} (page size ${o?.get("max_page_items")?.let(::shortValue) ?: "?"})",
                "page $page items: ${items.size}; is_series=1: $seriesFlag; with episode list: $withEpisodes",
                "sample: ${items.take(5).joinToString(" | ") { "${it.str("name") ?: "?"} (${it.str("year") ?: "?"})" }}",
                "cmd kinds: ${cmdKinds.entries.joinToString { "${it.key} x${it.value}" }.ifEmpty { "-" }}",
                "item keys: ${items.firstOrNull()?.keys?.sorted()?.joinToString(",") ?: "-"}",
            )
        }

    /** Returns the first channel's cmd through [firstCmd] for create_link. */
    fun channelsPage1(): Pair<StalkerResult, String?> {
        var cmd: String? = null
        val r = call(StalkerRequests.channelsPage1(ctx())) { js ->
            val o = js as? JsonObject
            val data = o?.get("data") as? JsonArray
            val items = data?.mapNotNull { it as? JsonObject }.orEmpty()
            cmd = items.firstNotNullOfOrNull { it.str("cmd")?.takeIf { c -> c.isNotBlank() } }
            val schemes = items.mapNotNull { it.str("cmd")?.let(::cmdShape) }.toSortedSet()
            listOf(
                "total channels: ${o?.get("total_items")?.let(::shortValue) ?: "?"} (page size ${o?.get("max_page_items")?.let(::shortValue) ?: "?"})",
                "sample: ${items.take(6).joinToString(" | ") { "${it.str("number") ?: "?"} ${it.str("name") ?: "?"}" }}",
                "cmd shapes on page 1: ${schemes.joinToString()}",
                "channel keys: ${items.firstOrNull()?.keys?.sorted()?.joinToString(",") ?: "-"}",
            )
        }
        return r to cmd
    }

    /** Returns the playable URL through the pair (cmd prefix such as "ffmpeg " removed). */
    fun createLink(cmd: String): Pair<StalkerResult, String?> {
        var url: String? = null
        val r = call(StalkerRequests.createLink(ctx(), cmd)) { js ->
            val linkCmd = (js as? JsonObject)?.str("cmd")
            url = linkCmd?.let(::streamUrlFromCmd)
            // error is a short portal status string (for example "" or "nothing_to_play"); not identifying.
            val error = (js as? JsonObject)?.str("error")
            listOfNotNull(
                "link returned: ${if (url != null) "yes, shape ${cmdShape(linkCmd!!)}" else "no"}",
                error?.let { "error: '${it.take(60)}'" },
            ) + keysLine(js)
        }
        return r to url
    }

    private fun call(request: ProbeRequest, summarize: (JsonElement?) -> List<String>): StalkerResult {
        val outcome = probe.send(request)
        val js = (outcome as? ProbeOutcome.Response)?.let { parseJs(it.bodyPrefix) }
        val summary = if (js != null) summarize(js) else listOf("no JSON payload in the response")
        return StalkerResult(outcome, js, summary)
    }

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Parses {"js": ...} and returns the inner element; null if not JSON or no "js". */
        fun parseJs(body: ByteArray): JsonElement? = try {
            (JSON.parseToJsonElement(String(body, Charsets.UTF_8).trim()) as? JsonObject)?.get("js")
        } catch (_: Exception) { null }

        /** "ffmpeg http://x/y" -> "http://x/y"; plain URLs unchanged; null if no URL. */
        fun streamUrlFromCmd(cmd: String): String? =
            Regex("(?i)\\b(https?|rtmp|rtsp|udp|rtp)://\\S+").find(cmd)?.value

        /** Non-identifying description of a cmd: prefix word + scheme + whether it is a placeholder host. */
        fun cmdShape(cmd: String): String {
            val prefix = cmd.trim().substringBefore(' ', "").takeIf { cmd.trim().contains(' ') } ?: "-"
            val url = streamUrlFromCmd(cmd) ?: return "$prefix (no URL)"
            val u = try { URI(url) } catch (_: Exception) { null }
            // URI.host is null for names it rejects (for example with underscores); fall back
            // to the raw authority so the kind can still be classified. Never printed itself.
            val host = (u?.host ?: u?.rawAuthority?.substringAfterLast('@')?.substringBefore(':'))?.lowercase()
            val hostKind = when {
                host == null -> "unparsed host"
                host == "localhost" || host == "127.0.0.1" -> "localhost (needs create_link)"
                Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host) -> "remote IP address"
                u?.host == null -> "remote host (non-standard name)"
                else -> "remote host"
            }
            // Non-standard host names also lose their port in URI; take it from the raw authority.
            val portNum = u?.port?.takeIf { it >= 0 }
                ?: u?.rawAuthority?.substringAfterLast(':', "")?.toIntOrNull()
            val port = portNum?.let { " port $it" } ?: ""
            return "$prefix ${u?.scheme ?: url.substringBefore("://")} $hostKind$port"
        }

        /**
         * Kind of a VOD cmd without revealing it: a URL (classified like [cmdShape]), a server
         * path, base64-looking data (often encoded JSON with a file id), or other.
         */
        fun vodCmdKind(cmd: String): String {
            val c = cmd.trim()
            return when {
                c.isEmpty() -> "empty"
                c.contains("://") -> "url: " + cmdShape(c)
                c.startsWith("/") -> "server path"
                Regex("^[A-Za-z0-9+/=_-]{16,}$").matches(c) -> "base64-like (${c.length} chars)"
                else -> "other (${c.length} chars)"
            }
        }

        /** Identifies a stream from its first bytes: MPEG-TS sync pattern, HLS playlist, or unknown. */
        fun streamSignature(bytes: ByteArray): String {
            if (bytes.isEmpty()) return "empty"
            val head = String(bytes, 0, minOf(bytes.size, 16), Charsets.ISO_8859_1)
            if (head.startsWith("#EXTM3U")) return "HLS playlist (#EXTM3U)"
            val syncs = (0 until 4).map { it * 188 }.filter { it < bytes.size }
            if (syncs.size >= 2 && syncs.all { bytes[it] == 0x47.toByte() }) return "MPEG-TS (0x47 sync every 188 bytes)"
            if (bytes.size > 8 && String(bytes, 4, 4, Charsets.ISO_8859_1) == "ftyp") return "MP4 (ftyp)"
            return "unknown (first byte 0x%02x)".format(bytes[0])
        }

        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        private fun shortValue(v: JsonElement): String = when (v) {
            is JsonPrimitive -> (v.intOrNull?.toString() ?: v.contentOrNull ?: "null").take(120)
            is JsonArray -> "[${v.size} items]"
            is JsonObject -> "{${v.size} keys}"
        }

        private fun keysLine(js: JsonElement?): List<String> =
            (js as? JsonObject)?.let { listOf("response keys: ${it.keys.sorted().joinToString(",")}") } ?: emptyList()
    }
}
