package io.github.ravindren_sm.openstream.data

import io.github.ravindren_sm.openstream.provider.VodItem
import io.github.ravindren_sm.openstream.provider.VodPage

/**
 * Turns a lean list entry (from the device copy of the catalog: no description, cast or genres) into the portal's full entry before
 * its page opens, by searching the portal for its name inside its category and picking the entry with the same id. Falls back to the
 * lean entry if the portal cannot be asked. Blocking: call off the main thread.
 */
class VodHydrator(private val session: PortalSession) {
    fun hydrate(item: VodItem, categoryId: String): VodItem {
        var parsed: VodPage? = null
        session.withSession(8_000) { m -> m.call { c -> val (r, p) = c.vodSearch(item.name, 1, categoryId); parsed = p; r } }
        return parsed?.items?.firstOrNull { it.id == item.id } ?: item
    }
}
