package io.github.ravindren_sm.openstream.data.omdb

import io.github.ravindren_sm.openstream.profiles.db.TitleTagEntity
import io.github.ravindren_sm.openstream.profiles.AgeLevels
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the optional second source (OMDb) says about one IMDb id. Any field may be missing. */
data class OmdbInfo(val imdbScore: Double?, val rottenTomatoes: Int?, val metascore: Int?, val rated: String?)

sealed interface OmdbResult {
    class Ok(val info: OmdbInfo?) : OmdbResult          // null: the id is unknown to the service
    data object KeyRefused : OmdbResult
    /** The day's allowance is used up; more is available tomorrow (or with a paid key). */
    data object DailyLimit : OmdbResult
    class Failed(val message: String) : OmdbResult
}

object OmdbParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private fun JsonPrimitive?.text() = this?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && !it.equals("N/A", ignoreCase = true) }

    fun parse(text: String): OmdbResult {
        val o = try { json.parseToJsonElement(text) as? JsonObject } catch (_: Exception) { null } ?: return OmdbResult.Failed("unreadable answer")
        if ((o["Response"] as? JsonPrimitive).text().equals("False", ignoreCase = true)) {
            val error = (o["Error"] as? JsonPrimitive).text().orEmpty().lowercase()
            return when {
                "limit" in error -> OmdbResult.DailyLimit
                "invalid api key" in error || "no api key" in error -> OmdbResult.KeyRefused
                else -> OmdbResult.Ok(null)
            }
        }
        val rt = (o["Ratings"] as? JsonArray)?.firstNotNullOfOrNull { e ->
            val r = e as? JsonObject ?: return@firstNotNullOfOrNull null
            if ((r["Source"] as? JsonPrimitive).text()?.contains("Rotten", ignoreCase = true) == true) (r["Value"] as? JsonPrimitive).text()?.removeSuffix("%")?.toIntOrNull() else null
        }
        return OmdbResult.Ok(OmdbInfo(
            (o["imdbRating"] as? JsonPrimitive).text()?.toDoubleOrNull()?.takeIf { it > 0 }, rt,
            (o["Metascore"] as? JsonPrimitive).text()?.toIntOrNull(), (o["Rated"] as? JsonPrimitive).text(),
        ))
    }
}

interface OmdbApi { fun byImdbId(imdbId: String): OmdbResult }

/** The OMDb service. Sends only an IMDb id and the viewer's own key. The address carries the key, so requests are never logged. */
class OmdbClient(
    private val key: String,
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build(),
    private val baseUrl: String = "https://www.omdbapi.com/",
) : OmdbApi {
    override fun byImdbId(imdbId: String): OmdbResult = try {
        val url = baseUrl.toHttpUrl().newBuilder().addQueryParameter("i", imdbId).addQueryParameter("apikey", key).addQueryParameter("r", "json").build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            when {
                r.code == 401 && body.isBlank() -> OmdbResult.KeyRefused
                r.code >= 500 -> OmdbResult.Failed("HTTP ${r.code}")
                else -> OmdbParser.parse(body).let { if (r.code == 401 && it is OmdbResult.Ok) OmdbResult.KeyRefused else it }
            }
        }
    } catch (e: IOException) { OmdbResult.Failed(e.javaClass.simpleName) }
}

interface OmdbStorage {
    suspend fun nextPending(limit: Int): List<TitleTagEntity>
    suspend fun save(tag: TitleTagEntity)
    /** Titles with an IMDb id, and how many of them are scored. */
    suspend fun counts(): Pair<Int, Int>
}

class OmdbStatus(val hasKey: Boolean, val running: Boolean, val withId: Int, val scored: Int, val message: String? = null) {
    companion object { val NONE = OmdbStatus(false, false, 0, 0) }
}

/**
 * Reads the IMDb score and the Rotten Tomatoes percentage for every tagged title that has an IMDb id, a few at a time. Opt-in (its own key and
 * Start). When the day's allowance is used up it waits and tries again later, so a free key quietly works through the list over weeks and a
 * paid key finishes in a day or two. A title the service does not know is marked read so it is not asked again. The Rated label fills the age
 * label for titles the first source left ungraded.
 */
class OmdbTagger(
    private val storage: OmdbStorage,
    private val api: () -> OmdbApi?,
    private val enabled: () -> Boolean,
    private val setEnabled: (Boolean) -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val pauseMillis: Long = 100,
    private val limitWaitMillis: Long = 30 * 60_000,
) {
    var status: OmdbStatus by mutableStateOf(OmdbStatus.NONE)
        private set

    suspend fun refreshStatus(hasKey: Boolean, message: String? = null) { val (withId, scored) = storage.counts(); status = OmdbStatus(hasKey, enabled(), withId, scored, message) }

    suspend fun run(canWork: () -> Boolean) {
        var failures = 0
        while (enabled()) {
            if (!canWork()) { delay(2_000); continue }
            val client = api()
            if (client == null) { setEnabled(false); refreshStatus(false, "No key saved."); return }
            val batch = storage.nextPending(BATCH)
            if (batch.isEmpty()) { refreshStatus(true, "Every title with an IMDb id has been read."); setEnabled(false); return }
            for (group in batch.chunked(WORKERS)) {
                if (!enabled()) return
                while (!canWork()) delay(2_000)
                val results = coroutineScope { group.map { row -> async { row to client.byImdbId(row.imdbId.orEmpty()) } }.awaitAll() }
                if (results.any { it.second == OmdbResult.KeyRefused }) { setEnabled(false); refreshStatus(true, "The service refused the key. Check it and start again."); return }
                var limited = false; var failed = 0
                for ((row, r) in results) when (r) {
                    is OmdbResult.Ok -> save(row, r.info)
                    OmdbResult.DailyLimit -> limited = true
                    is OmdbResult.Failed -> failed++
                    OmdbResult.KeyRefused -> {}
                }
                if (limited) { refreshStatus(true, "Today's allowance is used up; it carries on later."); delay(limitWaitMillis); break }
                if (failed > 0) { if (++failures >= MAX_FAILURES) { setEnabled(false); refreshStatus(true, "Stopped after repeated network errors."); return }; delay(5_000L * failures) } else failures = 0
                delay(pauseMillis)
            }
            refreshStatus(true)
        }
        refreshStatus(api() != null)
    }

    private suspend fun save(row: TitleTagEntity, info: OmdbInfo?) {
        val rated = info?.rated?.let { label -> AgeLevels.levelOf(label)?.let { label to it } }
        storage.save(row.copy(
            imdbScore = info?.imdbScore, rtScore = info?.rottenTomatoes, omdbAt = nowMillis(),
            certLabel = row.certLabel ?: rated?.first, certAge = row.certAge ?: rated?.second,
        ))
    }

    private companion object { const val BATCH = 40; const val WORKERS = 5; const val MAX_FAILURES = 6 }
}
