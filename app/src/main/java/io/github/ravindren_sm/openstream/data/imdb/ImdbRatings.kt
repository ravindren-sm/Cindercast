package io.github.ravindren_sm.openstream.data.imdb

import io.github.ravindren_sm.openstream.profiles.db.TitleTagDao
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * IMDb's free ratings file (title.ratings.tsv.gz), which IMDb publishes for personal, non-commercial use and allows to be kept as a local copy.
 * Terms (read 2026-11-22 at help.imdb.com and data.imdb.com): personal and non-commercial use only; do not republish or turn the data into a database
 * of movie information for others; no scraping of the site; the credit line below must be shown; IMDb may withdraw permission at any time.
 * Each device downloads its own copy straight from IMDb, keeps only the rating of titles it already knows, and shares nothing.
 */
object ImdbRatings {
    const val CREDIT = "Information courtesy of IMDb (https://www.imdb.com). Used with permission."
    const val URL = "https://datasets.imdbws.com/title.ratings.tsv.gz"

    /** Reads "tconst, averageRating, numVotes" lines (a header line is skipped) and keeps the rating of the [wanted] ids. */
    fun parse(lines: Sequence<String>, wanted: Set<String>): Map<String, Double> {
        val out = HashMap<String, Double>()
        for (line in lines) {
            val tab1 = line.indexOf('\t')
            if (tab1 < 3) continue
            val id = line.substring(0, tab1)
            if (id !in wanted) continue
            val tab2 = line.indexOf('\t', tab1 + 1).let { if (it < 0) line.length else it }
            val rating = line.substring(tab1 + 1, tab2).toDoubleOrNull() ?: continue          // the header's "averageRating" is not a number
            if (rating > 0) out[id] = rating
        }
        return out
    }
}

sealed interface ImdbResult {
    class Done(val rated: Int) : ImdbResult
    class Failed(val message: String) : ImdbResult
}

/** Downloads the file and stores the ratings of known titles. Blocking: call off the main thread. */
class ImdbRatingsLoader(
    private val dao: TitleTagDao,
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build(),
    private val url: String = ImdbRatings.URL,
) {
    fun load(): ImdbResult = try {
        val wanted = kotlinx.coroutines.runBlocking { dao.imdbIds() }.toHashSet()
        if (wanted.isEmpty()) ImdbResult.Failed("No titles have an IMDb id yet. Let the online tags finish first.")
        else http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return@use ImdbResult.Failed("IMDb answered HTTP ${r.code}")
            val stream = r.body?.byteStream() ?: return@use ImdbResult.Failed("Empty answer")
            val ratings = BufferedReader(InputStreamReader(GZIPInputStream(stream), Charsets.UTF_8)).use { reader -> ImdbRatings.parse(reader.lineSequence(), wanted) }
            kotlinx.coroutines.runBlocking { dao.setImdbScores(ratings) }
            ImdbResult.Done(ratings.size)
        }
    } catch (e: IOException) { ImdbResult.Failed("Network error (${e.javaClass.simpleName})") }
}
