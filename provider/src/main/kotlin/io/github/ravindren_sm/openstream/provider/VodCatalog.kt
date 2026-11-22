package io.github.ravindren_sm.openstream.provider

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * One server-side page of VOD titles. [totalItems] is the portal's count for the category;
 * [pageSize] is the portal's page length (14 on this portal), falling back to the items on the page.
 */
class VodPage(val page: Int, val items: List<VodItem>, val totalItems: Int, val pageSize: Int) {
    /** Whether a following page should exist, judged from the total (the portal gives no "next" flag). */
    fun hasMore(loadedSoFar: Int): Boolean = items.isNotEmpty() && loadedSoFar < totalItems
}

object VodCatalogParser {
    /** A page from the "js" object of vod get_ordered_list; null if the payload is not an object. */
    fun page(js: JsonElement?, page: Int): VodPage? {
        val o = js as? JsonObject ?: return null
        val items = VodItem.listFrom(js)
        val total = (o["total_items"] as? JsonPrimitive)?.intOrNull ?: items.size
        val size = (o["max_page_items"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 } ?: items.size
        return VodPage(page, items, total, size)
    }
}
