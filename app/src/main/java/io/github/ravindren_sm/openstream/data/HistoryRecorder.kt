package io.github.ravindren_sm.openstream.data

/**
 * Receives where the viewer is in a title while it plays, so Home can offer "Continue watching". The player calls it
 * every few seconds and when it stops; an implementation decides what is worth keeping (see ProfileRepository.recordProgress).
 */
interface HistoryRecorder {
    fun record(target: PlayTarget, positionMs: Long, durationMs: Long)
}

/** For callers with no profile (tests, previews): remembers nothing. */
object NoHistory : HistoryRecorder {
    override fun record(target: PlayTarget, positionMs: Long, durationMs: Long) = Unit
}
