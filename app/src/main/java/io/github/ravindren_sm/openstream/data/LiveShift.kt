package io.github.ravindren_sm.openstream.data

/** Wording for how far behind the live edge a live stream is playing (after pause or rewind). Plain Kotlin, tested without Android. */
object LiveShift {
    /** Within this of the edge counts as live. */
    const val LIVE_THRESHOLD_MS = 4_000L

    /** "Live", or "-12:05 behind live". Null offset (not a live stream, or not known) gives "". */
    fun label(offsetMillis: Long?): String = when {
        offsetMillis == null || offsetMillis < 0 -> ""
        offsetMillis < LIVE_THRESHOLD_MS -> "Live"
        else -> "-" + TimeText.format(offsetMillis) + " behind live"
    }

    /** Whether a forward skip of [deltaMillis] from [offsetMillis] behind live would pass the live edge (so it should rejoin live). */
    fun reachesLive(offsetMillis: Long?, deltaMillis: Long): Boolean = offsetMillis != null && offsetMillis >= 0 && offsetMillis - deltaMillis <= LIVE_THRESHOLD_MS / 2
}
