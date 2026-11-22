package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.profiles.FavoritesState
import io.github.ravindren_sm.openstream.profiles.ProfileRepository
import io.github.ravindren_sm.openstream.profiles.db.CatalogIndexDao
import io.github.ravindren_sm.openstream.profiles.db.CatalogTitleEntity

/**
 * Gathers the text the device already has for [SuggestionEngine]: the profile's recent searches, favorites and watch
 * history, the channel names (once the channel list has been read), and the local title index. No portal request.
 */
class SearchSources(
    private val repo: ProfileRepository,
    private val favorites: FavoritesState,
    private val index: CatalogIndexDao,
    private val channelNames: () -> List<String>,
) {
    suspend fun suggestions(profileId: Long, query: String, kids: KidsRules? = null): List<Suggestion> {
        val q = SearchRanking.normalize(query)
        val sources = mutableMapOf<SuggestionSource, List<String>>()
        sources[SuggestionSource.RECENT] = if (profileId > 0) repo.recentSearches(profileId, 8) else emptyList()
        if (q.isNotEmpty()) {
            sources[SuggestionSource.FAVORITE] = favorites.all.map { it.name }
            sources[SuggestionSource.WATCHED] = if (profileId > 0) repo.recentlyWatched(profileId, 40).map { it.title } else emptyList()
            sources[SuggestionSource.CHANNEL] = if (kids == null) channelNames() else emptyList()      // a Kids profile has no Live TV
            if (q.length >= 2) sources[SuggestionSource.TITLE] = titlesFor(q, kids)
        }
        return SuggestionEngine.suggest(query, sources)
    }

    private suspend fun titlesFor(q: String, kids: KidsRules?): List<String> {
        val esc = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val rows: List<CatalogTitleEntity> = index.match("$esc%", "% $esc%", 60)
        // Whole-name starts first, so the 60-row cut does not hide them behind word matches.
        return rows.filter { kids == null || kids.allows(it.name, it.age, it.genres) }.sortedBy { if (it.nameKey.startsWith(q)) 0 else 1 }.map { it.name }
    }
}
