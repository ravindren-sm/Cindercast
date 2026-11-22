package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.profiles.db.TitleTagDao
import io.github.ravindren_sm.openstream.provider.VodItem
import kotlinx.coroutines.runBlocking

/**
 * Adds what the optional online tag service found (score, genres, age label) to list entries, filling only gaps. A page of titles is
 * looked up in one query by id; the database holds nothing until a key was saved, so without tags this is one cheap query that finds
 * nothing. Blocking: call off the main thread.
 */
class TagEnricher(private val dao: TitleTagDao) {
    fun enrich(items: List<VodItem>): List<VodItem> {
        if (items.isEmpty()) return items
        val ids = items.filter { !it.isSeason && !it.isEpisode && !it.isFile }.map { it.id }.distinct()
        if (ids.isEmpty()) return items
        val tags = try { ids.chunked(500).flatMap { chunk -> runBlocking { dao.byIds(chunk) } }.associateBy { it.id } } catch (_: Exception) { return items }
        if (tags.isEmpty()) return items
        return items.map { item -> tags[item.id]?.let { t -> item.withTags(t.rating, t.genres, t.certLabel, t.imdbScore, t.rtScore) } ?: item }
    }
}
