package io.github.ravindren_sm.openstream.data.tmdb

import io.github.ravindren_sm.openstream.profiles.AgeLevels
import io.github.ravindren_sm.openstream.data.TextFilter
import io.github.ravindren_sm.openstream.data.TitleMerge
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One possible match from the tag service's search. [year] is the four-digit release (or first air) year, if it gave a date. */
data class TmdbCandidate(val id: Long, val title: String, val originalTitle: String?, val year: String?, val popularity: Double, val voteCount: Int)

/** What the tag service knows about one title. [certifications] maps a country code to the label shown there ("US" to "PG-13"). */
data class TmdbDetails(
    val id: Long, val series: Boolean, val certifications: Map<String, String>, val genres: List<String>,
    val language: String?, val rating: Double?, val keywords: List<String>,
)

/** Reads the service's JSON answers. Tolerant: a missing or odd field is skipped, never an exception. */
object TmdbParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parse(text: String): JsonObject? = try { json.parseToJsonElement(text) as? JsonObject } catch (_: Exception) { null }
    private fun JsonElement?.str() = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonElement?.arr(): JsonArray = (this as? JsonArray) ?: JsonArray(emptyList())
    private fun yearOf(date: String?) = date?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }

    fun candidates(text: String, series: Boolean): List<TmdbCandidate> =
        parse(text)?.get("results").arr().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = (o["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            val title = (if (series) o["name"] else o["title"]).str() ?: return@mapNotNull null
            TmdbCandidate(
                id, title, (if (series) o["original_name"] else o["original_title"]).str(),
                yearOf((if (series) o["first_air_date"] else o["release_date"]).str()),
                (o["popularity"] as? JsonPrimitive)?.doubleOrNull ?: 0.0, (o["vote_count"] as? JsonPrimitive)?.intOrNull ?: 0,
            )
        }

    fun details(text: String, series: Boolean): TmdbDetails? {
        val o = parse(text) ?: return null
        val id = (o["id"] as? JsonPrimitive)?.longOrNull ?: return null
        val certs = HashMap<String, String>()
        if (series) {
            (o["content_ratings"] as? JsonObject)?.get("results").arr().forEach { r ->
                val ro = r as? JsonObject ?: return@forEach
                val country = ro["iso_3166_1"].str(); val label = ro["rating"].str()
                if (country != null && label != null) certs.putIfAbsent(country, label)
            }
        } else {
            (o["release_dates"] as? JsonObject)?.get("results").arr().forEach { r ->
                val ro = r as? JsonObject ?: return@forEach
                val country = ro["iso_3166_1"].str() ?: return@forEach
                // The first non-empty certification among that country's releases (theatrical releases come first in practice).
                val label = ro["release_dates"].arr().firstNotNullOfOrNull { d -> (d as? JsonObject)?.get("certification").str() }
                if (label != null) certs.putIfAbsent(country, label)
            }
        }
        val keywords = (o["keywords"] as? JsonObject).let { k -> (k?.get("keywords") ?: k?.get("results")).arr() }.mapNotNull { (it as? JsonObject)?.get("name").str() }
        return TmdbDetails(
            id, series, certs, o["genres"].arr().mapNotNull { (it as? JsonObject)?.get("name").str() },
            o["original_language"].str(), (o["vote_average"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 }, keywords,
        )
    }
}

/** Picks one age level out of the several countries' certifications. */
object Certification {
    /** A level and the label it came from. */
    class Pick(val label: String, val level: Int)

    /**
     * The US label when it is known (the table the rest of the app uses); otherwise the strictest known label among the other
     * countries, so a gap in one country never makes a title look gentler than another country judged it. Null when none is known.
     */
    fun pick(certifications: Map<String, String>): Pick? {
        certifications["US"]?.let { l -> AgeLevels.levelOf(l)?.let { return Pick(l, it) } }
        return certifications.entries.mapNotNull { (_, l) -> AgeLevels.levelOf(l)?.let { Pick(l, it) } }.maxByOrNull { it.level }
    }
}

/** Decides whether a search result is the portal's title. Cautious: a wrong match is worse than none, so doubtful ones return null. */
object TmdbMatcher {
    private val TRAILING_YEAR = Regex("[\\s(\\[]*((?:19|20)\\d{2})[)\\]]?\\s*$")

    /** The name to search for, and a year found at its end (if the portal wrote one there). */
    fun clean(name: String): Pair<String, String?> {
        val base = TitleMerge.baseKey(name)
        val m = TRAILING_YEAR.find(base)
        return if (m != null && base.length > m.value.length) base.removeRange(m.range).trim() to m.groupValues[1] else base to null
    }

    fun pick(name: String, year: String?, candidates: List<TmdbCandidate>): TmdbCandidate? {
        val (key, trailing) = clean(name)
        if (key.isEmpty()) return null
        val wanted = year?.takeIf { it.length == 4 && it.all(Char::isDigit) } ?: trailing
        val exact = candidates.filter { c -> TitleMerge.baseKey(c.title) == key || (c.originalTitle != null && TitleMerge.baseKey(c.originalTitle) == key) }
        val scored = exact.mapNotNull { c ->
            val diff = if (wanted != null && c.year != null) kotlin.math.abs(wanted.toInt() - c.year.toInt()) else null
            when {
                diff == null -> if (c.voteCount >= 10 || exact.size == 1) c to 0 else null          // no year to compare: needs some standing
                diff == 0 -> c to 2
                diff == 1 -> c to 1
                else -> null                                                                          // a different year is a different film
            }
        }
        return scored.maxWithOrNull(compareBy<Pair<TmdbCandidate, Int>> { it.second }.thenBy { it.first.voteCount })?.first
    }
}

/** Words in the tag service's genres or keywords that make a Kids profile hide the title (the same list as for names). */
object TagFlags {
    private val words = TextFilter()
    fun flagged(genres: List<String>, keywords: List<String>): Boolean = words.blocksAny(genres.joinToString(" "), keywords.joinToString(" "))
}
