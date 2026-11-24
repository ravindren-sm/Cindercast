package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.profiles.db.PinnedCategoryEntity
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.VodItem

/** The kinds a pin can have: a movies category, a shows category, or the one live group. */
object PinKinds {
    const val MOVIES = "MOVIES"
    const val SHOWS = "SHOWS"
    const val LIVE = "LIVE"
}

/** A row on Home that comes from something pinned. [items] / [channels] are null until loaded. */
class PinnedRow(val pin: PinnedCategoryEntity, val titles: List<VodItem>?, val channels: List<LiveChannel>?, val failed: Boolean)

/** Which pins become Home rows, and how many. Plain Kotlin, tested without Android. */
object HomeRowPlan {
    /** At most this many "New in ..." rows, so Home never asks the portal for more than a few lists. */
    const val MAX_TITLE_ROWS = 3

    /** Movie and show pins in their saved order, newest pin last, cut to [MAX_TITLE_ROWS]. */
    fun titlePins(pins: List<PinnedCategoryEntity>): List<PinnedCategoryEntity> =
        pins.filter { it.kind == PinKinds.MOVIES || it.kind == PinKinds.SHOWS }.sortedBy { it.position }.take(MAX_TITLE_ROWS)

    fun livePin(pins: List<PinnedCategoryEntity>): PinnedCategoryEntity? = pins.firstOrNull { it.kind == PinKinds.LIVE }

    /** The position for a new pin: after the existing ones. */
    fun nextPosition(pins: List<PinnedCategoryEntity>): Int = (pins.maxOfOrNull { it.position } ?: -1) + 1
}

/**
 * Loads the lists behind pinned Home rows (one portal request each, newest titles first as the portal lists them, or the first
 * page of a live group) and keeps them ten minutes. Blocking: call off the main thread.
 */
class HomeFeed(private val vod: VodCatalog, private val live: LiveCatalog, private val ttlMillis: Long = 10 * 60_000, private val nowMillis: () -> Long = System::currentTimeMillis) {
    private class Entry<T>(val value: T, val at: Long)
    private val titles = java.util.concurrent.ConcurrentHashMap<String, Entry<List<VodItem>>>()
    private val channels = java.util.concurrent.ConcurrentHashMap<String, Entry<List<LiveChannel>>>()

    /** The first [limit] titles of the category's newest page that [keep] allows (the profile's rating floor). The page is cached whole. */
    fun titlesOf(categoryId: String, limit: Int = 12, keep: (VodItem) -> Boolean = { true }): List<VodItem>? {
        titles[categoryId]?.let { if (nowMillis() - it.at < ttlMillis) return it.value.filter(keep).take(limit) }
        val r = vod.titles(categoryId, 1) as? CatalogResult.Ok ?: return null
        titles[categoryId] = Entry(r.value.items, nowMillis())
        return r.value.items.filter(keep).take(limit)
    }

    fun channelsOf(genreId: String, limit: Int = 12): List<LiveChannel>? {
        channels[genreId]?.let { if (nowMillis() - it.at < ttlMillis) return it.value }
        val r = live.channels(genreId, 1) as? CatalogResult.Ok ?: return null
        return r.value.items.take(limit).also { channels[genreId] = Entry(it, nowMillis()) }
    }

    fun forget() { titles.clear(); channels.clear() }
}
