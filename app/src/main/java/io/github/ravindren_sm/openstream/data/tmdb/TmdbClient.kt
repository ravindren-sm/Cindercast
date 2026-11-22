package io.github.ravindren_sm.openstream.data.tmdb

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The calls the tagger makes. An interface so tests can answer without a network. */
interface TmdbApi {
    /** Candidates for [name] (and [year], if known). */
    fun search(name: String, year: String?, series: Boolean): TmdbResult<List<TmdbCandidate>>
    fun details(id: Long, series: Boolean): TmdbResult<TmdbDetails?>
}

sealed interface TmdbResult<out T> {
    class Ok<T>(val value: T) : TmdbResult<T>
    /** The key was refused (HTTP 401 or 403): stop and tell the viewer; asking again will not help. */
    data object KeyRefused : TmdbResult<Nothing>
    /** Asked too fast (HTTP 429): wait [seconds] and go on. */
    class Slow(val seconds: Long) : TmdbResult<Nothing>
    /** Network or server trouble: try again later. */
    class Failed(val message: String) : TmdbResult<Nothing>
}

/**
 * The online tag service (themoviedb.org). Sends only the title text and, when known, the year, plus the viewer's own key; never an address, MAC,
 * profile or viewing history of this app. Requests and addresses are not logged, since the key travels with them. A v3 key goes in the
 * query; a long v4 read token goes in the Authorization header.
 */
class TmdbClient(
    private val key: String,
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build(),
    private val baseUrl: String = "https://api.themoviedb.org/3",
) : TmdbApi {
    private val bearer = key.length > 40

    override fun search(name: String, year: String?, series: Boolean): TmdbResult<List<TmdbCandidate>> {
        val url = "$baseUrl/search/${if (series) "tv" else "movie"}".toHttpUrl().newBuilder()
            .addQueryParameter("query", name).addQueryParameter("include_adult", "false").addQueryParameter("language", "en-US")
            .apply { if (!bearer) addQueryParameter("api_key", key) }.build()
        return get(url) { TmdbParser.candidates(it, series) }
    }

    override fun details(id: Long, series: Boolean): TmdbResult<TmdbDetails?> {
        val url = "$baseUrl/${if (series) "tv" else "movie"}/$id".toHttpUrl().newBuilder()
            .addQueryParameter("append_to_response", if (series) "content_ratings,keywords" else "release_dates,keywords").addQueryParameter("language", "en-US")
            .apply { if (!bearer) addQueryParameter("api_key", key) }.build()
        return get(url) { TmdbParser.details(it, series) }
    }

    private fun <T> get(url: okhttp3.HttpUrl, parse: (String) -> T): TmdbResult<T> = try {
        val request = Request.Builder().url(url).apply { if (bearer) header("Authorization", "Bearer $key") }.build()
        http.newCall(request).execute().use { r ->
            when {
                r.code == 401 || r.code == 403 -> TmdbResult.KeyRefused
                r.code == 429 -> TmdbResult.Slow(r.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 120) ?: 10)
                r.code == 404 -> TmdbResult.Ok(parse("{}"))
                !r.isSuccessful -> TmdbResult.Failed("HTTP ${r.code}")
                else -> TmdbResult.Ok(parse(r.body?.string().orEmpty()))
            }
        }
    } catch (e: IOException) { TmdbResult.Failed(e.javaClass.simpleName) }
}
