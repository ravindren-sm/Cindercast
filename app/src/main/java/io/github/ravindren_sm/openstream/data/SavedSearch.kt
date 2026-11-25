package io.github.ravindren_sm.openstream.data

import android.content.SharedPreferences
import io.github.ravindren_sm.openstream.profiles.db.CatalogIndexDao
import io.github.ravindren_sm.openstream.provider.VodItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Which kinds of title a saved search covers. */
enum class SavedKind { BOTH, MOVIES, SHOWS }

/**
 * A named, editable search that runs against the title index on the device (no typing, no portal request).
 *
 * [genres] are display names ("Comedy", "Science Fiction", "Sci-Fi"); the query matches both spellings of a genre (see [SavedGenres]).
 * [allGenres] true: a title needs EVERY chosen genre; false: ANY of them. [languages] are the tag service's codes ("en", "sv").
 * Limits applied when the query runs: at most [MAX_GENRES] genres and [MAX_LANGUAGES] languages (extras are ignored).
 */
data class SavedSearch(
    val id: String,
    val name: String,
    val genres: Set<String> = emptySet(),
    val allGenres: Boolean = false,
    val languages: Set<String> = emptySet(),
    val minRating: Double? = null,
    val yearFrom: Int? = null,
    val kind: SavedKind = SavedKind.BOTH,
    val sort: BrowseSort = BrowseSort.BEST,
) {
    /** No criteria at all: running it would list the whole index. */
    val isEmpty: Boolean get() = genres.isEmpty() && languages.isEmpty() && minRating == null && yearFrom == null && kind == SavedKind.BOTH

    /** Compact description, for example "Comedy + Sci-Fi · English · 6+ · from 2010" ("+" joins genres that must all match, "/" any). */
    fun summary(): String {
        val parts = buildList {
            when (kind) { SavedKind.MOVIES -> add("Movies"); SavedKind.SHOWS -> add("Shows"); SavedKind.BOTH -> Unit }
            if (genres.isNotEmpty()) add(genres.joinToString(if (allGenres) " + " else " / "))
            if (languages.isNotEmpty()) add(languages.joinToString(" / ") { SavedLanguages.nameOf(it) })
            minRating?.let { add(ratingText(it)) }
            yearFrom?.let { add("from $it") }
        }
        return if (parts.isEmpty()) "Everything" else parts.joinToString(" · ")
    }

    /** A short default name when the viewer gives none, for example "English Comedy / Sci-Fi Movies 6+ from 2010". */
    fun autoName(): String {
        val kindWord = when (kind) {
            SavedKind.MOVIES -> "Movies"; SavedKind.SHOWS -> "Shows"
            SavedKind.BOTH -> if (genres.isEmpty() && languages.isEmpty()) "Titles" else ""
        }
        val text = listOf(
            languages.joinToString(" / ") { SavedLanguages.nameOf(it) },
            genres.joinToString(if (allGenres) " + " else " / "),
            kindWord,
            minRating?.let { ratingText(it) }.orEmpty(),
            yearFrom?.let { "from $it" }.orEmpty(),
        ).filter { it.isNotEmpty() }.joinToString(" ")
        return text.take(40).trim()
    }

    private fun ratingText(r: Double) = (if (r == Math.floor(r)) r.toInt().toString() else r.toString()) + "+"

    companion object {
        const val MAX_GENRES = 4
        const val MAX_LANGUAGES = 4
    }
}

/** Language codes of the tag service and their names. */
object SavedLanguages {
    private val names = mapOf(
        "en" to "English", "hi" to "Hindi", "ta" to "Tamil", "te" to "Telugu", "ml" to "Malayalam", "kn" to "Kannada", "bn" to "Bengali",
        "pa" to "Punjabi", "mr" to "Marathi", "ko" to "Korean", "ja" to "Japanese", "zh" to "Chinese", "es" to "Spanish", "fr" to "French",
        "de" to "German", "it" to "Italian", "pt" to "Portuguese", "ru" to "Russian", "tr" to "Turkish", "ar" to "Arabic", "sv" to "Swedish",
    )
    fun nameOf(code: String): String = names[code.lowercase()] ?: code
}

/**
 * The two spellings the index may hold for one genre. Portal lists say "Sci-Fi", the tag service (TMDB) says "Science Fiction".
 * TMDB's combined TV genres ("Sci-Fi & Fantasy", "Action & Adventure", "War & Politics") already contain "Sci-Fi", "Action",
 * "Adventure", "War" as substrings, so the substring match finds them without extra entries. The result is the pair
 * (first, second); the second equals the first when there is no alternative.
 */
object SavedGenres {
    private val scifi = setOf("sci-fi", "science fiction", "sci fi", "scifi", "science-fiction")
    fun spellings(genre: String): Pair<String, String> {
        val g = genre.trim()
        return if (g.lowercase() in scifi) "Sci-Fi" to "Science Fiction" else g to g
    }
}

/** Reads and writes saved searches as JSON; no Android classes, so it is unit-testable. */
object SavedSearchCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(list: List<SavedSearch>): String = buildJsonArray {
        list.forEach { s ->
            add(buildJsonObject {
                put("id", s.id); put("name", s.name)
                put("genres", buildJsonArray { s.genres.forEach { add(JsonPrimitive(it)) } })
                put("allGenres", s.allGenres)
                put("languages", buildJsonArray { s.languages.forEach { add(JsonPrimitive(it)) } })
                s.minRating?.let { put("minRating", it) }
                s.yearFrom?.let { put("yearFrom", it) }
                put("kind", s.kind.name); put("sort", s.sort.name)
            })
        }
    }.toString()

    /** Reads [encode]'s output; missing fields take defaults, unknown fields are ignored, entries without an id are dropped, unreadable text gives nothing. */
    fun decode(text: String?): List<SavedSearch> = try {
        if (text.isNullOrBlank()) emptyList()
        else (json.parseToJsonElement(text) as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
            fun strings(k: String): Set<String> = (o[k] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toCollection(LinkedHashSet())
            val id = s("id")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            SavedSearch(
                id = id, name = s("name").orEmpty(),
                genres = strings("genres"), allGenres = (o["allGenres"] as? JsonPrimitive)?.booleanOrNull ?: false,
                languages = strings("languages"),
                minRating = (o["minRating"] as? JsonPrimitive)?.doubleOrNull,
                yearFrom = (o["yearFrom"] as? JsonPrimitive)?.intOrNull,
                kind = SavedKind.entries.firstOrNull { it.name == s("kind") } ?: SavedKind.BOTH,
                sort = BrowseSort.entries.firstOrNull { it.name == s("sort") } ?: BrowseSort.BEST,
            )
        }
    } catch (_: Exception) { emptyList() }
}

/** Each profile's saved searches, kept on the device under a key that names the profile. */
class SavedSearchStore(private val prefs: SharedPreferences) {
    private fun key(profileId: Long) = "p$profileId.saved_searches"

    fun list(profileId: Long): List<SavedSearch> = SavedSearchCodec.decode(prefs.getString(key(profileId), null))
    fun get(profileId: Long, id: String): SavedSearch? = list(profileId).firstOrNull { it.id == id }

    /** Inserts, or replaces the search with the same id in place (order kept); a new one goes last. Returns the list now stored. */
    fun save(profileId: Long, search: SavedSearch): List<SavedSearch> {
        val old = list(profileId)
        val next = if (old.any { it.id == search.id }) old.map { if (it.id == search.id) search else it } else old + search
        write(profileId, next)
        return next
    }

    fun delete(profileId: Long, id: String): List<SavedSearch> {
        val next = list(profileId).filterNot { it.id == id }
        write(profileId, next)
        return next
    }

    fun newId(): String = java.util.UUID.randomUUID().toString().take(8)

    private fun write(profileId: Long, list: List<SavedSearch>) { prefs.edit().putString(key(profileId), SavedSearchCodec.encode(list)).apply() }
}

/** Runs a [SavedSearch] against the title index on the device. */
class SavedSearchRunner(private val dao: CatalogIndexDao, private val enricher: TagEnricher? = null) {
    class Result(val items: List<VodItem>, val total: Int)

    suspend fun run(s: SavedSearch, limit: Int = 600): Result {
        val g = s.genres.take(SavedSearch.MAX_GENRES).map { SavedGenres.spellings(it) }
        val l = s.languages.take(SavedSearch.MAX_LANGUAGES).map { it.trim().lowercase() }
        val kind = when (s.kind) { SavedKind.MOVIES -> "MOVIE"; SavedKind.SHOWS -> "SHOW"; SavedKind.BOTH -> null }
        val all = if (s.allGenres) 1 else 0
        // Same conversion as the browse screens (IndexedTitles.page): lean rows via toVodItem, then optional tag enrichment.
        val rows = dao.savedSearch(
            kind, g.getOrNull(0)?.first, g.getOrNull(1)?.first, g.getOrNull(2)?.first, g.getOrNull(3)?.first,
            g.getOrNull(0)?.second, g.getOrNull(1)?.second, g.getOrNull(2)?.second, g.getOrNull(3)?.second,
            all, l.getOrNull(0), l.getOrNull(1), l.getOrNull(2), l.getOrNull(3), s.minRating, s.yearFrom, s.sort.code, limit,
        )
        val total = dao.savedSearchCount(
            kind, g.getOrNull(0)?.first, g.getOrNull(1)?.first, g.getOrNull(2)?.first, g.getOrNull(3)?.first,
            g.getOrNull(0)?.second, g.getOrNull(1)?.second, g.getOrNull(2)?.second, g.getOrNull(3)?.second,
            all, l.getOrNull(0), l.getOrNull(1), l.getOrNull(2), l.getOrNull(3), s.minRating, s.yearFrom,
        )
        val items = rows.map { it.toVodItem() }.let { list -> enricher?.enrich(list) ?: list }
        return Result(items, total)
    }
}
