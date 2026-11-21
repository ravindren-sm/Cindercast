package io.github.ravindren_sm.openstream.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/*
 * Live TV catalog models for the app (M3). Parsed from the portal's get_genres and
 * itv get_ordered_list payloads. Titles and names are catalog labels, not identifying data;
 * a channel's cmd is needed for create_link but is never part of toString.
 */

data class LiveGenre(val id: String, val title: String)

class LiveChannel(
    val id: String,
    /** The channel number as the portal sends it (text, may be empty). */
    val number: String,
    val name: String,
    val cmd: String?,
) {
    override fun toString() = "LiveChannel(id=$id, number=$number, name=$name)"
}

/**
 * One server-side page of channels. [totalItems] is the portal's count for the whole genre;
 * [pageSize] is the portal's page length (14 on this portal), falling back to the items on the page.
 */
class LiveChannelPage(
    val page: Int,
    val items: List<LiveChannel>,
    val totalItems: Int,
    val pageSize: Int,
) {
    /** Whether a following page should exist, judged from the total (the portal gives no "next" flag). */
    fun hasMore(loadedSoFar: Int): Boolean = items.isNotEmpty() && loadedSoFar < totalItems
}

object LiveCatalogParser {
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    /** Genres from the "js" array of get_genres. Entries without an id are dropped. */
    fun genres(js: JsonElement?): List<LiveGenre> =
        (js as? JsonArray)?.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = o.str("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            LiveGenre(id, o.str("title")?.trim()?.takeIf { it.isNotEmpty() } ?: "?")
        }.orEmpty()

    /** A channel page from the "js" object of itv get_ordered_list; null if the payload is not an object. */
    fun channelPage(js: JsonElement?, page: Int): LiveChannelPage? {
        val o = js as? JsonObject ?: return null
        val items = (o["data"] as? JsonArray)?.mapNotNull { e ->
            val c = e as? JsonObject ?: return@mapNotNull null
            val id = c.str("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            LiveChannel(
                id = id,
                number = c.str("number")?.trim().orEmpty(),
                name = c.str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: "?",
                cmd = c.str("cmd")?.takeIf { it.isNotBlank() },
            )
        }.orEmpty()
        val total = o.int("total_items") ?: items.size
        val size = o.int("max_page_items")?.takeIf { it > 0 } ?: items.size
        return LiveChannelPage(page, items, total, size)
    }
}
