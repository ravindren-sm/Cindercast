package io.github.ravindren_sm.openstream.provider

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

    /**
     * One page of VOD titles whose name matches [query], across all categories (the portal's own `search` parameter).
     * The query is what the viewer typed, so it is only in the request, never in the purpose text that gets logged.
     */
    fun vodSearch(ctx: Context, query: String, page: Int = 1, category: String = "*") = build(ctx,
        "Search: VOD titles matching the typed text, page $page",
        "type" to "vod", "action" to "get_ordered_list", "movie_id" to "0", "season_id" to "0",
        "episode_id" to "0", "category" to category, "fav" to "0", "sortby" to "added",
        "hd" to "0", "not_ended" to "0", "search" to query, "p" to page.toString())

    /** One page of live channels matching [query] (the portal's `search` parameter on the channel list, if it honours it). */
    fun channelsSearch(ctx: Context, query: String, page: Int = 1) = build(ctx,
        "Search: live channels matching the typed text, page $page",
        "type" to "itv", "action" to "get_ordered_list", "genre" to "*", "fav" to "0", "sortby" to "number",
        "search" to query, "p" to page.toString())

    /** The next few programmes of one channel (the portal's short guide). */
    fun epgShort(ctx: Context, channelId: String, size: Int = 10) = build(ctx,
        "Guide probe: the next programmes of one channel",
        "type" to "itv", "action" to "get_short_epg", "ch_id" to channelId, "size" to size.toString())

    /** The guide for every channel for the next [periodHours] hours. Large: the caller sets a response cap. */
    fun epgInfo(ctx: Context, periodHours: Int) = build(ctx,
        "Guide probe: guide data for all channels for a few hours",
        "type" to "itv", "action" to "get_epg_info", "period" to periodHours.toString())

    /** Every live channel in one answer (the portal has no live search of its own; the app filters them). */
    fun allChannels(ctx: Context) = build(ctx, "Search: all live channels, for filtering on the device",
        "type" to "itv", "action" to "get_all_channels")

    /** Children of a VOD title: seasons (seasonId "0"), a season's episodes, or an episode's files. */
    fun vodChildren(ctx: Context, movieId: String, seasonId: String = "0", episodeId: String = "0", page: Int = 1) = build(ctx,
        "Step 4f: ${levelName(seasonId, episodeId)}, page $page",
        "type" to "vod", "action" to "get_ordered_list", "movie_id" to movieId, "season_id" to seasonId,
        "episode_id" to episodeId, "category" to "*", "fav" to "0", "sortby" to "added", "hd" to "0",
        "not_ended" to "0", "p" to page.toString())

    internal fun levelName(seasonId: String, episodeId: String) = when {
        seasonId == "0" -> "seasons of a show"
        episodeId == "0" -> "episodes of a season"
        else -> "files of an episode"
    }

    /** Session keep-alive (the shape open-source clients send), interval below watchdog_timeout. */
    fun watchdog(ctx: Context) = build(ctx, "Step 4g: watchdog get_events (keep-alive)",
        "type" to "watchdog", "action" to "get_events", "init" to "0", "cur_play_type" to "1", "event_active_id" to "0")

    /** VOD create_link: movie (series "") or episode (series = episode number on older portals). */
    fun vodCreateLink(ctx: Context, cmd: String, series: String = "") = build(ctx,
        "Step 4f: VOD create_link (${if (series.isEmpty()) "movie or episode cmd" else "episode number"})",
        "type" to "vod", "action" to "create_link", "cmd" to cmd, "series" to series,
        "forced_storage" to "undefined", "disable_ad" to "0", "download" to "0", "force_ch_link_check" to "0")

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

    /**
     * The portal's expired-session signal: an HTTP 200 whose body is the text
     * "Authorization failed." instead of JSON (behavior of Stalker middleware as handled by
     * open-source clients). The caller re-authenticates once.
     */
    val authFailed: Boolean get() {
        val resp = outcome as? ProbeOutcome.Response ?: return false
        return js == null && String(resp.bodyPrefix, Charsets.UTF_8).contains("Authorization failed", ignoreCase = true)
    }
}

/** A rating value (0 to 10) and where it came from ("IMDb" or "Kinopoisk"). */
data class ShownRating(val value: Double, val source: String)

/**
 * A VOD entry (movie, show, season or episode). [cmd] is needed for create_link but is never
 * printed: [toString] omits it.
 */
class VodItem(
    val id: String,
    val name: String,
    val isSeries: Boolean,
    val isSeason: Boolean,
    val isEpisode: Boolean,
    val cmd: String?,
    /** Episode numbers listed on the item itself (older portals). */
    val episodeNumbers: List<String>,
    /** A file entry under an episode (fourth navigation level). */
    val isFile: Boolean = false,
    /** cmd values of files listed inline in an episode's `series_files`, if any. Never printed. */
    val fileCmds: List<String> = emptyList(),
    // Catalog metadata (text is raw from the portal; the UI decodes entities). Labels, not identifying data.
    val year: String? = null,
    val description: String? = null,
    val actors: String? = null,
    val director: String? = null,
    val genres: String? = null,
    /** The portal's own IMDb rating, null if absent or zero (hide the badge). */
    val imdbRating: Double? = null,
    val kinopoiskRating: Double? = null,
    /** MPAA rating or age restriction as sent ("PG-13", "16+"), if any. */
    val age: String? = null,
    /** Poster address as sent: absolute, or a path on the portal host. Resolved by the app. */
    val posterUri: String? = null,
) {
    /**
     * The rating to show: the portal's IMDb value if it sent one, else its Kinopoisk value, labelled as that and
     * never passed off as IMDb. On the real portal IMDb is almost always 0 and Kinopoisk is filled for about half.
     */
    val shownRating: ShownRating?
        get() = imdbRating?.let { ShownRating(it, "IMDb") } ?: kinopoiskRating?.let { ShownRating(it, "Kinopoisk") }

    override fun toString() = "VodItem(id=$id, name=$name, series=$isSeries, season=$isSeason, episode=$isEpisode, file=$isFile, inlineFiles=${fileCmds.size})"

    /** The cmd to use for create_link: the item's own, else the first inline file's. */
    val playableCmd: String? get() = cmd ?: fileCmds.firstOrNull()

    companion object {
        /** A non-blank text field, or null. */
        private fun text(o: JsonObject, key: String): String? =
            (o[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "0" }

        private fun flag(o: JsonObject, key: String): Boolean =
            (o[key] as? JsonPrimitive)?.contentOrNull?.lowercase() in setOf("1", "true")

        /** Items from {"data":[...]} of a get_ordered_list payload. */
        fun listFrom(js: JsonElement?): List<VodItem> {
            val data = ((js as? JsonObject)?.get("data") as? JsonArray) ?: return emptyList()
            return data.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                VodItem(
                    id = id,
                    name = (o["name"] as? JsonPrimitive)?.contentOrNull ?: "?",
                    isSeries = flag(o, "is_series"),
                    isSeason = flag(o, "is_season"),
                    isEpisode = flag(o, "is_episode"),
                    cmd = (o["cmd"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
                    episodeNumbers = (o["series"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
                    isFile = flag(o, "is_file"),
                    // series_files may be a count, an id list, or a list of file objects; only
                    // objects with a non-blank cmd are usable.
                    fileCmds = (o["series_files"] as? JsonArray)?.mapNotNull { f ->
                        ((f as? JsonObject)?.get("cmd") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                    }.orEmpty(),
                    year = text(o, "year"),
                    description = text(o, "description"),
                    actors = text(o, "actors"),
                    director = text(o, "director"),
                    genres = text(o, "genres_str"),
                    imdbRating = PortalText.rating(text(o, "rating_imdb")),
                    kinopoiskRating = PortalText.rating(text(o, "rating_kinopoisk")),
                    age = text(o, "rating_mpaa") ?: text(o, "age"),
                    posterUri = text(o, "screenshot_uri") ?: text(o, "pic"),
                )
            }
        }
    }
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

    /** Live genres, parsed. The summary holds only a count. */
    fun liveGenres(): Pair<StalkerResult, List<LiveGenre>> {
        var parsed: List<LiveGenre> = emptyList()
        val r = call(StalkerRequests.genres(ctx())) { js ->
            parsed = LiveCatalogParser.genres(js)
            listOf("genres: ${parsed.size}")
        }
        return r to parsed
    }

    /** One page (1-based) of live channels in [genreId] ("*" for all). Null page if the payload is unusable. */
    fun liveChannels(genreId: String, page: Int): Pair<StalkerResult, LiveChannelPage?> {
        var parsed: LiveChannelPage? = null
        val r = call(StalkerRequests.channelsInGenre(ctx(), genreId, page)) { js ->
            parsed = LiveCatalogParser.channelPage(js, page)
            listOf("channels page $page: ${parsed?.items?.size ?: "?"} of ${parsed?.totalItems ?: "?"}")
        }
        return r to parsed
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
        vodPageItems(type, category, page).first

    /** [vodPage] plus the parsed items (for choosing a movie or show). */
    fun vodPageItems(type: String, category: String = "*", page: Int = 1): Pair<StalkerResult, List<VodItem>> {
        var parsed: List<VodItem> = emptyList()
        val r = call(StalkerRequests.vodPage(ctx(), type, category, page)) { js ->
            parsed = VodItem.listFrom(js)
            vodPageSummary(type, page, js)
        }
        return r to parsed
    }

    /** One page (1-based) of VOD titles in [category] ("*" = all), parsed with its total. Summary: counts only. */
    fun vodTitles(category: String, page: Int): Pair<StalkerResult, VodPage?> {
        var parsed: VodPage? = null
        val r = call(StalkerRequests.vodPage(ctx(), "vod", category, page)) { js ->
            parsed = VodCatalogParser.page(js, page)
            listOf("vod page $page: ${parsed?.items?.size ?: "?"} of ${parsed?.totalItems ?: "?"}")
        }
        return r to parsed
    }

    /** One page (1-based) of VOD titles matching [query]. Summary: counts only, never the text. */
    fun vodSearch(query: String, page: Int, category: String = "*"): Pair<StalkerResult, VodPage?> {
        var parsed: VodPage? = null
        val r = call(StalkerRequests.vodSearch(ctx(), query, page, category)) { js ->
            parsed = VodCatalogParser.page(js, page)
            listOf("search page $page: ${parsed?.items?.size ?: "?"} of ${parsed?.totalItems ?: "?"}")
        }
        return r to parsed
    }

    /** One page (1-based) of live channels matching [query]. Summary: counts only, never the text. */
    fun channelsSearch(query: String, page: Int): Pair<StalkerResult, LiveChannelPage?> {
        var parsed: LiveChannelPage? = null
        val r = call(StalkerRequests.channelsSearch(ctx(), query, page)) { js ->
            parsed = LiveCatalogParser.channelPage(js, page)
            listOf("channel search page $page: ${parsed?.items?.size ?: "?"} of ${parsed?.totalItems ?: "?"}")
        }
        return r to parsed
    }

    /** Guide probe: the shape (key names, counts, time spread) of one channel's short guide. Never programme text. */
    fun epgShort(channelId: String, size: Int = 10): StalkerResult =
        call(StalkerRequests.epgShort(ctx(), channelId, size)) { js -> EpgShape.describe(js, System.currentTimeMillis() / 1000) }

    /** Guide probe: the shape of the all-channels guide for [periodHours] hours. Up to 8 MB read. */
    fun epgInfo(periodHours: Int): StalkerResult =
        call(StalkerRequests.epgInfo(ctx(), periodHours), bodyLimit = ALL_CHANNELS_MAX_BYTES) { js -> EpgShape.describe(js, System.currentTimeMillis() / 1000) }

    /** All live channels, for on-device search. Null if the payload is unusable. Summary: the count only. */
    fun allChannels(): Pair<StalkerResult, List<LiveChannel>?> {
        var parsed: List<LiveChannel>? = null
        val r = call(StalkerRequests.allChannels(ctx()), bodyLimit = ALL_CHANNELS_MAX_BYTES) { js ->
            parsed = LiveCatalogParser.allChannels(js)
            listOf("all channels: ${parsed?.size ?: "?"}")
        }
        return r to parsed
    }

    /**
     * One page (1-based) of a show's children, parsed with its total: seasons ([seasonId] "0"), a season's episodes
     * ([episodeId] "0"), or an episode's files. Summary: counts only.
     */
    fun vodChildPage(movieId: String, seasonId: String = "0", episodeId: String = "0", page: Int = 1): Pair<StalkerResult, VodPage?> {
        var parsed: VodPage? = null
        val r = call(StalkerRequests.vodChildren(ctx(), movieId, seasonId, episodeId, page)) { js ->
            parsed = VodCatalogParser.page(js, page)
            listOf("${StalkerRequests.levelName(seasonId, episodeId)} page $page: ${parsed?.items?.size ?: "?"} of ${parsed?.totalItems ?: "?"}")
        }
        return r to parsed
    }

    /** Seasons, episodes or files of a VOD title (by level), parsed; summary counts kinds safely. */
    fun vodChildren(movieId: String, seasonId: String = "0", episodeId: String = "0"): Pair<StalkerResult, List<VodItem>> {
        var parsed: List<VodItem> = emptyList()
        val r = call(StalkerRequests.vodChildren(ctx(), movieId, seasonId, episodeId)) { js ->
            parsed = VodItem.listFrom(js)
            val o = js as? JsonObject
            val level = when { seasonId == "0" -> "seasons"; episodeId == "0" -> "episodes"; else -> "files" }
            listOf(
                "$level total: ${o?.get("total_items")?.let(::shortValue) ?: "?"}; on page: ${parsed.size}",
                "flags on page: season=${parsed.count { it.isSeason }} episode=${parsed.count { it.isEpisode }} file=${parsed.count { it.isFile }} series=${parsed.count { it.isSeries }}",
                "inline series_files cmds: ${parsed.sumOf { it.fileCmds.size }}",
                "sample: ${parsed.take(5).joinToString(" ; ") { it.name }}",
                "cmd kinds: ${parsed.mapNotNull { it.cmd?.let(::vodCmdKind) }.groupingBy { it }.eachCount().entries.joinToString { "${it.key} x${it.value}" }.ifEmpty { "-" }}",
                "item keys: ${(o?.get("data") as? JsonArray)?.firstOrNull()?.let { (it as? JsonObject)?.keys?.sorted()?.joinToString(",") } ?: "-"}",
            )
        }
        return r to parsed
    }

    /** VOD create_link; returns the playable URL (prefix word stripped). */
    /** VOD create_link with its external subtitles, if the portal sent any. Null link if there is no stream address. */
    fun vodLink(cmd: String, series: String = ""): Pair<StalkerResult, VodLink?> {
        var link: VodLink? = null
        val r = call(StalkerRequests.vodCreateLink(ctx(), cmd, series)) { js ->
            val url = ((js as? JsonObject)?.str("cmd"))?.let(::streamUrlFromCmd)
            val subs = SubtitleParser.fromLink(js)
            link = url?.let { VodLink(it, subs) }
            listOf("vod link returned: ${if (url != null) "yes" else "no"}", "subtitles offered: ${subs.size}")
        }
        return r to link
    }

    fun vodCreateLink(cmd: String, series: String = ""): Pair<StalkerResult, String?> {
        var url: String? = null
        val r = call(StalkerRequests.vodCreateLink(ctx(), cmd, series)) { js ->
            val linkCmd = (js as? JsonObject)?.str("cmd")
            url = linkCmd?.let(::streamUrlFromCmd)
            val error = (js as? JsonObject)?.str("error")
            listOfNotNull(
                "vod link returned: ${if (url != null) "yes, shape ${cmdShape(linkCmd!!)}" else "no (cmd kind: ${linkCmd?.let(::vodCmdKind) ?: "-"})"}",
                error?.let { "error: '${it.take(60)}'" },
            ) + keysLine(js)
        }
        return r to url
    }

    /** Safe by construction: totals, page size, sample titles, is_series counts, cmd KINDS, field names. */
    private fun vodPageSummary(type: String, page: Int, js: JsonElement?): List<String> {
        val o = js as? JsonObject
        val items = (o?.get("data") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        val seriesFlag = items.count { it.str("is_series") == "1" }
        val withEpisodes = items.count { (it["series"] as? JsonArray)?.isNotEmpty() == true }
        val cmdKinds = items.mapNotNull { it.str("cmd")?.let(::vodCmdKind) }.groupingBy { it }.eachCount()
        return listOf(
                "$type total: ${o?.get("total_items")?.let(::shortValue) ?: "?"} (page size ${o?.get("max_page_items")?.let(::shortValue) ?: "?"})",
                "page $page items: ${items.size}; is_series=1: $seriesFlag; with episode list: $withEpisodes",
                "sample: ${items.take(5).joinToString(" ; ") { "${it.str("name") ?: "?"} (${it.str("year") ?: "?"})" }}",
                "cmd kinds: ${cmdKinds.entries.joinToString { "${it.key} x${it.value}" }.ifEmpty { "-" }}",
                "item keys: ${items.firstOrNull()?.keys?.sorted()?.joinToString(",") ?: "-"}",
            )
    }

    /** Keep-alive call. Summary: data keys and the event name if one is pending (not identifying). */
    fun watchdog(): StalkerResult = call(StalkerRequests.watchdog(ctx())) { js ->
        val data = (js as? JsonObject)?.get("data") as? JsonObject
        listOfNotNull(
            "watchdog ok",
            data?.let { "data keys: ${it.keys.sorted().joinToString(",")}" },
            data?.str("event")?.takeIf { it.isNotBlank() }?.let { "pending event: $it" },
        ) + keysLine(js)
    }

    /** Re-authenticates after an expired session: handshake (new token) then get_profile. */
    fun reauthenticate(): Pair<StalkerResult, StalkerResult?> {
        val hs = handshake()
        if (!hs.ok || token == null) return hs to null
        return hs to profile()
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

    private fun call(request: ProbeRequest, bodyLimit: Int? = null, summarize: (JsonElement?) -> List<String>): StalkerResult {
        val outcome = if (bodyLimit != null) probe.send(request, bodyLimit) else probe.send(request)
        val js = (outcome as? ProbeOutcome.Response)?.let { parseJs(it.bodyPrefix) }
        val body = (outcome as? ProbeOutcome.Response)?.bodyPrefix
        val summary = when {
            js != null -> summarize(js)
            body != null && String(body, Charsets.UTF_8).contains("Authorization failed", ignoreCase = true) ->
                listOf("portal says: Authorization failed (session expired)")
            body != null && bodyLimit != null && body.size >= bodyLimit -> listOf("response larger than the size cap ($bodyLimit bytes)")
            else -> listOf("no JSON payload in the response")
        }
        return StalkerResult(outcome, js, summary)
    }

    companion object {
        /** The full channel list is several megabytes (about 5,000 entries with many fields each); the usual 1 MB cap cuts it off. */
        const val ALL_CHANNELS_MAX_BYTES = 8_000_000

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
         * Category id by title: exact (case-insensitive) match, else the shortest title that
         * contains [title]. Titles on this portal combine labels with " | ".
         */
        fun pickCategory(cats: List<Pair<String, String>>, title: String): Pair<String, String>? {
            val t = title.trim()
            return cats.firstOrNull { it.second.trim().equals(t, ignoreCase = true) }
                ?: cats.filter { it.second.contains(t, ignoreCase = true) }.minByOrNull { it.second.length }
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
