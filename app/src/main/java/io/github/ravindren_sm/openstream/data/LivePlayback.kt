package io.github.ravindren_sm.openstream.data

import androidx.media3.common.PlaybackException
import io.github.ravindren_sm.openstream.provider.LiveChannel

/** A playable stream address, or fixed user-facing failure text (no addresses, MACs or tokens). */
sealed interface StreamLink {
    /** [url] lives in memory only: it is never logged and never part of any message. */
    class Ok(val url: String) : StreamLink {
        override fun toString() = "StreamLink.Ok"
    }
    class Failed(val message: String) : StreamLink
}

/** Turns a channel into a stream address with the portal's create_link, on the session thread. */
class LivePlayback(private val session: PortalSession) {

    /** Blocking: call off the main thread. */
    fun resolve(channel: LiveChannel): StreamLink {
        val cmd = channel.cmd ?: return StreamLink.Failed(NO_ADDRESS)
        var url: String? = null
        val result = session.withSession { m ->
            m.call { c -> val (r, u) = c.createLink(cmd); url = u; r }
        } ?: return StreamLink.Failed(LiveCatalog.NOT_CONNECTED)
        val link = url
        return if (result.ok && link != null) StreamLink.Ok(link) else StreamLink.Failed(NO_LINK)
    }

    companion object {
        const val NO_ADDRESS = "This channel has no stream address."
        const val NO_LINK = "The portal did not give a stream for this channel."
    }
}

/**
 * Wording for playback errors. Fixed text only: the player's own messages can hold stream URLs, so
 * they are never shown. Takes the error code and, when the error was an HTTP refusal, its status.
 */
object PlaybackMessages {
    fun forError(errorCode: Int, httpStatus: Int? = null): String = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Could not reach the stream. Check the connection and try again."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> when (httpStatus) {
            401, 403 -> "The stream server refused the request (HTTP $httpStatus)."
            404, 410 -> "This channel is not available right now."
            null -> "The stream server returned an error."
            else -> "The stream server returned an error (HTTP $httpStatus)."
        }
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "This channel is not available right now."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
            "The stream could not be read (unsupported or damaged data)."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "This device cannot play the stream's video or audio format."
        else -> "Playback failed (code $errorCode)."
    }
}
