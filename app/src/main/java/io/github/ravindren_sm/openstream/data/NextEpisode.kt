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
    fun next(seasons: List<SeasonRef>, seasonId: String, episodeId: String, episodesOf: (SeasonRef) -> List<EpisodeRef>?): Next? {
        val si = seasons.indexOfFirst { it.item.id == seasonId }.takeIf { it >= 0 } ?: return null
        val here = episodesOf(seasons[si]) ?: return null
        val ei = here.indexOfFirst { it.item.id == episodeId }.takeIf { it >= 0 } ?: return null
        if (ei + 1 < here.size) return Next(seasons[si], here[ei + 1])
        for (k in si + 1 until seasons.size) {
            val first = episodesOf(seasons[k])?.firstOrNull() ?: continue
            return Next(seasons[k], first)
        }
        return null
    }

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
            posterUri = current.posterUri, historyTitle = current.historyTitle,
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
