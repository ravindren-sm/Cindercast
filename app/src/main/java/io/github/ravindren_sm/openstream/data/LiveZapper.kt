package io.github.ravindren_sm.openstream.data

/** Where a zap lands: a position in the loaded channel list, or why it could not move. */
sealed interface ZapResult {
    class To(val index: Int) : ZapResult
    /** Nothing further in that direction (first or last channel of the genre). */
    data object AtEnd : ZapResult
    /** No channel with that number (or the portal stopped answering while looking). */
    data object NotFound : ZapResult
}

/**
 * Channel up/down and number entry for the live player, over the channels of one genre as loaded by
 * [PagedChannels]. The list is the portal's own order, which is by channel number, so a typed number
 * is looked up in what is loaded and, if it lies beyond it, further pages are fetched until the
 * numbers pass it. At most [maxPages] extra pages are fetched for one lookup, so a far-off number
 * cannot flood the portal. All calls block (they may fetch a page): call off the main thread.
 */
class LiveZapper(private val paged: PagedChannels, private val maxPages: Int = 40) {

    /** The channel after [current], fetching the next page if [current] is the last one loaded. */
    fun next(current: Int): ZapResult {
        if (current + 1 < paged.items.size) return ZapResult.To(current + 1)
        if (paged.exhausted) return ZapResult.AtEnd
        paged.loadNext()
        return if (current + 1 < paged.items.size) ZapResult.To(current + 1) else ZapResult.AtEnd
    }

    /** The channel before [current]. Earlier pages are always loaded, so no fetch is needed. */
    fun previous(current: Int): ZapResult = if (current > 0) ZapResult.To(current - 1) else ZapResult.AtEnd

    /** The channel whose number is [typed] (digits only; leading zeros ignored). */
    fun byNumber(typed: String): ZapResult {
        val target = typed.trim().toIntOrNull() ?: return ZapResult.NotFound
        var fetched = 0
        while (true) {
            find(target)?.let { return ZapResult.To(it) }
            val last = paged.items.lastOrNull()?.number?.toIntOrNull()
            // Sorted by number: once the loaded numbers have passed the target it is not in this genre.
            if (paged.exhausted || (last != null && last > target) || fetched >= maxPages) return ZapResult.NotFound
            val before = paged.items.size
            paged.loadNext()
            fetched++
            if (paged.items.size == before) return ZapResult.NotFound   // a failed or empty page
        }
    }

    private fun find(target: Int): Int? =
        paged.items.indexOfFirst { it.number.toIntOrNull() == target }.takeIf { it >= 0 }
}
