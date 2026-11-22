package io.github.ravindren_sm.openstream

import androidx.media3.common.PlaybackException
import io.github.ravindren_sm.openstream.data.PlaybackRecovery
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a playback error is retried automatically, and when it is finally shown. */
class PlaybackRecoveryTest {
    private var t = 0L
    private fun recovery(max: Int = 5, window: Long = 120_000) = PlaybackRecovery(max, window) { t }

    @Test fun `a decoder or connection failure is retried`() {
        listOf(
            PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        ).forEach { assertTrue("code $it", recovery().shouldRecover(it)) }
    }

    @Test fun `a refused request, unreadable data or an unsupported format is never retried`() {
        listOf(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW, 9999,
        ).forEach { assertFalse("code $it", recovery().shouldRecover(it)) }
    }

    @Test fun `five attempts inside the window, then the error is shown`() {
        val r = recovery()
        repeat(5) { t += 10_000; assertTrue(r.shouldRecover(PlaybackException.ERROR_CODE_DECODING_FAILED)) }
        t += 10_000
        assertFalse(r.shouldRecover(PlaybackException.ERROR_CODE_DECODING_FAILED))
    }

    @Test fun `attempts older than the window no longer count`() {
        val r = recovery()
        repeat(5) { assertTrue(r.shouldRecover(PlaybackException.ERROR_CODE_DECODING_FAILED)) }
        assertFalse(r.shouldRecover(PlaybackException.ERROR_CODE_DECODING_FAILED))
        t += 121_000                                   // the glitches were long ago: a fresh one is retried again
        assertTrue(r.shouldRecover(PlaybackException.ERROR_CODE_DECODING_FAILED))
    }

    @Test fun `a refused request does not use up attempts, and reset clears them`() {
        val r = recovery(max = 1)
        assertFalse(r.shouldRecover(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertTrue(r.shouldRecover(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertFalse(r.shouldRecover(PlaybackException.ERROR_CODE_DECODING_FAILED))
        r.reset()
        assertTrue(r.shouldRecover(PlaybackException.ERROR_CODE_DECODING_FAILED))
    }
}
