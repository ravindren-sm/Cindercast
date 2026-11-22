package io.github.ravindren_sm.openstream

import androidx.media3.common.PlaybackException
import io.github.ravindren_sm.openstream.data.LiveCatalog
import io.github.ravindren_sm.openstream.data.LivePlayback
import io.github.ravindren_sm.openstream.data.PlaybackMessages
import io.github.ravindren_sm.openstream.data.PortalSession
import io.github.ravindren_sm.openstream.data.SessionStatus
import io.github.ravindren_sm.openstream.data.StreamLink
import io.github.ravindren_sm.openstream.provider.HttpProbe
import io.github.ravindren_sm.openstream.provider.LiveChannel
import io.github.ravindren_sm.openstream.provider.PortalSettings
import io.github.ravindren_sm.openstream.provider.RequestPacer
import io.github.ravindren_sm.openstream.provider.SessionManager
import io.github.ravindren_sm.openstream.provider.StalkerClient
import io.github.ravindren_sm.openstream.provider.StalkerIdentity
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** create_link resolution against a local fake portal, and the fixed wording of playback errors. */
class LivePlaybackTest {
    private lateinit var server: MockWebServer
    private lateinit var session: PortalSession
    private lateinit var playback: LivePlayback
    private val linkRequests = CopyOnWriteArrayList<String>()
    @Volatile private var linkBody = """{"js":{"cmd":"ffmpeg http://stream.invalid/live/7?token=abc","error":""}}"""

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    @Before fun setUp() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val q = request.path.orEmpty()
                    return when {
                        "handshake" in q -> ok("""{"js":{"token":"t0k3n"}}""")
                        "get_profile" in q -> ok("""{"js":{"status":0}}""")
                        "create_link" in q -> { linkRequests += q; ok(linkBody) }
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start()
        }
        session = PortalSession(newManager = { s ->
            SessionManager(StalkerClient(s.portalUrl, s.mac, HttpProbe(RequestPacer(0)), StalkerIdentity.HONEST))
        }, intervalMillis = 60_000)
        playback = LivePlayback(session)
    }

    @After fun tearDown() { session.shutdown(); server.shutdown() }

    private fun connect() {
        val s = (PortalSettings.validate(server.url("/stalker_portal/c/").toString(), "00:1A:79:00:00:01") as PortalSettings.Result.Valid).settings
        session.configure(s); session.onForeground()
        val end = System.nanoTime() + 5_000_000_000L
        while (session.status !is SessionStatus.Ready) { check(System.nanoTime() < end) { "timed out connecting" }; Thread.sleep(10) }
    }

    private fun channel(cmd: String?) = LiveChannel("7", "7", "Seven", cmd)

    @Test fun `a channel resolves to the stream address from create_link, with the prefix word removed`() {
        connect()
        val link = playback.resolve(channel("ffmpeg http://localhost/ch/7"))
        assertEquals("http://stream.invalid/live/7?token=abc", (link as StreamLink.Ok).url)
        assertTrue(linkRequests.single().let { "type=itv" in it && "action=create_link" in it })
        assertFalse("the address must not appear in toString", link.toString().contains("stream.invalid"))
    }

    @Test fun `a channel without a cmd fails without asking the portal`() {
        connect()
        assertEquals(LivePlayback.NO_ADDRESS, (playback.resolve(channel(null)) as StreamLink.Failed).message)
        assertTrue(linkRequests.isEmpty())
    }

    @Test fun `a portal answer without a stream fails with fixed text`() {
        connect()
        linkBody = """{"js":{"cmd":"","error":"nothing_to_play"}}"""
        val failed = playback.resolve(channel("ffmpeg http://localhost/ch/7")) as StreamLink.Failed
        assertEquals(LivePlayback.NO_LINK, failed.message)
        assertFalse(failed.message.contains("nothing_to_play"))
    }

    @Test fun `without a connected session resolving fails with fixed text`() {
        assertEquals(LiveCatalog.NOT_CONNECTED, (playback.resolve(channel("ffmpeg http://localhost/ch/7")) as StreamLink.Failed).message)
    }

    // ---- error wording ----

    @Test fun `network errors say the stream could not be reached`() {
        listOf(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
            .forEach { assertTrue(PlaybackMessages.forError(it).startsWith("Could not reach the stream")) }
    }

    @Test fun `http refusals are named, missing channels say not available`() {
        assertTrue("403" in PlaybackMessages.forError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 403))
        assertTrue("401" in PlaybackMessages.forError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 401))
        assertEquals("This channel is not available right now.", PlaybackMessages.forError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 404))
        assertTrue("503" in PlaybackMessages.forError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 503))
        assertEquals("The stream server returned an error.", PlaybackMessages.forError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, null))
    }

    @Test fun `format and decoder errors say the device cannot play it, parse errors say unreadable`() {
        assertTrue("cannot play" in PlaybackMessages.forError(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertTrue("cannot play" in PlaybackMessages.forError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertTrue("could not be read" in PlaybackMessages.forError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
    }

    @Test fun `a decoder that failed mid-stream is not called a format problem`() {
        assertEquals("The video decoder stopped unexpectedly.", PlaybackMessages.forError(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertTrue("cannot play" in PlaybackMessages.forError(PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES))
    }

    @Test fun `unknown codes give a generic message with the number only`() {
        assertEquals("Playback failed (code 9999).", PlaybackMessages.forError(9999))
    }
}
