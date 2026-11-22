package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.profiles.db.CatalogIndexDao
import io.github.ravindren_sm.openstream.profiles.db.CatalogTitleEntity
import io.github.ravindren_sm.openstream.provider.VodItem
import io.github.ravindren_sm.openstream.provider.VodPage

/** A lean catalog row as a list entry: enough for a poster tile. Opening one fetches the full entry first (see [VodHydrator]). */
fun CatalogTitleEntity.toVodItem() = VodItem(
    id = id, name = name, isSeries = kind == "SHOW", isSeason = false, isEpisode = false, cmd = cmd, episodeNumbers = emptyList(),
    year = year, imdbRating = imdbRating, kinopoiskRating = kinopoiskRating, age = age, posterUri = posterUri,
)

/** Pages of one indexed category in a chosen order and rating floor, read from the device. Blocking: call off the main thread. */
class IndexedTitles(private val dao: CatalogIndexDao, private val pageSize: Int = 28) {
    fun page(categoryId: String, sort: BrowseSort, policy: ContentPolicy, page: Int, genres: List<String> = emptyList()): CatalogResult<VodPage> = try {
        val keep = if (policy.hideUnrated) 0 else 1
        val g1 = genres.getOrNull(0); val g2 = genres.getOrNull(1); val g3 = genres.getOrNull(2)    // up to three genres, any of them
        val (rows, total) = kotlinx.coroutines.runBlocking {
            dao.browse(categoryId, policy.minRating, keep, g1, g2, g3, sort.code, pageSize, (page - 1) * pageSize) to dao.browseCount(categoryId, policy.minRating, keep, g1, g2, g3)
        }
        CatalogResult.Ok(VodPage(page, rows.map { it.toVodItem() }, total, pageSize))
    } catch (e: Exception) {
        CatalogResult.Failed("The saved title list could not be read.")
    }
}
