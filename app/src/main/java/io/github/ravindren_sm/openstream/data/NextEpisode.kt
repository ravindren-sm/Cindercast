package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.VodItem

class SeasonRef(val item: VodItem)
class EpisodeRef(val item: VodItem)

/** Which episode follows another: plain Kotlin over lists, so it is tested without the portal. */
object NextEpisodeLogic {
    class Next(val season: SeasonRef, val episode: EpisodeRef)

    /**
     * The episode after [episodeId] in [seasonId]: the next one in the season, else the first of the next season that has
     * episodes. [episodesOf] returns every episode of a season in order (or null when it could not be read). Null at the end
     * of the show or when something needed could not be read.
     */
    fun next(listedSeasons: List<SeasonRef>, seasonId: String, episodeId: String, episodesOf: (SeasonRef) -> List<EpisodeRef>?): Next? {
        // Portals list seasons and episodes in either order (some newest first: "Season 2" above "Season 1", E12 above E11),
        // so both are put in number order when every name carries a number; otherwise the portal's order is kept.
        val seasons = inNumberOrder(listedSeasons) { seasonNumber(it.item.name) }
        val episodesInOrder = { s: SeasonRef -> episodesOf(s)?.let { list -> inNumberOrder(list) { episodeNumber(it.item.name) } } }
        val si = seasons.indexOfFirst { it.item.id == seasonId }.takeIf { it >= 0 } ?: return null
        val here = episodesInOrder(seasons[si]) ?: return null
        val ei = here.indexOfFirst { it.item.id == episodeId }.takeIf { it >= 0 } ?: return null
        if (ei + 1 < here.size) return Next(seasons[si], here[ei + 1])
        for (k in si + 1 until seasons.size) {
            val first = episodesInOrder(seasons[k])?.firstOrNull() ?: continue
            return Next(seasons[k], first)
        }
        return null
    }

    /** [list] sorted by [number] when every entry has one (a stable sort, so ties keep the portal's order); else unchanged. */
    fun <T> inNumberOrder(list: List<T>, number: (T) -> Int?): List<T> {
        val numbers = list.map(number)
        if (numbers.any { it == null }) return list
        return list.indices.sortedBy { numbers[it]!! }.map { list[it] }
    }

    private val SEASON_WORD = Regex("""\bSEASON\s*(\d{1,3})\b""", RegexOption.IGNORE_CASE)
    private val SEASON_SHORT = Regex("""(?<![A-Z0-9])S(\d{1,3})(?=E\d|\b)""", RegexOption.IGNORE_CASE)
    private val EPISODE = Regex("""(?<![A-Z])(?:EPISODE|EP|E)\s*(\d{1,4})\b""", RegexOption.IGNORE_CASE)

    /** The season number in a season's name ("Season 2. Show S02", "Show - Season 1", "S03"), or null. */
    fun seasonNumber(name: String): Int? =
        (SEASON_WORD.find(name) ?: SEASON_SHORT.find(name))?.groupValues?.get(1)?.toIntOrNull()

    /** The episode number in an episode's name ("S02 E11 | Title", "S02E11", "Episode 4"), or null. */
    fun episodeNumber(name: String): Int? = EPISODE.find(name)?.groupValues?.get(1)?.toIntOrNull()

    /** "show/season/episode" parts of a resume key, or null for anything else. */
    fun parseKey(resumeKey: String): Triple<String, String, String>? =
        resumeKey.split('/').takeIf { it.size == 3 && it.none(String::isEmpty) }?.let { Triple(it[0], it[1], it[2]) }
}

/**
 * Finds and prepares the next episode for the player: reads the seasons, the episodes around the current one and the files of the
 * next episode (a handful of requests), and returns a play target for its first file. Blocking: call off the main thread.
 */
class NextEpisodeFinder(private val catalog: VodCatalog) {
    fun find(current: PlayTarget): PlayTarget? {
        if (current.kind != "EPISODE") return null
        val (showId, seasonId, episodeId) = NextEpisodeLogic.parseKey(current.resumeKey) ?: return null
        val show = VodItem(id = showId, name = current.historyTitle, isSeries = true, isSeason = false, isEpisode = false, cmd = null, episodeNumbers = emptyList())
        val seasons = (catalog.seasons(show) as? CatalogResult.Ok)?.value?.map(::SeasonRef) ?: return null
        val cache = HashMap<String, List<EpisodeRef>?>()
        val next = NextEpisodeLogic.next(seasons, seasonId, episodeId) { season ->
            cache.getOrPut(season.item.id) { allEpisodes(show, season.item) }
        } ?: return null
        val file = (catalog.files(show, next.season.item, next.episode.item) as? CatalogResult.Ok)?.value?.firstOrNull() ?: return null
        val name = PortalTextLite.clean(next.episode.item.name)
        return PlayTarget(
            file, "${current.historyTitle}: $name", ResumeKeys.episode(showId, next.season.item.id, next.episode.item.id),
            kind = "EPISODE", titleId = showId, subtitle = "${PortalTextLite.clean(next.season.item.name)} · $name",
            posterUri = current.posterUri, historyTitle = current.historyTitle, genres = current.genres, languageText = current.languageText,
        )
    }

    private fun allEpisodes(show: VodItem, season: VodItem): List<EpisodeRef>? {
        val out = mutableListOf<VodItem>()
        for (page in 1..MAX_PAGES) {
            val r = catalog.episodes(show, season, page) as? CatalogResult.Ok ?: return if (out.isEmpty()) null else out.map(::EpisodeRef)
            val known = out.mapTo(HashSet()) { it.id }
            out += r.value.items.filter { it.id !in known }
            if (!r.value.hasMore(out.size)) break
        }
        return out.map(::EpisodeRef)
    }

    private companion object { const val MAX_PAGES = 10 }
}

private object PortalTextLite { fun clean(s: String) = io.github.ravindren_sm.openstream.provider.PortalText.decodeEntities(s).trim() }
