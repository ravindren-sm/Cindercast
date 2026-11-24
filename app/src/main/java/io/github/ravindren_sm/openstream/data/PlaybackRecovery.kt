package io.github.ravindren_sm.openstream.data

import androidx.media3.common.PlaybackException

/**
 * Decides whether a playback error deserves another automatic attempt, so a one-off glitch does not stop the
 * picture for a viewer. Seen on the Fire TV: a video decoder error about two minutes into an episode, which
 * played on normally after a manual retry.
 *
 * Only failures that are plausibly momentary qualify: a decoder or audio output that failed part-way, and
 * dropped or timed-out connections. A refused request (HTTP 403, 404), unreadable data or an unsupported format
 * never retries. At most [maxAttempts] automatic attempts are made within [windowMillis]; after that the error
 * is shown with the retry button, so a stream that is really broken does not loop. The players wait
 * [RETRY_DELAY_MILLIS] before each attempt, so five attempts cover a network outage of about half a minute
 * without hammering a connection that is down.
 */
class PlaybackRecovery(
    private val maxAttempts: Int = 5,
    private val windowMillis: Long = 120_000,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val attempts = ArrayDeque<Long>()

    /** True if this error should be retried now; records the attempt. */
    fun shouldRecover(errorCode: Int): Boolean {
        if (errorCode !in RECOVERABLE) return false
        val now = nowMillis()
        while (attempts.isNotEmpty() && now - attempts.first() > windowMillis) attempts.removeFirst()
        if (attempts.size >= maxAttempts) return false
        attempts.addLast(now)
        return true
    }

    /** Forget earlier attempts: a new title or channel starts with a clean slate. */
    fun reset() = attempts.clear()

    companion object {
        const val RETRY_DELAY_MILLIS = 1_500L

        val RECOVERABLE: Set<Int> = setOf(
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        )
    }
}
