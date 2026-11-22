package io.github.ravindren_sm.openstream.data

/**
 * How far one Left or Right key event skips. A single press moves 10 s; holding the key makes the step grow, so a long film can
 * be crossed in a few seconds and the viewer lets go when the time looks right. [repeatCount] is the number of repeat events the
 * system has sent for the held key (0 for the first press).
 */
object SeekRamp {
    fun stepMillis(repeatCount: Int): Long = when {
        repeatCount < 3 -> 10_000
        repeatCount < 8 -> 30_000
        repeatCount < 16 -> 60_000
        repeatCount < 30 -> 180_000
        else -> 300_000
    }
}
