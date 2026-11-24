package io.github.ravindren_sm.openstream.profiles

import io.github.ravindren_sm.openstream.profiles.db.HistoryEntity

/** One tile of the Continue watching row. */
data class ContinueTile(
    val resumeKey: String,
    val kind: String,
    /** The movie's id, or the show's id for an episode. */
    val titleId: String,
    val title: String,
    val subtitle: String?,
    val posterUri: String?,
    val playCmd: String,
    /** 0.0 to 1.0, or 0 when the length is unknown. */
    val progress: Float,
    /** "12 min left", or null when the length is unknown. */
    val timeLeft: String?,
    val positionMs: Long,
    val completed: Boolean = false,
) {
    /** The title this tile stands for (a show's episodes share one). */
    val titleKey: String get() = ContinueItems.titleKey(kind, titleId)
}

/** Turns history rows (newest first) into the tiles Home shows. Plain Kotlin so it is tested without Android. */
object ContinueItems {
    const val MAX_TILES = 14

    /**
     * Keeps the newest row of each show (a viewer mid-way through three episodes sees one tile: the latest) and drops
     * rows that cannot be played again (no stored command). Input order is preserved.
     */
    fun from(rows: List<HistoryEntity>, max: Int = MAX_TILES, exclude: Set<String> = emptySet()): List<ContinueTile> {
        val seen = HashSet<String>(exclude)
        val out = ArrayList<ContinueTile>()
        for (r in rows) {
            val cmd = r.playCmd ?: continue
            if (!seen.add(titleKey(r.kind, r.titleId))) continue
            out += ContinueTile(
                r.resumeKey, r.kind, r.titleId, r.title, r.subtitle, r.posterUri, cmd,
                if (r.completed) 1f else progress(r.positionMs, r.durationMs),
                if (r.completed) "Watched" else timeLeft(r.positionMs, r.durationMs), r.positionMs, r.completed,
            )
            if (out.size >= max) break
        }
        return out
    }

    /** What makes two rows the same title for Home: a movie, or a show (all its episodes). */
    fun titleKey(kind: String, titleId: String) = "$kind/$titleId"

    fun progress(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    /** Time remaining rounded up to whole minutes: "45 min left", "1 h 5 min left"; null when the length is unknown. */
    fun timeLeft(positionMs: Long, durationMs: Long): String? {
        if (durationMs <= 0) return null
        val minutes = ((durationMs - positionMs).coerceAtLeast(0) + 59_999) / 60_000
        return when {
            minutes <= 0 -> "Almost done"
            minutes < 60 -> "$minutes min left"
            minutes % 60 == 0L -> "${minutes / 60} h left"
            else -> "${minutes / 60} h ${minutes % 60} min left"
        }
    }
}
